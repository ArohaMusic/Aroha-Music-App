package com.arohamusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arohamusic.app.ui.*

@UnstableApi
class MainActivity : ComponentActivity() {
    private var musicModel: MusicViewModel? = null
    private val launchRequests = kotlinx.coroutines.flow.MutableStateFlow<android.content.Intent?>(null)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent); launchRequests.value = intent
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchRequests.value = intent
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            val model: MusicViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MusicViewModel(application as ArohaApplication) as T
                }
            })
            val appearance = model.theme.collectAsStateWithLifecycle().value
            androidx.compose.runtime.SideEffect {
                musicModel = model
                model.setAppVisible(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
            }
            if (appearance != null) ArohaTheme(appearance) {
                androidx.compose.runtime.LaunchedEffect(model) {
                    launchRequests.collect { request ->
                        if (request?.getBooleanExtra(com.arohamusic.app.updates.UpdateNotifications.OPEN_UPDATES, false) == true) {
                            model.showAvailableUpdate()
                        } else if (request?.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                            model.playFromSearch(request.getStringExtra(android.app.SearchManager.QUERY).orEmpty())
                        } else if (request?.action == android.content.Intent.ACTION_SEND && request.type == "text/plain") {
                            model.openSharedLink(request.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty())
                        } else if (request?.action == android.content.Intent.ACTION_VIEW) {
                            model.openSharedLink(request.dataString.orEmpty())
                        }
                        launchRequests.compareAndSet(request, null)
                    }
                }
                Surface(color = Ink) { ArohaApp(model) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        musicModel?.setAppVisible(true)
        (application as ArohaApplication).checkUpdatesIfDue()
    }
    override fun onStop() { musicModel?.setAppVisible(false); super.onStop() }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && musicModel?.preferences?.value?.stopOnExit == true) {
            (application as ArohaApplication).stopPlaybackOnExit()
        }
        super.onDestroy()
    }
}
