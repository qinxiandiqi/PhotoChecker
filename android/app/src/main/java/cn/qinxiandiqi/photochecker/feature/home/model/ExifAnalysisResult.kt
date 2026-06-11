package cn.qinxiandiqi.photochecker.feature.home.model

import android.net.Uri

data class ExifAnalysisResult(
    val uri: Uri,
    val categoryGroups: List<ExifCategoryGroup>,
    val overallRisk: PrivacyRisk,
    val hasGpsCoordinates: Boolean,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val hasThumbnail: Boolean,
    val imageWidth: Int = 0, // Display-orientation-corrected width (0 if undecodable)
    val imageHeight: Int = 0, // Display-orientation-corrected height (0 if undecodable)
    val consistencyWarnings: List<ConsistencyWarning> = emptyList()
)
