package cn.qinxiandiqi.photochecker.feature.home.model

data class ConsistencyWarning(
    val type: WarningType,
    val messageResId: Int,
    val detail: String
)

enum class WarningType {
    DATETIME_MISMATCH,
    DIMENSION_MISMATCH,
    THUMBNAIL_MISMATCH
}
