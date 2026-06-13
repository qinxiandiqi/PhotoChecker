package cn.qinxiandiqi.photochecker.feature.home.model

import android.content.Context

data class ExifTagEntry(
    val rawTagName: String,
    val displayNameResId: Int,
    val rawValue: String,
    val formattedValue: String,
    val category: ExifCategory,
    val risk: PrivacyRisk
)

/**
 * Resolves the display name for this tag: the translated friendly name when a resource is
 * registered, otherwise the raw EXIF tag name. Shared by the EXIF list item (UI) and the
 * plain-text export (clipboard copy) so both stay in sync.
 */
fun ExifTagEntry.displayName(context: Context): String =
    if (displayNameResId != 0) context.getString(displayNameResId) else rawTagName
