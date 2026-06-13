package cn.qinxiandiqi.photochecker.feature.home

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import cn.qinxiandiqi.lib.exif.ExifInterface
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ConsistencyWarning
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategory
import cn.qinxiandiqi.photochecker.feature.home.model.ExifCategoryGroup
import cn.qinxiandiqi.photochecker.feature.home.model.ExifTagEntry
import cn.qinxiandiqi.photochecker.feature.home.model.ExifTagRegistry
import cn.qinxiandiqi.photochecker.feature.home.model.ExifValueType
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.feature.home.model.TagMeta
import cn.qinxiandiqi.photochecker.feature.home.model.WarningType
import java.util.concurrent.TimeUnit

object ExifAnalysisService {

    suspend fun analyze(
        uri: Uri,
        contentResolver: ContentResolver,
        context: Context
    ): ExifAnalysisResult {
        val entries = mutableListOf<ExifTagEntry>()
        val seenTagNames = mutableSetOf<String>()
        var exifInterface: ExifInterface? = null
        var thumbnailBitmap: android.graphics.Bitmap? = null

        contentResolver.openInputStream(uri)?.use { inputStream ->
            exifInterface = ExifInterface(inputStream)
            val ei = exifInterface!!

            for (tagGroup in ExifInterface.EXIF_TAGS) {
                for (tag in tagGroup) {
                    val tagName = tag.name
                    // Skip internal pointer tags
                    if (tagName in ExifTagRegistry.excludedTags) continue
                    // Skip duplicate tags (same tag name can appear in multiple IFD groups,
                    // e.g. ImageWidth exists in both IFD_TIFF_TAGS and IFD_THUMBNAIL_TAGS)
                    if (!seenTagNames.add(tagName)) continue

                    val rawValue = ei.getAttribute(tagName)
                    if (rawValue.isNullOrBlank()) continue

                    val meta = ExifTagRegistry.lookup(tagName) ?: TagMeta(
                        displayNameResId = 0, // 0 means use raw tag name
                        category = ExifCategory.OTHER,
                        risk = PrivacyRisk.NONE,
                        formatter = ExifValueType.PLAIN_TEXT
                    )

                    val formattedValue = ExifValueFormatter.format(
                        rawValue, meta.formatter, context
                    )

                    entries.add(
                        ExifTagEntry(
                            rawTagName = tagName,
                            displayNameResId = meta.displayNameResId,
                            rawValue = rawValue,
                            formattedValue = formattedValue,
                            category = meta.category,
                            risk = meta.risk
                        )
                    )
                }
            }

            // Decode the embedded thumbnail now, while the input stream is still open.
            // getThumbnailBitmap re-reads from the stream/FD when bytes aren't cached yet;
            // calling it after the stream closed throws "dup failed: EBADF".
            thumbnailBitmap = runCatching { ei.thumbnailBitmap }.getOrNull()
        }

        // Group by category
        val groups = entries
            .groupBy { it.category }
            .map { (category, categoryEntries) ->
                val titleResId = ExifTagRegistry.categoryTitleResIds[category]
                    ?: R.string.category_other
                val maxRisk = categoryEntries.maxOfOrNull { it.risk } ?: PrivacyRisk.NONE
                ExifCategoryGroup(category, titleResId, categoryEntries, maxRisk)
            }
            .sortedBy { it.category.ordinal }

        // Overall risk
        val overallRisk = groups
            .flatMap { it.entries }
            .maxOfOrNull { it.risk } ?: PrivacyRisk.NONE

        // GPS coordinates
        val gpsCoords = exifInterface?.latLong
        val hasGps = gpsCoords != null && gpsCoords.size == 2
        val gpsLat = if (hasGps) gpsCoords!![0] else null
        val gpsLng = if (hasGps) gpsCoords!![1] else null

        // Thumbnail
        val hasThumbnail = exifInterface?.hasThumbnail() == true

        // Decode actual image dimensions once (orientation-corrected for display).
        // Shared by the consistency check (raw dims) and the result (display dims).
        val decoded = decodeImageSize(uri, contentResolver, exifInterface)

        // Consistency checks
        val warnings = mutableListOf<ConsistencyWarning>()
        val ei = exifInterface

        if (ei != null) {
            // 1. DateTime mismatch: EXIF DateTimeOriginal vs file lastModified
            checkDateTimeMismatch(ei, uri, contentResolver, context, warnings)

            // 2. Dimension mismatch: EXIF PixelXDimension/PixelYDimension vs actual decoded size
            checkDimensionMismatch(ei, decoded, context, warnings)

            // 3. Thumbnail exists
            if (hasThumbnail) {
                warnings.add(
                    ConsistencyWarning(
                        type = WarningType.THUMBNAIL_MISMATCH,
                        messageResId = R.string.warning_thumbnail_exists,
                        detail = context.getString(R.string.warning_thumbnail_exists_detail),
                        thumbnail = thumbnailBitmap
                    )
                )
            }
        }

        return ExifAnalysisResult(
            uri = uri,
            categoryGroups = groups,
            overallRisk = overallRisk,
            hasGpsCoordinates = hasGps,
            gpsLatitude = gpsLat,
            gpsLongitude = gpsLng,
            hasThumbnail = hasThumbnail,
            imageWidth = decoded?.displayWidth ?: 0,
            imageHeight = decoded?.displayHeight ?: 0,
            consistencyWarnings = warnings
        )
    }

