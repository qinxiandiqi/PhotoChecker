package cn.qinxiandiqi.photochecker.feature.home

import android.content.Context
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ExifValueType
import java.text.SimpleDateFormat
import java.util.Locale

object ExifValueFormatter {

    fun format(rawValue: String, type: ExifValueType, context: Context): String {
        if (rawValue.isBlank()) return rawValue
        return when (type) {
            ExifValueType.PLAIN_TEXT -> rawValue

            ExifValueType.RATIONAL_APERTURE -> formatAperture(rawValue)
            ExifValueType.RATIONAL_FOCAL -> formatFocal(rawValue, context)
            ExifValueType.RATIONAL_EXPOSURE -> formatExposure(rawValue, context)
            ExifValueType.RATIONAL_DISTANCE -> formatDistance(rawValue, context)
            ExifValueType.GPS_COORDINATE -> formatGpsCoordinate(rawValue)
            ExifValueType.GPS_DECIMAL -> formatGpsDecimal(rawValue)
            ExifValueType.DATETIME -> formatDateTime(rawValue)
            ExifValueType.ORIENTATION -> formatOrientation(rawValue, context)
            ExifValueType.RESOLUTION -> formatResolution(rawValue, context)
            ExifValueType.METERING_MODE -> formatMeteringMode(rawValue, context)
            ExifValueType.FLASH -> formatFlash(rawValue, context)
            ExifValueType.EXPOSURE_PROGRAM -> formatExposureProgram(rawValue, context)
            ExifValueType.WHITE_BALANCE -> formatWhiteBalance(rawValue, context)
            ExifValueType.SCENE_CAPTURE -> formatSceneCapture(rawValue, context)
            ExifValueType.COLOR_SPACE -> formatColorSpace(rawValue, context)
            ExifValueType.EXPOSURE_MODE -> formatExposureMode(rawValue, context)
            ExifValueType.CONTRAST_SATURATION_SHARPNESS -> formatCss(rawValue, context)
            ExifValueType.LENS_SPECIFICATION -> formatLensSpec(rawValue)
        }
    }

    // --- Rational helpers ---

    /** Parse "num/den" to a Double. Returns null if not in rational format. */
    private fun parseRational(value: String): Double? {
        val parts = value.trim().split("/")
        if (parts.size != 2) return null
        val num = parts[0].trim().toDoubleOrNull() ?: return null
        val den = parts[1].trim().toDoubleOrNull() ?: return null
        if (den == 0.0) return null
        return num / den
    }

    /** Parse "num/den, num/den, num/den" (3 rationals for GPS DMS). */
    private fun parseDmsRationals(value: String): Triple<Double, Double, Double>? {
        val parts = value.split(",").map { it.trim() }
        if (parts.size != 3) return null
        val deg = parseRational(parts[0]) ?: return null
        val min = parseRational(parts[1]) ?: return null
        val sec = parseRational(parts[2]) ?: return null
        return Triple(deg, min, sec)
    }

    // --- Formatters ---

    private fun formatAperture(value: String): String {
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return "f/${"%.1f".format(d)}"
    }

    private fun formatFocal(value: String, context: Context): String {
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return "${"%.1f".format(d)} ${context.getString(R.string.unit_mm)}"
    }

    private fun formatExposure(value: String, context: Context): String {
        // Try rational first
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return when {
            d < 1.0 -> "1/${"%.0f".format(1.0 / d)} ${context.getString(R.string.unit_seconds)}"
            else -> "${"%.1f".format(d)} ${context.getString(R.string.unit_seconds)}"
        }
    }

    private fun formatDistance(value: String, context: Context): String {
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return "${"%.1f".format(d)} ${context.getString(R.string.unit_meters)}"
    }

    private fun formatGpsCoordinate(value: String): String {
        val (deg, min, sec) = parseDmsRationals(value) ?: return value
        val decimal = deg + min / 60.0 + sec / 3600.0
        return "${"%.6f".format(decimal)}°"
    }

    private fun formatGpsDecimal(value: String): String {
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return "${"%.2f".format(d)}"
    }

