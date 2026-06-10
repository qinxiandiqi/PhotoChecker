package cn.qinxiandiqi.photochecker.feature.home.model

data class ExifCategoryGroup(
    val category: ExifCategory,
    val displayNameResId: Int,
    val entries: List<ExifTagEntry>,
    val maxRisk: PrivacyRisk
)
