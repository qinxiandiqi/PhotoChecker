package cn.qinxiandiqi.photochecker.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================
// App semantic colors (light + dark pairs)
// ============================================================

// Risk colors
val RiskHigh = Color(0xFFE53935)
val RiskHighContainerLight = Color(0xFFFFEBEE)
val RiskHighContainerDark = Color(0xFF5F0101)

val RiskMedium = Color(0xFFFF9800)
val RiskMediumContainerLight = Color(0xFFFFF3E0)
val RiskMediumContainerDark = Color(0xFF4A2800)

val RiskLow = Color(0xFF2196F3)
val RiskLowContainerLight = Color(0xFFE3F2FD)
val RiskLowContainerDark = Color(0xFF001D3D)

// Risk on-container (text on risk container backgrounds)
val RiskOnHighContainerLight = Color(0xFFC62828)
val RiskOnHighContainerDark = Color(0xFFFFB4AB)
val RiskOnMediumContainerLight = Color(0xFFE65100)
val RiskOnMediumContainerDark = Color(0xFFFFB68C)
val RiskOnLowContainerLight = Color(0xFF1565C0)
val RiskOnLowContainerDark = Color(0xFFA4C8FF)

// Warning colors
val WarningContainerLight = Color(0xFFFFF8E1)
val WarningContainerDark = Color(0xFF3E2E00)
val WarningIconLight = Color(0xFFF57F17)
val WarningIconDark = Color(0xFFFFD54F)
val WarningTitleLight = Color(0xFFE65100)
val WarningTitleDark = Color(0xFFFFB68C)
val WarningDetailLight = Color(0xFF795548)
val WarningDetailDark = Color(0xFFD7C0A8)

// Success color
val Success = Color(0xFF4CAF50)

// Neutral
val NeutralRisk = Color(0xFF9E9E9E)
val ScrimOverlay = Color(0x66000000)

// ============================================================
// AppColors data class + CompositionLocal
// ============================================================

data class AppColors(
    val riskHigh: Color,
    val riskHighContainer: Color,
    val riskOnHighContainer: Color,
    val riskMedium: Color,
    val riskMediumContainer: Color,
    val riskOnMediumContainer: Color,
    val riskLow: Color,
    val riskLowContainer: Color,
    val riskOnLowContainer: Color,
    val neutralRisk: Color,
    val warningContainer: Color,
    val warningIcon: Color,
    val warningTitle: Color,
    val warningDetail: Color,
    val success: Color,
    val scrimOverlay: Color,
)

val LightAppColors = AppColors(
    riskHigh = RiskHigh,
    riskHighContainer = RiskHighContainerLight,
    riskOnHighContainer = RiskOnHighContainerLight,
    riskMedium = RiskMedium,
    riskMediumContainer = RiskMediumContainerLight,
    riskOnMediumContainer = RiskOnMediumContainerLight,
    riskLow = RiskLow,
    riskLowContainer = RiskLowContainerLight,
    riskOnLowContainer = RiskOnLowContainerLight,
    neutralRisk = NeutralRisk,
    warningContainer = WarningContainerLight,
    warningIcon = WarningIconLight,
    warningTitle = WarningTitleLight,
    warningDetail = WarningDetailLight,
    success = Success,
    scrimOverlay = ScrimOverlay,
)

val DarkAppColors = AppColors(
    riskHigh = RiskHigh,
    riskHighContainer = RiskHighContainerDark,
    riskOnHighContainer = RiskOnHighContainerDark,
    riskMedium = RiskMedium,
    riskMediumContainer = RiskMediumContainerDark,
    riskOnMediumContainer = RiskOnMediumContainerDark,
    riskLow = RiskLow,
    riskLowContainer = RiskLowContainerDark,
    riskOnLowContainer = RiskOnLowContainerDark,
    neutralRisk = NeutralRisk,
    warningContainer = WarningContainerDark,
    warningIcon = WarningIconDark,
    warningTitle = WarningTitleDark,
    warningDetail = WarningDetailDark,
    success = Success,
    scrimOverlay = ScrimOverlay,
)

val LocalAppColors = compositionLocalOf { LightAppColors }
