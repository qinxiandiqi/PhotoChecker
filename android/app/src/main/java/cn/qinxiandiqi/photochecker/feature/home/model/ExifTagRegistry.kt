package cn.qinxiandiqi.photochecker.feature.home.model

import cn.qinxiandiqi.photochecker.R

object ExifTagRegistry {

    private val registry: Map<String, TagMeta> = mapOf(

        // ===== BASIC =====
        "ImageWidth" to TagMeta(R.string.tag_image_width, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ImageLength" to TagMeta(R.string.tag_image_height, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "BitsPerSample" to TagMeta(R.string.tag_bits_per_sample, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "Orientation" to TagMeta(R.string.tag_orientation, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.ORIENTATION),
        "XResolution" to TagMeta(R.string.tag_x_resolution, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.RESOLUTION),
        "YResolution" to TagMeta(R.string.tag_y_resolution, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.RESOLUTION),
        "ResolutionUnit" to TagMeta(R.string.tag_resolution_unit, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "YCbCrPositioning" to TagMeta(R.string.tag_ycbcr_positioning, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "PixelXDimension" to TagMeta(R.string.tag_pixel_x_dimension, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "PixelYDimension" to TagMeta(R.string.tag_pixel_y_dimension, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ColorSpace" to TagMeta(R.string.tag_color_space, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.COLOR_SPACE),
        "Compression" to TagMeta(R.string.tag_compression, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "SamplesPerPixel" to TagMeta(R.string.tag_samples_per_pixel, ExifCategory.BASIC, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),

        // ===== CAMERA =====
        "Make" to TagMeta(R.string.tag_make, ExifCategory.CAMERA, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "Model" to TagMeta(R.string.tag_model, ExifCategory.CAMERA, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "LensModel" to TagMeta(R.string.tag_lens_model, ExifCategory.CAMERA, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "LensMake" to TagMeta(R.string.tag_lens_make, ExifCategory.CAMERA, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "LensSpecification" to TagMeta(R.string.tag_lens_specification, ExifCategory.CAMERA, PrivacyRisk.LOW, ExifValueType.LENS_SPECIFICATION),
        "FocalLength" to TagMeta(R.string.tag_focal_length, ExifCategory.CAMERA, PrivacyRisk.NONE, ExifValueType.RATIONAL_FOCAL),
        "FocalLengthIn35mmFilm" to TagMeta(R.string.tag_focal_length_35mm, ExifCategory.CAMERA, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "FocalPlaneXResolution" to TagMeta(R.string.tag_focal_plane_x_res, ExifCategory.CAMERA, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "FocalPlaneYResolution" to TagMeta(R.string.tag_focal_plane_y_res, ExifCategory.CAMERA, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "FocalPlaneResolutionUnit" to TagMeta(R.string.tag_focal_plane_res_unit, ExifCategory.CAMERA, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "CameraOwnerName" to TagMeta(R.string.tag_camera_owner, ExifCategory.CAMERA, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "BodySerialNumber" to TagMeta(R.string.tag_body_serial, ExifCategory.CAMERA, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "LensSerialNumber" to TagMeta(R.string.tag_lens_serial, ExifCategory.CAMERA, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "ImageUniqueID" to TagMeta(R.string.tag_image_unique_id, ExifCategory.CAMERA, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),

        // ===== EXPOSURE =====
        "ExposureTime" to TagMeta(R.string.tag_exposure_time, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.RATIONAL_EXPOSURE),
        "FNumber" to TagMeta(R.string.tag_f_number, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.RATIONAL_APERTURE),
        "ExposureProgram" to TagMeta(R.string.tag_exposure_program, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.EXPOSURE_PROGRAM),
        "PhotographicSensitivity" to TagMeta(R.string.tag_iso, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ShutterSpeedValue" to TagMeta(R.string.tag_shutter_speed, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ApertureValue" to TagMeta(R.string.tag_aperture_value, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.RATIONAL_APERTURE),
        "BrightnessValue" to TagMeta(R.string.tag_brightness, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ExposureBiasValue" to TagMeta(R.string.tag_exposure_bias, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "MaxApertureValue" to TagMeta(R.string.tag_max_aperture, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.RATIONAL_APERTURE),
        "MeteringMode" to TagMeta(R.string.tag_metering_mode, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.METERING_MODE),
        "LightSource" to TagMeta(R.string.tag_light_source, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "Flash" to TagMeta(R.string.tag_flash, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.FLASH),
        "FlashEnergy" to TagMeta(R.string.tag_flash_energy, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "ExposureMode" to TagMeta(R.string.tag_exposure_mode, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.EXPOSURE_MODE),
        "WhiteBalance" to TagMeta(R.string.tag_white_balance, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.WHITE_BALANCE),
        "DigitalZoomRatio" to TagMeta(R.string.tag_digital_zoom, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "SceneCaptureType" to TagMeta(R.string.tag_scene_capture, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.SCENE_CAPTURE),
        "GainControl" to TagMeta(R.string.tag_gain_control, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.CONTRAST_SATURATION_SHARPNESS),
        "Contrast" to TagMeta(R.string.tag_contrast, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.CONTRAST_SATURATION_SHARPNESS),
        "Saturation" to TagMeta(R.string.tag_saturation, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.CONTRAST_SATURATION_SHARPNESS),
        "Sharpness" to TagMeta(R.string.tag_sharpness, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.CONTRAST_SATURATION_SHARPNESS),
        "SubjectDistance" to TagMeta(R.string.tag_subject_distance, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.RATIONAL_DISTANCE),
        "SubjectDistanceRange" to TagMeta(R.string.tag_subject_distance_range, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "SensingMethod" to TagMeta(R.string.tag_sensing_method, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "Gamma" to TagMeta(R.string.tag_gamma, ExifCategory.EXPOSURE, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),

        // ===== GPS =====
        "GPSLatitudeRef" to TagMeta(R.string.tag_gps_latitude_ref, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSLatitude" to TagMeta(R.string.tag_gps_latitude, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.GPS_COORDINATE),
        "GPSLongitudeRef" to TagMeta(R.string.tag_gps_longitude_ref, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSLongitude" to TagMeta(R.string.tag_gps_longitude, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.GPS_COORDINATE),
        "GPSAltitude" to TagMeta(R.string.tag_gps_altitude, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.GPS_DECIMAL),
        "GPSAltitudeRef" to TagMeta(R.string.tag_gps_altitude_ref, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSTimeStamp" to TagMeta(R.string.tag_gps_timestamp, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSDateStamp" to TagMeta(R.string.tag_gps_datestamp, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSSpeed" to TagMeta(R.string.tag_gps_speed, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.GPS_DECIMAL),
        "GPSSpeedRef" to TagMeta(R.string.tag_gps_speed_ref, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSImgDirection" to TagMeta(R.string.tag_gps_img_direction, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSImgDirectionRef" to TagMeta(R.string.tag_gps_img_direction_ref, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSProcessingMethod" to TagMeta(R.string.tag_gps_processing_method, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSMapDatum" to TagMeta(R.string.tag_gps_map_datum, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),
        "GPSSatellites" to TagMeta(R.string.tag_gps_satellites, ExifCategory.GPS, PrivacyRisk.HIGH, ExifValueType.PLAIN_TEXT),

        // ===== DATETIME =====
        "DateTime" to TagMeta(R.string.tag_datetime, ExifCategory.DATETIME, PrivacyRisk.LOW, ExifValueType.DATETIME),
        "DateTimeOriginal" to TagMeta(R.string.tag_datetime_original, ExifCategory.DATETIME, PrivacyRisk.LOW, ExifValueType.DATETIME),
        "DateTimeDigitized" to TagMeta(R.string.tag_datetime_digitized, ExifCategory.DATETIME, PrivacyRisk.LOW, ExifValueType.DATETIME),
        "SubSecTime" to TagMeta(R.string.tag_subsec_time, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "SubSecTimeOriginal" to TagMeta(R.string.tag_subsec_time_original, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "SubSecTimeDigitized" to TagMeta(R.string.tag_subsec_time_digitized, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "OffsetTime" to TagMeta(R.string.tag_offset_time, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "OffsetTimeOriginal" to TagMeta(R.string.tag_offset_time_original, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "OffsetTimeDigitized" to TagMeta(R.string.tag_offset_time_digitized, ExifCategory.DATETIME, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),

        // ===== EDITING =====
        "Software" to TagMeta(R.string.tag_software, ExifCategory.EDITING, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "MakerNote" to TagMeta(R.string.tag_maker_note, ExifCategory.EDITING, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "UserComment" to TagMeta(R.string.tag_user_comment, ExifCategory.EDITING, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "ImageDescription" to TagMeta(R.string.tag_image_description, ExifCategory.EDITING, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "Artist" to TagMeta(R.string.tag_artist, ExifCategory.EDITING, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "Copyright" to TagMeta(R.string.tag_copyright, ExifCategory.EDITING, PrivacyRisk.MEDIUM, ExifValueType.PLAIN_TEXT),
        "CustomRendered" to TagMeta(R.string.tag_custom_rendered, ExifCategory.EDITING, PrivacyRisk.LOW, ExifValueType.PLAIN_TEXT),
        "ExifVersion" to TagMeta(R.string.tag_exif_version, ExifCategory.EDITING, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
        "FlashpixVersion" to TagMeta(R.string.tag_flashpix_version, ExifCategory.EDITING, PrivacyRisk.NONE, ExifValueType.PLAIN_TEXT),
    )

    /** Tags to exclude from display (internal pointers, offsets, raw data) */
    val excludedTags: Set<String> = setOf(
        "ExifIFDPointer",
        "GPSInfoIFDPointer",
        "InteroperabilityIFDPointer",
        "SubIFDPointer",
        "CameraSettingsIFDPointer",
        "ImageProcessingIFDPointer",
        "StripOffsets",
        "JPEGInterchangeFormat",
        "JPEGInterchangeFormatLength",
        "StripByteCounts",
        "RowsPerStrip",
        "JpgFromRaw",
        "ThumbnailImageWidth",
        "ThumbnailImageLength",
        "ThumbnailOrientation",
        "ThumbnailCompression",
        "ThumbnailXResolution",
        "ThumbnailYResolution",
        "ThumbnailResolutionUnit",
        "ThumbnailJPEGInterchangeFormat",
        "ThumbnailJPEGInterchangeFormatLength",
        "GPSVersionID",
        "GPSStatus",
        "GPSMeasureMode",
        "GPSDOP",
        "GPSDestLatitude",
        "GPSDestLatitudeRef",
        "GPSDestLongitude",
        "GPSDestLongitudeRef",
        "GPSDestBearing",
        "GPSDestBearingRef",
        "GPSDifferential",
        "GPSHPositioningError",
        "InteropIndex",
        "InteropVersion",
        "ThumbnailYCbCrPositioning",
    )

    /** All GPS tag names for selective removal */
    val gpsTags: Set<String> = registry.keys.filter { it.startsWith("GPS") }.toSet()

    /** Tags that can identify a person for selective removal */
    val personalTags: Set<String> = setOf(
        "CameraOwnerName",
        "BodySerialNumber",
        "LensSerialNumber",
        "ImageUniqueID",
        "Artist",
        "UserComment",
        "Copyright",
    )

    fun lookup(tagName: String): TagMeta? = registry[tagName]

    /** Category display name string resource ID for each category */
    val categoryTitleResIds: Map<ExifCategory, Int> = mapOf(
        ExifCategory.BASIC to R.string.category_basic,
        ExifCategory.CAMERA to R.string.category_camera,
        ExifCategory.EXPOSURE to R.string.category_exposure,
        ExifCategory.GPS to R.string.category_gps,
        ExifCategory.DATETIME to R.string.category_datetime,
        ExifCategory.EDITING to R.string.category_editing,
        ExifCategory.OTHER to R.string.category_other,
    )
}