    private fun formatDateTime(value: String): String {
        // EXIF format: "2024:03:14 10:30:00" or "2024:03:14T10:30:00"
        val cleaned = value.replace(":", "-").let {
            // Only replace the first two colons (date part), keep time colons
            val datePart = value.substringBefore(" ")
            val timePart = value.substringAfter(" ", "")
            datePart.replace(":", "-") +
                    if (timePart.isNotEmpty()) " $timePart" else ""
        }
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat.getDateTimeInstance()
            val date = parser.parse(cleaned)
            if (date != null) formatter.format(date) else cleaned
        } catch (e: Exception) {
            cleaned
        }
    }

    private fun formatOrientation(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            1 -> context.getString(R.string.value_orientation_normal)
            2 -> context.getString(R.string.value_orientation_flip_h)
            3 -> context.getString(R.string.value_orientation_rotate_180)
            4 -> context.getString(R.string.value_orientation_flip_v)
            5 -> context.getString(R.string.value_orientation_transpose)
            6 -> context.getString(R.string.value_orientation_rotate_90)
            7 -> context.getString(R.string.value_orientation_transverse)
            8 -> context.getString(R.string.value_orientation_rotate_270)
            else -> context.getString(R.string.value_orientation_unknown, n)
        }
    }

    private fun formatResolution(value: String, context: Context): String {
        val d = parseRational(value) ?: value.toDoubleOrNull() ?: return value
        return "${"%.0f".format(d)} ${context.getString(R.string.unit_dpi)}"
    }

    private fun formatMeteringMode(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_metering_unknown)
            1 -> context.getString(R.string.value_metering_average)
            2 -> context.getString(R.string.value_metering_center)
            3 -> context.getString(R.string.value_metering_spot)
            4 -> context.getString(R.string.value_metering_matrix)
            5 -> context.getString(R.string.value_metering_partial)
            else -> value
        }
    }

    private fun formatFlash(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        // Flash is a bit field. Lower 3 bits are the main state.
        // Bits 0-1: 0=not fired, 1=fired
        // Bit 2: 0=no strobe return, 1=strobe return
        // Bits 3-4: flash mode (0=unknown, 1=compulsory, 2=compulsory off, 3=auto)
        return when {
            n == 0x00 || n == 0x10 || n == 0x18 -> context.getString(R.string.value_flash_not_fired)
            n == 0x01 || n == 0x05 -> context.getString(R.string.value_flash_fired)
            n == 0x07 -> context.getString(R.string.value_flash_fired_return)
            n == 0x09 || n == 0x0D -> context.getString(R.string.value_flash_on_no_return)
            n == 0x0F -> context.getString(R.string.value_flash_on_return)
            n == 0x08 || n == 0x10 || n == 0x18 -> context.getString(R.string.value_flash_off)
            n == 0x19 -> context.getString(R.string.value_flash_auto_fired)
            n == 0x15 || n == 0x58 || n == 0x5D -> context.getString(R.string.value_flash_auto_not_fired)
            n == 0x20 -> context.getString(R.string.value_flash_no_flash)
            n and 0x01 != 0 -> context.getString(R.string.value_flash_fired)
            else -> context.getString(R.string.value_flash_not_fired)
        }
    }

    private fun formatExposureProgram(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_exposure_program_not_defined)
            1 -> context.getString(R.string.value_exposure_program_manual)
            2 -> context.getString(R.string.value_exposure_program_normal)
            3 -> context.getString(R.string.value_exposure_program_aperture)
            4 -> context.getString(R.string.value_exposure_program_shutter)
            5 -> context.getString(R.string.value_exposure_program_creative)
            6 -> context.getString(R.string.value_exposure_program_action)
            7 -> context.getString(R.string.value_exposure_program_portrait)
            8 -> context.getString(R.string.value_exposure_program_landscape)
            else -> value
        }
    }

    private fun formatWhiteBalance(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_white_balance_auto)
            1 -> context.getString(R.string.value_white_balance_manual)
            else -> value
        }
    }

    private fun formatSceneCapture(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_scene_standard)
            1 -> context.getString(R.string.value_scene_landscape)
            2 -> context.getString(R.string.value_scene_portrait)
            3 -> context.getString(R.string.value_scene_night)
            else -> value
        }
    }

    private fun formatColorSpace(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            1 -> context.getString(R.string.value_color_space_srgb)
            0xFFFF -> context.getString(R.string.value_color_space_uncalibrated)
            else -> value
        }
    }

    private fun formatExposureMode(value: String, context: Context): String {
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_exposure_mode_auto)
            1 -> context.getString(R.string.value_exposure_mode_manual)
            2 -> context.getString(R.string.value_exposure_mode_auto_bracket)
            else -> value
        }
    }

    private fun formatCss(value: String, context: Context): String {
        // Contrast / Saturation / Sharpness share the same mapping
        val n = value.toIntOrNull() ?: return value
        return when (n) {
            0 -> context.getString(R.string.value_css_normal)
            1 -> context.getString(R.string.value_css_soft)
            2 -> context.getString(R.string.value_css_hard)
            else -> value
        }
    }

    private fun formatLensSpec(value: String): String {
        // LensSpecification: 4 rationals "min_focal/max_focal min_aperture_at_min/max_aperture_at_max"
        val parts = value.split(",").mapNotNull { parseRational(it.trim()) }
        if (parts.size < 2) return value
        val focal = if (parts[0] == parts[1]) {
            "${parts[0].toInt()}mm"
        } else {
            "${parts[0].toInt()}-${parts[1].toInt()}mm"
        }
        val aperture = if (parts.size >= 4) {
            if (parts[2] == parts[3]) {
                " f/${"%.1f".format(parts[2])}"
            } else {
                " f/${"%.1f".format(parts[2])}-${"%.1f".format(parts[3])}"
            }
        } else if (parts.size >= 3) {
            " f/${"%.1f".format(parts[2])}"
        } else ""
        return "$focal$aperture"
    }
}
