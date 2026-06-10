package cn.qinxiandiqi.photochecker.feature.home.model

data class TagMeta(
    val displayNameResId: Int,
    val category: ExifCategory,
    val risk: PrivacyRisk,
    val formatter: ExifValueType
)
