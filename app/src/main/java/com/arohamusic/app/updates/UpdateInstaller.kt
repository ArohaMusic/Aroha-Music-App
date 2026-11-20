package com.arohamusic.app.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.arohamusic.app.analytics.HealthEvents
import com.arohamusic.app.analytics.HealthOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class InstallState(val downloading: Boolean = false, val progress: Float? = null,
    val readyVersion: String? = null, val message: String? = null)

internal class UpdateInstaller(private val context: Context, private val scope: CoroutineScope) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()
    private val directory get() = File(context.cacheDir, "app-updates")
    private val mutableState = MutableStateFlow(InstallState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    @Volatile private var call: Call? = null
    private var prepared: Pair<File, GitHubRelease>? = null
    private val preferences = context.getSharedPreferences("update_install", Context.MODE_PRIVATE)
    private val initialized = scope.async { cleanupAfterRestart() }

    suspend fun cleanupAfterRestart() = withContext(Dispatchers.IO) {
        directory.deleteRecursively() // Interrupted downloads are retried explicitly, never auto-installed.
        val expected = preferences.getLong("pending_code", 0)
        if (expected > 0) {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = versionCode(installed)
            if (code >= expected) mutableState.value = InstallState(message = "Your update was installed successfully.")
            preferences.edit().remove("pending_code").apply()
        }
    }

    fun download(release: GitHubRelease) {
        if (work?.isActive == true) return
        val url = release.apkUrl ?: return
        if (!trustedReleaseUrl(url, true)) return
        if (release.apkSha256 == null) {
            mutableState.value = InstallState(message = "This release needs a browser download. Use Download in browser below.")
            return
        }
        prepared = null
        mutableState.value = InstallState(downloading = true)
        work = scope.launch {
            var file: File? = null
            try {
                initialized.await()
                mutableState.value = InstallState(downloading = true)
                file = withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    directory.listFiles()?.forEach { it.delete() }
                    val destination = File(directory, "update.apk")
                    try {
                        UpdateDownload(client).fetch(release, destination, onCall = { call = it }) { progress ->
                            mutableState.value = InstallState(downloading = true, progress = progress)
                        }
                        verify(destination, release)
                        destination
                    } catch (error: Throwable) { destination.delete(); throw error }
                }
                prepared = file to release
                mutableState.value = InstallState(readyVersion = release.version, message = "Download verified. Tap Install update to continue.")
                HealthEvents.record(HealthOperation.UPDATE_DOWNLOAD, true)
            } catch (error: CancellationException) {
                file?.delete(); mutableState.value = InstallState(message = "Download canceled."); throw error
            } catch (_: Exception) {
                file?.delete()
                mutableState.value = InstallState(message = "Couldn't download or verify this update. Check your connection and free space, then retry or use your browser.")
                HealthEvents.record(HealthOperation.UPDATE_DOWNLOAD, false)
            } finally { call = null }
        }
    }

    fun cancel() { call?.cancel(); work?.cancel(); prepared = null }

    fun install(activityContext: Context) {
        val (file, release) = prepared ?: return
        if (work?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching { activityContext.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                .onSuccess { mutableState.value = state.value.copy(message = "Allow updates from Aroha Music, then return and tap Install update.") }
                .onFailure { mutableState.value = state.value.copy(message = "Open Android settings to allow installation, or download with your browser.") }
            return
        }
        work = scope.launch {
            try {
                withContext(Dispatchers.IO) { verify(file, release) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                activityContext.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                val info = archive(file)
                preferences.edit().putLong("pending_code", versionCode(info)).apply()
                mutableState.value = state.value.copy(message = "Confirm installation in Android. You can retry here if installation is canceled.")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { prepared = null; file.delete(); mutableState.value = InstallState(message = "Couldn't open this verified update. Download it again or use your browser.") }
        }
    }

    @Suppress("DEPRECATION")
    private fun archive(file: File): PackageInfo = checkNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES))
    @Suppress("DEPRECATION")
    private fun verify(file: File, release: GitHubRelease) {
        val expected = requireNotNull(release.apkSha256)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        require(hex(digest.digest()) == expected)
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = archive(file)
        require(UpdatePackagePolicy.accepts(installed.packageName, versionCode(installed), signers(installed), candidate.packageName,
            versionCode(candidate), candidate.versionName, signers(candidate), release.version))
    }
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