    private fun checkDateTimeMismatch(
        ei: ExifInterface,
        uri: Uri,
        contentResolver: ContentResolver,
        context: Context,
        warnings: MutableList<ConsistencyWarning>
    ) {
        val exifTime = ei.dateTimeOriginal ?: return

        // EXIF DateTimeOriginal records local time WITHOUT timezone.
        // ExifInterface.parseDateTime() treats it as UTC, producing a "pseudo-epoch".
        //
        // Case 1: OffsetTimeOriginal is present (e.g., "+08:00")
        //   parseDateTime already corrects to real UTC → can compare precisely (threshold: 1h)
        //
        // Case 2: OffsetTimeOriginal is absent (most cameras don't write it)
        //   We have NO way to know the capture timezone. Using the device's current timezone
        //   is wrong for photos taken while traveling. Instead, we skip timezone correction
        //   and use a large threshold (25h) to avoid false positives from timezone differences
        //   (max global offset is UTC+14 to UTC-12 = 26h spread).
        //   Real metadata tampering typically differs by much more than 1 day.
        val hasExplicitOffset = !ei.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL).isNullOrBlank()
        val thresholdHours = if (hasExplicitOffset) 1L else 25L

        val fileTime = try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateModifiedIndex = cursor.getColumnIndex("last_modified")
                    if (dateModifiedIndex >= 0) {
                        cursor.getLong(dateModifiedIndex)
                    } else {
                        // Fallback: use date_added from MediaStore
                        val dateTakenIndex = cursor.getColumnIndex("date_added")
                        if (dateTakenIndex >= 0) {
                            cursor.getLong(dateTakenIndex) * 1000
                        } else null
                    }
                } else null
            }
        } catch (_: Exception) { null }

        if (fileTime != null && fileTime > 0) {
            val diffMs = kotlin.math.abs(exifTime - fileTime)
            val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
            if (diffHours > thresholdHours) {
                val humanDiff = when {
                    diffHours >= 24 -> {
                        val days = diffHours / 24
                        context.resources.getQuantityString(
                            R.plurals.days_count, days.toInt(), days
                        )
                    }
                    else -> {
                        context.resources.getQuantityString(
                            R.plurals.hours_count, diffHours.toInt(), diffHours
                        )
                    }
                }
                warnings.add(
                    ConsistencyWarning(
                        type = WarningType.DATETIME_MISMATCH,
                        messageResId = R.string.warning_datetime_mismatch,
                        detail = context.getString(R.string.warning_datetime_mismatch_detail, humanDiff)
                    )
                )
            }
        }
    }

    private fun checkDimensionMismatch(
        ei: ExifInterface,
        decoded: DecodedSize?,
        context: Context,
        warnings: MutableList<ConsistencyWarning>
    ) {
        if (decoded == null) return
        val exifWidth = ei.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)?.toIntOrNull()
        val exifHeight = ei.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION)?.toIntOrNull()

        if (exifWidth != null && exifHeight != null) {
            val actualWidth = decoded.rawWidth
            val actualHeight = decoded.rawHeight

            if (actualWidth > 0 && actualHeight > 0
                && (exifWidth != actualWidth || exifHeight != actualHeight)
            ) {
                // Account for possible orientation swap: width/height may be transposed
                val isTransposed = exifWidth == actualHeight && exifHeight == actualWidth
                if (!isTransposed) {
                    warnings.add(
                        ConsistencyWarning(
                            type = WarningType.DIMENSION_MISMATCH,
                            messageResId = R.string.warning_dimension_mismatch,
                            detail = context.getString(
                                R.string.warning_dimension_mismatch_detail,
                                exifWidth, exifHeight, actualWidth, actualHeight
                            )
                        )
                    )
                }
            }
        }
    }

    /**
     * Holds decoded image dimensions. [rawWidth]/[rawHeight] are the unrotated pixel
     * dimensions from BitmapFactory (used by the consistency check);
     * [displayWidth]/[displayHeight] are corrected for EXIF orientation (used by the UI).
     */
    private data class DecodedSize(
        val rawWidth: Int,
        val rawHeight: Int,
        val displayWidth: Int,
        val displayHeight: Int
    )

    /**
     * Decodes image dimensions cheaply via inJustDecodeBounds and corrects for EXIF
     * orientation. Returns null if the stream can't be decoded.
     */
    private fun decodeImageSize(
        uri: Uri,
        contentResolver: ContentResolver,
        ei: ExifInterface?
    ): DecodedSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        val rawW = options.outWidth
        val rawH = options.outHeight
        if (rawW <= 0 || rawH <= 0) return null
        val rotation = ei?.rotationDegrees ?: 0
        return if (rotation == 90 || rotation == 270) {
            DecodedSize(rawW, rawH, displayWidth = rawH, displayHeight = rawW)
        } else {
            DecodedSize(rawW, rawH, displayWidth = rawW, displayHeight = rawH)
        }
    }
}
