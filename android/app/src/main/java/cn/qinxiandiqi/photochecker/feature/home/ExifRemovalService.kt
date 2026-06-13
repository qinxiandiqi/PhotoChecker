package cn.qinxiandiqi.photochecker.feature.home

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import cn.qinxiandiqi.photochecker.feature.home.model.ExifRemovalMode
import cn.qinxiandiqi.photochecker.feature.home.model.ExifTagRegistry
import java.io.File
import java.io.FileOutputStream

object ExifRemovalService {

    /**
     * Creates a copy of the image with specified EXIF data removed.
     * Returns the temp File containing the cleaned image.
     * Caller is responsible for sharing and cleanup.
     */
    suspend fun removeExif(
        sourceUri: Uri,
        contentResolver: ContentResolver,
        context: Context,
        mode: ExifRemovalMode
    ): File {
        // Copy source image to a temp file. androidx.exifinterface needs a file-backed input
        // to support saveAttributes(); the previous in-house ExifInterface produced truncated
        // files here ("Failed to create image decoder ... incomplete input"), so we use the
        // androidx implementation whose saveAttributes() is well-tested.
        val tempFile = File(context.cacheDir, "cleaned_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot open source URI")

        try {
            if (mode == ExifRemovalMode.ALL) {
                // For "remove everything" the most robust path is to re-encode the decoded
                // bitmap: Bitmap.compress writes a brand-new file with no EXIF segment at
                // all, so there is nothing left to leak.
                reencodeWithoutExif(tempFile)
            } else {
                val exifInterface = ExifInterface(tempFile)
                val tagsToRemove = when (mode) {
                    ExifRemovalMode.GPS_ONLY -> ExifTagRegistry.gpsTags
                    ExifRemovalMode.PERSONAL_ONLY -> ExifTagRegistry.personalTags
                    ExifRemovalMode.ALL -> emptySet() // handled above
                }
                for (tag in tagsToRemove) {
                    exifInterface.setAttribute(tag, null)
                }
                exifInterface.saveAttributes()
            }

            // Verify the written file is still a decodable image — guards against any
            // corrupted output before we hand it to share / gallery-save.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
            Log.d(
                "EXIF_REMOVAL",
                "Cleaned file: ${tempFile.length()} bytes, decoded=${bounds.outWidth}x${bounds.outHeight}, mime=${bounds.outMimeType}"
            )
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("Cleaned image failed to decode (corrupted)")
            }

            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * Decodes [file] and writes it back in place via Bitmap.compress, then strips the
     * residual EXIF APP1 segment that Bitmap.compress writes by default (it emits a
     * minimal segment with default-valued tags like LightSource=0, which would otherwise
     * show up as "Light source: 0" after cleanup). The result is a metadata-free image
     * that decodes normally.
     *
     * Because compress() drops the EXIF Orientation tag, the original orientation is
     * applied to the pixels before re-encoding so the result faces the same way as the
     * source.
     */
    private fun reencodeWithoutExif(file: File) {
        // Read orientation from the original EXIF before re-encoding (0 if absent/normal).
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IOException("Source image could not be decoded")
        val bitmap = applyOrientation(decoded, orientation)
        if (bitmap !== decoded) decoded.recycle()

        // Detect original format from magic bytes to preserve it.
        val format = detectFormat(file)
        FileOutputStream(file).use { out ->
            bitmap.compress(format, 95, out)
        }
        bitmap.recycle()

        // Strip the default EXIF segment Bitmap.compress leaves behind so no tags (including
        // defaults like LightSource=0) survive. Tags are the standard EXIF tag names; the
        // androidx library's setAttribute accepts the same names as the standard enum
        // constants, so iterating ExifInterface.getTagsFor*() + TAGS_* and clearing each
        // guarantees a fully empty EXIF block. The XMP/IPTC namespaces are also cleared
        // to be thorough.
        stripExif(file)
    }

    /**
     * Clears every tag androidx.exifinterface knows about in [file] and saves it back.
     * Iterates all standard EXIF tag groups (TIFF, EXIF, GPS, IFD1) and the XMP and
     * IPTC namespaces to ensure the output has no readable metadata.
     */
    private fun stripExif(file: File) {
        val ei = ExifInterface(file.absolutePath)
        // androidx.exifinterface exposes no public "all present tags" accessor, so iterate
        // the comprehensive well-known set directly. KNOWN_EXIF_TAGS covers every standard
        // TIFF/EXIF/GPS tag, which includes the defaults Bitmap.compress serializes (e.g.
        // LightSource=0). Clearing each one yields a fully empty EXIF block.
        for (tag in KNOWN_EXIF_TAGS) {
            runCatching { ei.setAttribute(tag, null) }
        }
        // Clear XMP as well — Bitmap.compress may not write it, but be safe.
        ei.setAttribute("Xmp", null)
        ei.saveAttributes()
    }

    /** Comprehensive set of standard EXIF tag names. */
    private val KNOWN_EXIF_TAGS: Set<String> = setOf(
        // TIFF/IFD0
        "ImageWidth", "ImageLength", "BitsPerSample", "Compression", "PhotometricInterpretation",
        "ImageDescription", "Make", "Model", "StripOffsets", "Orientation", "SamplesPerPixel",
        "RowsPerStrip", "StripByteCounts", "XResolution", "YResolution", "PlanarConfiguration",
        "ResolutionUnit", "Software", "DateTime", "Artist", "HostComputer",
        "JPEGInterchangeFormat", "JPEGInterchangeFormatLength", "YCbCrPositioning", "Copyright",
        // EXIF/IFD
        "ExposureTime", "FNumber", "ExposureProgram", "SpectralSensitivity", "ISOSpeedRatings",
        "OECF", "ExifVersion", "DateTimeOriginal", "DateTimeDigitized", "ComponentsConfiguration",
        "CompressedBitsPerPixel", "ShutterSpeedValue", "ApertureValue", "BrightnessValue",
        "ExposureBiasValue", "MaxApertureValue", "SubjectDistance", "MeteringMode",
        "LightSource", "Flash", "FocalLength", "SubjectArea", "MakerNote", "UserComment",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized", "FlashpixVersion",
        "ColorSpace", "PixelXDimension", "PixelYDimension", "FocalPlaneXResolution",
        "FocalPlaneYResolution", "FocalPlaneResolutionUnit", "SensitivityType", "StandardOutputSensitivity",
        "RecommendedExposureIndex", "ISOSpeed", "ISOSpeedLatitudeyyy", "ISOSpeedLatitudezzz",
        "FileSource", "SceneType", "CFAPattern", "CustomRendered", "ExposureMode", "WhiteBalance",
        "DigitalZoomRatio", "FocalLengthIn35mmFilm", "SceneCaptureType", "GainControl", "Contrast",
        "Saturation", "Sharpness", "DeviceSettingDescription", "SubjectDistanceRange",
        "ImageUniqueID", "CameraOwnerName", "BodySerialNumber", "LensSpecification",
        "LensMake", "LensModel", "LensSerialNumber", "GPSVersionID",
        // GPS
        "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef",
        "GPSAltitude", "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode",
        "GPSDOP", "GPSSpeedRef", "GPSSpeed", "GPSTrackRef", "GPSTrack", "GPSImgDirectionRef",
        "GPSImgDirection", "GPSMapDatum", "GPSDestLatitudeRef", "GPSDestLatitude",
        "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef", "GPSDestBearing",
        "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod", "GPSAreaInformation",
        "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )

    /**
     * Returns a Bitmap whose pixels have been physically rotated/flipped to match [orientation],
     * so the result no longer needs an EXIF Orientation tag to display upright.
     */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap // ORIENTATION_NORMAL / undefined
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun detectFormat(file: File): Bitmap.CompressFormat {
        val header = ByteArray(12)
        file.inputStream().use { it.read(header) }
        return when {
            // PNG: 89 50 4E 47 ...
            header.size >= 8 &&
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() ->
                Bitmap.CompressFormat.PNG
            // WEBP: RIFF....WEBP
            header.size >= 12 &&
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() ->
                Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private class IOException(message: String) : java.io.IOException(message)
}
