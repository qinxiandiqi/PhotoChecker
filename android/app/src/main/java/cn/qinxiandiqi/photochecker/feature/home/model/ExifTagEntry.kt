package cn.qinxiandiqi.photochecker.feature.home.model

data class ExifTagEntry(
    val rawTagName: String,
    val displayNameResId: Int,
    val rawValue: String,
    val formattedValue: String,
    val category: ExifCategory,
    val risk: PrivacyRisk
)
