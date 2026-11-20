package com.arohamusic.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.arohamusic.app.R

/** Bundled fonts work offline; Android supplies fallback glyphs for other scripts. */
internal val ArohaFont = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun textStyle(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = ArohaFont, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = tracking.sp,
)

/** Complete Material scale: every control inherits the same family, rhythm and weights. */
internal val ArohaTypography = Typography(
    displayLarge = textStyle(36, 44, FontWeight.Bold, -.7f),
    displayMedium = textStyle(32, 40, FontWeight.Bold, -.5f),
    displaySmall = textStyle(28, 36, FontWeight.Bold, -.4f),
    headlineLarge = textStyle(30, 38, FontWeight.Bold, -.5f),
    headlineMedium = textStyle(26, 34, FontWeight.Bold, -.3f),
    headlineSmall = textStyle(24, 32, FontWeight.SemiBold, -.25f),
    titleLarge = textStyle(20, 28, FontWeight.SemiBold, -.15f),
    titleMedium = textStyle(16, 24, FontWeight.SemiBold, -.1f),
    titleSmall = textStyle(14, 20, FontWeight.SemiBold),
    bodyLarge = textStyle(16, 24),
    bodyMedium = textStyle(14, 21),
    bodySmall = textStyle(12, 18),
    labelLarge = textStyle(14, 20, FontWeight.SemiBold, .05f),
    labelMedium = textStyle(12, 16, FontWeight.SemiBold, .1f),
    labelSmall = textStyle(11, 16, FontWeight.Medium, .25f),
)

/** Semantic roles shared across lists, cards, menus and player variants. All sizes use sp. */
internal object ArohaType {
    val Brand = textStyle(22, 28, FontWeight.Bold, -.4f)
    val PageTitle = ArohaTypography.headlineMedium
    val HeroTitle = ArohaTypography.headlineLarge
    val SectionTitle = ArohaTypography.titleLarge
    val TrackTitle = textStyle(15, 22, FontWeight.SemiBold, -.1f)
    val CardTitle = ArohaTypography.titleSmall
    val Metadata = textStyle(13, 20)
    val Caption = ArohaTypography.bodySmall
    val GroupLabel = textStyle(13, 20, FontWeight.SemiBold)
    val SettingTitle = textStyle(16, 24, FontWeight.Medium)
    val ActionTitle = ArohaTypography.titleMedium
    val ActionLabel = textStyle(14, 20, FontWeight.Medium)
    val Control = ArohaTypography.labelLarge
    val Chip = ArohaTypography.labelMedium
    val Navigation = ArohaTypography.labelSmall
    val Eyebrow = textStyle(11, 16, FontWeight.SemiBold, .9f)
    val Stat = textStyle(24, 32, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
    val Timestamp = ArohaTypography.labelMedium.copy(fontFeatureSettings = "tnum")
    val PlayerTitle = textStyle(30, 38, FontWeight.Bold, -.4f)
    val PlayerTitleRegular = ArohaTypography.headlineMedium
    val PlayerTitleCompact = textStyle(22, 30, FontWeight.SemiBold, -.15f)
    val GlimpseTitle = textStyle(18, 26, FontWeight.SemiBold, -.15f)
}
