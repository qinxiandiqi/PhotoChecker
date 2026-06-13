package cn.qinxiandiqi.photochecker.feature.home.model

import android.graphics.Bitmap

data class ConsistencyWarning(
    val type: WarningType,
    val messageResId: Int,
    val detail: String,
    val thumbnail: Bitmap? = null
)

enum class WarningType {
    DATETIME_MISMATCH,
    DIMENSION_MISMATCH,
    THUMBNAIL_MISMATCH
}
