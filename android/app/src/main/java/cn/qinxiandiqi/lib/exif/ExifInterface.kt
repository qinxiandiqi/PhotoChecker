/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.qinxiandiqi.lib.exif

import android.annotation.SuppressLint
import android.content.res.AssetManager.AssetInputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import android.system.OsConstants
import android.util.Log
import android.util.Pair
import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.min

/**
 * This is a class for reading and writing Exif tags in various image file formats.
 *
 *
 * Supported for reading: JPEG, PNG, WebP, HEIF, DNG, CR2, NEF, NRW, ARW, RW2, ORF, PEF, SRW, RAF.
 *
 *
 * Supported for writing: JPEG, PNG, WebP.
 *
 *
 * Note: JPEG and HEIF files may contain XMP data either inside the Exif data chunk or outside of
 * it. This class will search both locations for XMP data, but if XMP data exist both inside and
 * outside Exif, will favor the XMP data inside Exif over the one outside.
 */
class ExifInterface {
    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(*[STREAM_TYPE_FULL_IMAGE_DATA, STREAM_TYPE_EXIF_DATA_ONLY])
    annotation class ExifStreamType

    // A class for indicating EXIF rational type.
    private class Rational(numerator: Long, denominator: Long) {
        var numerator: Long
        var denominator: Long

        constructor(value: Double) : this((value * 10000).toLong(), 10000)

        init {
            // Handle erroneous case
            if (denominator == 0L) {
                this.numerator = 0
                this.denominator = 1
            } else {
                this.numerator = numerator
                this.denominator = denominator
            }

        }

        override fun toString(): String {
            return "$numerator/$denominator"
        }

        fun calculate(): Double {
            return numerator.toDouble() / denominator
        }
    }

    // A class for indicating EXIF attribute.
    private class ExifAttribute(
        val format: Int,
        val numberOfComponents: Int,
        val bytesOffset: Long,
        val bytes: ByteArray
    ) {
        constructor(format: Int, numberOfComponents: Int, bytes: ByteArray) : this(
            format,
            numberOfComponents,
            BYTES_OFFSET_UNKNOWN,
            bytes
        )

        override fun toString(): String {
            return "(" + IFD_FORMAT_NAMES[format] + ", data length:" + bytes.size + ")"
        }

        fun getValue(byteOrder: ByteOrder): Any? {
            var inputStream: ByteOrderedDataInputStream? = null
            try {
                inputStream = ByteOrderedDataInputStream(bytes)
                inputStream.setByteOrder(byteOrder)
                when (format) {
                    IFD_FORMAT_BYTE, IFD_FORMAT_SBYTE -> {
                        // Exception for GPSAltitudeRef tag
                        if (bytes.size == 1 && bytes[0] >= 0 && bytes[0] <= 1) {
                            return String(charArrayOf((bytes[0] + '0'.code.toByte()).toChar()))
                        }
                        return String(bytes, ASCII)
                    }

                    IFD_FORMAT_UNDEFINED, IFD_FORMAT_STRING -> {
                        var index = 0
                        if (numberOfComponents >= EXIF_ASCII_PREFIX.size) {
                            var same = true
                            var i = 0
                            while (i < EXIF_ASCII_PREFIX.size) {
                                if (bytes[i] != EXIF_ASCII_PREFIX[i]) {
                                    same = false
                                    break
                                }
                                ++i
                            }
                            if (same) {
                                index = EXIF_ASCII_PREFIX.size
                            }
                        }

                        val stringBuilder = StringBuilder()
                        while (index < numberOfComponents) {
                            val ch = bytes[index].toInt()
                            if (ch == 0) {
                                break
                            }
                            if (ch >= 32) {
                                stringBuilder.append(ch.toChar())
                            } else {
                                stringBuilder.append('?')
                            }
                            ++index
                        }
                        return stringBuilder.toString()
                    }

                    IFD_FORMAT_USHORT -> {
                        val values = IntArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readUnsignedShort()
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_ULONG -> {
                        val values = LongArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readUnsignedInt()
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_URATIONAL -> {
                        val values = arrayOfNulls<Rational>(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            val numerator = inputStream.readUnsignedInt()
                            val denominator = inputStream.readUnsignedInt()
                            values[i] = Rational(numerator, denominator)
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_SSHORT -> {
                        val values = IntArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readShort().toInt()
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_SLONG -> {
                        val values = IntArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readInt()
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_SRATIONAL -> {
                        val values = arrayOfNulls<Rational>(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            val numerator = inputStream.readInt().toLong()
                            val denominator = inputStream.readInt().toLong()
                            values[i] = Rational(numerator, denominator)
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_SINGLE -> {
                        val values = DoubleArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readFloat().toDouble()
                            ++i
                        }
                        return values
                    }

                    IFD_FORMAT_DOUBLE -> {
                        val values = DoubleArray(numberOfComponents)
                        var i = 0
                        while (i < numberOfComponents) {
                            values[i] = inputStream.readDouble()
                            ++i
                        }
                        return values
                    }

                    else -> return null
                }
            } catch (e: IOException) {
                Log.w(TAG, "IOException occurred during reading a value", e)
                return null
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "IOException occurred while closing InputStream", e)
                    }
                }
            }
        }

        fun getDoubleValue(byteOrder: ByteOrder): Double {
            val value = getValue(byteOrder)
                ?: throw NumberFormatException("NULL can't be converted to a double value")
            if (value is String) {
                return value.toDouble()
            }
            if (value is LongArray) {
                val array = value
                if (array.size == 1) {
                    return array[0].toDouble()
                }
                throw NumberFormatException("There are more than one component")
            }
            if (value is IntArray) {
                val array = value
                if (array.size == 1) {
                    return array[0].toDouble()
                }
                throw NumberFormatException("There are more than one component")
            }
            if (value is DoubleArray) {
                val array = value
                if (array.size == 1) {
                    return array[0]
                }
                throw NumberFormatException("There are more than one component")
            }
            if (value is Array<*> && value.isArrayOf<Rational>()) {
                val array = value as Array<Rational>
                if (array.size == 1) {
                    return array[0].calculate()
                }
                throw NumberFormatException("There are more than one component")
            }
            throw NumberFormatException("Couldn't find a double value")
        }

        fun getIntValue(byteOrder: ByteOrder): Int {
            val value = getValue(byteOrder)
                ?: throw NumberFormatException("NULL can't be converted to a integer value")
            if (value is String) {
                return value.toInt()
            }
            if (value is LongArray) {
                val array = value
                if (array.size == 1) {
                    return array[0].toInt()
                }
                throw NumberFormatException("There are more than one component")
            }
            if (value is IntArray) {
                val array = value
                if (array.size == 1) {
                    return array[0]
                }
                throw NumberFormatException("There are more than one component")
            }
            throw NumberFormatException("Couldn't find a integer value")
        }

        fun getStringValue(byteOrder: ByteOrder): String? {
            val value = getValue(byteOrder) ?: return null
            if (value is String) {
                return value
            }

            val stringBuilder = StringBuilder()
            if (value is LongArray) {
                val array = value
                for (i in array.indices) {
                    stringBuilder.append(array[i])
                    if (i + 1 != array.size) {
                        stringBuilder.append(",")
                    }
                }
                return stringBuilder.toString()
            }
            if (value is IntArray) {
                val array = value
                for (i in array.indices) {
                    stringBuilder.append(array[i])
                    if (i + 1 != array.size) {
                        stringBuilder.append(",")
                    }
                }
                return stringBuilder.toString()
            }
            if (value is DoubleArray) {
                val array = value
                for (i in array.indices) {
                    stringBuilder.append(array[i])
                    if (i + 1 != array.size) {
                        stringBuilder.append(",")
                    }
                }
                return stringBuilder.toString()
            }
            if (value is Array<*> && value.isArrayOf<Rational>()) {
                val array = value as Array<Rational>
                for (i in array.indices) {
                    stringBuilder.append(array[i].numerator)
                    stringBuilder.append('/')
                    stringBuilder.append(array[i].denominator)
                    if (i + 1 != array.size) {
                        stringBuilder.append(",")
                    }
                }
                return stringBuilder.toString()
            }
            return null
        }

        fun size(): Int {
            return IFD_FORMAT_BYTES_PER_FORMAT[format] * numberOfComponents
        }

        companion object {
            const val BYTES_OFFSET_UNKNOWN: Long = -1

            fun createUShort(values: IntArray, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_USHORT] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putShort(value.toShort())
                }
                return ExifAttribute(IFD_FORMAT_USHORT, values.size, buffer.array())
            }

            fun createUShort(value: Int, byteOrder: ByteOrder): ExifAttribute {
                return createUShort(intArrayOf(value), byteOrder)
            }

            fun createULong(values: LongArray, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_ULONG] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putInt(value.toInt())
                }
                return ExifAttribute(IFD_FORMAT_ULONG, values.size, buffer.array())
            }

            fun createULong(value: Long, byteOrder: ByteOrder): ExifAttribute {
                return createULong(longArrayOf(value), byteOrder)
            }

            fun createSLong(values: IntArray, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_SLONG] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putInt(value)
                }
                return ExifAttribute(IFD_FORMAT_SLONG, values.size, buffer.array())
            }

            fun createSLong(value: Int, byteOrder: ByteOrder): ExifAttribute {
                return createSLong(intArrayOf(value), byteOrder)
            }

            fun createByte(value: String): ExifAttribute {
                // Exception for GPSAltitudeRef tag
                if (value.length == 1 && value[0] >= '0' && value[0] <= '1') {
                    val bytes = byteArrayOf((value[0].code - '0'.code).toByte())
                    return ExifAttribute(IFD_FORMAT_BYTE, bytes.size, bytes)
                }
                val ascii = value.toByteArray(ASCII)
                return ExifAttribute(IFD_FORMAT_BYTE, ascii.size, ascii)
            }

            fun createString(value: String): ExifAttribute {
                val ascii = (value + '\u0000').toByteArray(ASCII)
                return ExifAttribute(IFD_FORMAT_STRING, ascii.size, ascii)
            }

            fun createURational(values: Array<Rational>, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_URATIONAL] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putInt(value.numerator.toInt())
                    buffer.putInt(value.denominator.toInt())
                }
                return ExifAttribute(IFD_FORMAT_URATIONAL, values.size, buffer.array())
            }

            fun createURational(value: Rational, byteOrder: ByteOrder): ExifAttribute {
                return createURational(arrayOf(value), byteOrder)
            }

            fun createSRational(values: Array<Rational>, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_SRATIONAL] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putInt(value.numerator.toInt())
                    buffer.putInt(value.denominator.toInt())
                }
                return ExifAttribute(IFD_FORMAT_SRATIONAL, values.size, buffer.array())
            }

            fun createSRational(value: Rational, byteOrder: ByteOrder): ExifAttribute {
                return createSRational(arrayOf(value), byteOrder)
            }

            fun createDouble(values: DoubleArray, byteOrder: ByteOrder): ExifAttribute {
                val buffer = ByteBuffer.wrap(
                    ByteArray(IFD_FORMAT_BYTES_PER_FORMAT[IFD_FORMAT_DOUBLE] * values.size)
                )
                buffer.order(byteOrder)
                for (value in values) {
                    buffer.putDouble(value)
                }
                return ExifAttribute(IFD_FORMAT_DOUBLE, values.size, buffer.array())
            }

            fun createDouble(value: Double, byteOrder: ByteOrder): ExifAttribute {
                return createDouble(doubleArrayOf(value), byteOrder)
            }
        }
    }

    // A class for indicating EXIF tag.
    class ExifTag {
        val number: Int
        val name: String
        val primaryFormat: Int
        val secondaryFormat: Int

        internal constructor(name: String, number: Int, format: Int) {
            this.name = name
            this.number = number
            this.primaryFormat = format
            this.secondaryFormat = -1
        }

        internal constructor(name: String, number: Int, primaryFormat: Int, secondaryFormat: Int) {
            this.name = name
            this.number = number
            this.primaryFormat = primaryFormat
            this.secondaryFormat = secondaryFormat
        }

        fun isFormatCompatible(format: Int): Boolean {
            if (primaryFormat == IFD_FORMAT_UNDEFINED || format == IFD_FORMAT_UNDEFINED) {
                return true
            } else if (primaryFormat == format || secondaryFormat == format) {
                return true
            } else if ((primaryFormat == IFD_FORMAT_ULONG || secondaryFormat == IFD_FORMAT_ULONG)
                && format == IFD_FORMAT_USHORT
            ) {
                return true
            } else if ((primaryFormat == IFD_FORMAT_SLONG || secondaryFormat == IFD_FORMAT_SLONG)
                && format == IFD_FORMAT_SSHORT
            ) {
                return true
            } else if ((primaryFormat == IFD_FORMAT_DOUBLE || secondaryFormat == IFD_FORMAT_DOUBLE)
                && format == IFD_FORMAT_SINGLE
            ) {
                return true
            }
            return false
        }
    }

    // See JEITA CP-3451C Section 4.6.3: Exif-specific IFD.
    // The following values are used for indicating pointers to the other Image File Directories.
    // Indices of Exif Ifd tag groups
    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        *[IFD_TYPE_PRIMARY, IFD_TYPE_EXIF, IFD_TYPE_GPS, IFD_TYPE_INTEROPERABILITY, IFD_TYPE_THUMBNAIL, IFD_TYPE_PREVIEW, IFD_TYPE_ORF_MAKER_NOTE, IFD_TYPE_ORF_CAMERA_SETTINGS, IFD_TYPE_ORF_IMAGE_PROCESSING, IFD_TYPE_PEF]
    )
    annotation class IfdType

    private var mFilename: String? = null
    private var mSeekableFileDescriptor: FileDescriptor? = null
    private var mAssetInputStream: AssetInputStream? = null
    private var mMimeType = 0
    private var mIsExifDataOnly = false
    private val mAttributes: Array<HashMap<String, ExifAttribute?>> =
        Array(EXIF_TAGS.size) { HashMap<String, ExifAttribute?>() }
    private val mAttributesOffsets: MutableSet<Int> = HashSet(EXIF_TAGS.size)
    private var mExifByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
    private var mHasThumbnail = false
    private var mHasThumbnailStrips = false
    private var mAreThumbnailStripsConsecutive = false

    // Used to indicate the position of the thumbnail (doesn't include offset to EXIF data segment).
    private var mThumbnailOffset = 0
    private var mThumbnailLength = 0
    private var mThumbnailBytes: ByteArray? = null
    private var mThumbnailCompression = 0

    // Used to indicate offset from the start of the original input stream to EXIF data
    private var mOffsetToExifData = 0
    private var mOrfMakerNoteOffset = 0
    private var mOrfThumbnailOffset = 0
    private var mOrfThumbnailLength = 0
    private var mModified = false

    // XMP data can be contained as either part of the EXIF data (tag number 700), or as a
    // separate data marker (a separate MARKER_APP1).
    private var mXmpIsFromSeparateMarker = false

    /**
     * Reads Exif tags from the specified image file.
     *
     * @param file the file of the image data
     * @throws NullPointerException if file is null
     * @throws IOException          if an I/O error occurs while retrieving file descriptor via
     * [FileInputStream.getFD].
     */
    constructor(file: File) {
        if (file == null) {
            throw NullPointerException("file cannot be null")
        }
        initForFilename(file.absolutePath)
    }

    /**
     * Reads Exif tags from the specified image file.
     *
     * @param filename the name of the file of the image data
     * @throws NullPointerException if file name is null
     * @throws IOException          if an I/O error occurs while retrieving file descriptor via
     * [FileInputStream.getFD].
     */
    constructor(filename: String) {
        if (filename == null) {
            throw NullPointerException("filename cannot be null")
        }
        initForFilename(filename)
    }

    /**
     * Reads Exif tags from the specified image file descriptor. Attribute mutation is supported
     * for writable and seekable file descriptors only. This constructor will not rewind the offset
     * of the given file descriptor. Developers should close the file descriptor after use.
     *
     * @param fileDescriptor the file descriptor of the image data
     * @throws NullPointerException if file descriptor is null
     * @throws IOException          if an error occurs while duplicating the file descriptor.
     */
    constructor(fileDescriptor: FileDescriptor) {
        var fileDescriptor = fileDescriptor
            ?: throw NullPointerException("fileDescriptor cannot be null")
        mAssetInputStream = null
        mFilename = null

        var isFdDuped = false
        if (Build.VERSION.SDK_INT >= 21 && isSeekableFD(fileDescriptor)) {
            mSeekableFileDescriptor = fileDescriptor
            // Keep the original file descriptor in order to save attributes when it's seekable.
            // Otherwise, just close the given file descriptor after reading it because the save
            // feature won't be working.
            try {
                fileDescriptor = ExifInterfaceUtils.Api21Impl.dup(fileDescriptor)
                isFdDuped = true
            } catch (e: Exception) {
                throw IOException("Failed to duplicate file descriptor", e)
            }
        } else {
            mSeekableFileDescriptor = null
        }
        var `in`: FileInputStream? = null
        try {
            `in` = FileInputStream(fileDescriptor)
            loadAttributes(`in`)
        } finally {
            ExifInterfaceUtils.closeQuietly(`in`)
            if (isFdDuped) {
                ExifInterfaceUtils.closeFileDescriptor(fileDescriptor)
            }
        }
    }

    /**
     * Reads Exif tags from the specified image input stream based on the stream type. Attribute
     * mutation is not supported for input streams. The given input stream will proceed from its
     * current position. Developers should close the input stream after use. This constructor is not
     * intended to be used with an input stream that performs any networking operations.
     *
     * @param inputStream the input stream that contains the image data
     * @param streamType  the type of input stream
     * @throws NullPointerException if the input stream is null
     * @throws IOException          if an I/O error occurs while retrieving file descriptor via
     * [FileInputStream.getFD].
     */
    /**
     * Reads Exif tags from the specified image input stream. Attribute mutation is not supported
     * for input streams. The given input stream will proceed from its current position. Developers
     * should close the input stream after use. This constructor is not intended to be used with
     * an input stream that performs any networking operations.
     *
     * @param inputStream the input stream that contains the image data
     * @throws NullPointerException if the input stream is null
     */
    @JvmOverloads
    constructor(
        inputStream: InputStream,
        @ExifStreamType streamType: Int = STREAM_TYPE_FULL_IMAGE_DATA
    ) {
        var inputStream = inputStream
            ?: throw NullPointerException("inputStream cannot be null")
        mFilename = null

        val shouldBeExifDataOnly = (streamType == STREAM_TYPE_EXIF_DATA_ONLY)
        if (shouldBeExifDataOnly) {
            inputStream = BufferedInputStream(inputStream, IDENTIFIER_EXIF_APP1.size)
            if (!isExifDataOnly(inputStream)) {
                Log.w(TAG, "Given data does not follow the structure of an Exif-only data.")
                return
            }
            mIsExifDataOnly = true
            mAssetInputStream = null
            mSeekableFileDescriptor = null
        } else {
            if (inputStream is AssetInputStream) {
                mAssetInputStream = inputStream
                mSeekableFileDescriptor = null
            } else if (inputStream is FileInputStream
                && isSeekableFD(inputStream.fd)
            ) {
                mAssetInputStream = null
                mSeekableFileDescriptor = inputStream.fd
            } else {
                mAssetInputStream = null
                mSeekableFileDescriptor = null
            }
        }
        loadAttributes(inputStream)
    }

    /**
     * Returns the EXIF attribute of the specified tag or `null` if there is no such tag in
     * the image file.
     *
     * @param tag the name of the tag.
     */
    @Suppress("deprecation")
    private fun getExifAttribute(tag: String): ExifAttribute? {
        var tag = tag ?: throw NullPointerException("tag shouldn't be null")
        // Maintain compatibility.
        if (TAG_ISO_SPEED_RATINGS == tag) {
            if (DEBUG) {
                Log.d(
                    TAG, "getExifAttribute: Replacing TAG_ISO_SPEED_RATINGS with "
                            + "TAG_PHOTOGRAPHIC_SENSITIVITY."
                )
            }
            tag = TAG_PHOTOGRAPHIC_SENSITIVITY
        }
        // Retrieves all tag groups. The value from primary image tag group has a higher priority
        // than the value from the thumbnail tag group if there are more than one candidates.
        for (i in EXIF_TAGS.indices) {
            val value = mAttributes[i][tag]
            if (value != null) {
                return value
            }
        }
        return null
    }

    /**
     * Returns the value of the specified tag or `null` if there
     * is no such tag in the image file.
     *
     * @param tag the name of the tag.
     */
    fun getAttribute(tag: String): String? {
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        val attribute = getExifAttribute(tag)
        if (attribute != null) {
            if (!sTagSetForCompatibility.contains(tag)) {
                return attribute.getStringValue(mExifByteOrder)
            }
            if (tag == TAG_GPS_TIMESTAMP) {
                // Convert the rational values to the custom formats for backwards compatibility.
                if (attribute.format != IFD_FORMAT_URATIONAL
                    && attribute.format != IFD_FORMAT_SRATIONAL
                ) {
                    Log.w(TAG, "GPS Timestamp format is not rational. format=" + attribute.format)
                    return null
                }
                val array = attribute.getValue(mExifByteOrder) as Array<Rational>?
                if (array == null || array.size != 3) {
                    Log.w(TAG, "Invalid GPS Timestamp array. array=" + array.contentToString())
                    return null
                }
                return String.format(
                    "%02d:%02d:%02d",
                    (array[0].numerator.toFloat() / array[0].denominator).toInt(),
                    (array[1].numerator.toFloat() / array[1].denominator).toInt(),
                    (array[2].numerator.toFloat() / array[2].denominator).toInt()
                )
            }
            return try {
                attribute.getDoubleValue(mExifByteOrder).toString()
            } catch (e: NumberFormatException) {
                null
            }
        }
        return null
    }

    /**
     * Returns the integer value of the specified tag. If there is no such tag
     * in the image file or the value cannot be parsed as integer, return
     * <var>defaultValue</var>.
     *
     * @param tag          the name of the tag.
     * @param defaultValue the value to return if the tag is not available.
     */
    fun getAttributeInt(tag: String, defaultValue: Int): Int {
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        val exifAttribute = getExifAttribute(tag) ?: return defaultValue

        return try {
            exifAttribute.getIntValue(mExifByteOrder)
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * Returns the double value of the tag that is specified as rational or contains a
     * double-formatted value. If there is no such tag in the image file or the value cannot be
     * parsed as double, return <var>defaultValue</var>.
     *
     * @param tag          the name of the tag.
     * @param defaultValue the value to return if the tag is not available.
     */
    fun getAttributeDouble(tag: String, defaultValue: Double): Double {
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        val exifAttribute = getExifAttribute(tag) ?: return defaultValue

        return try {
            exifAttribute.getDoubleValue(mExifByteOrder)
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * Sets the value of the specified tag.
     *
     * @param tag   the name of the tag.
     * @param value the value of the tag.
     */
    @Suppress("deprecation")
    fun setAttribute(tag: String, value: String?) {
        var tag = tag
        var value = value
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        // Validate and convert if necessary.
        if (TAG_DATETIME == tag || TAG_DATETIME_ORIGINAL == tag
            || TAG_DATETIME_DIGITIZED == tag
        ) {
            if (value != null) {
                val isPrimaryFormat = DATETIME_PRIMARY_FORMAT_PATTERN.matcher(value).find()
                val isSecondaryFormat = DATETIME_SECONDARY_FORMAT_PATTERN.matcher(value).find()
                // Validate
                if (value.length != DATETIME_VALUE_STRING_LENGTH
                    || (!isPrimaryFormat && !isSecondaryFormat)
                ) {
                    Log.w(
                        TAG,
                        "Invalid value for $tag : $value"
                    )
                    return
                }
                // If datetime value has secondary format (e.g. 2020-01-01 00:00:00), convert it to
                // primary format (e.g. 2020:01:01 00:00:00) since it is the format in the
                // official documentation.
                // See JEITA CP-3451C Section 4.6.4. D. Other Tags, DateTime
                if (isSecondaryFormat) {
                    // Replace "-" with ":" to match the primary format.
                    value = value.replace("-".toRegex(), ":")
                }
            }
        }
        // Maintain compatibility.
        if (TAG_ISO_SPEED_RATINGS == tag) {
            if (DEBUG) {
                Log.d(
                    TAG, "setAttribute: Replacing TAG_ISO_SPEED_RATINGS with "
                            + "TAG_PHOTOGRAPHIC_SENSITIVITY."
                )
            }
            tag = TAG_PHOTOGRAPHIC_SENSITIVITY
        }
        // Convert the given value to rational values for backwards compatibility.
        if (value != null && sTagSetForCompatibility.contains(tag)) {
            if (tag == TAG_GPS_TIMESTAMP) {
                val m = GPS_TIMESTAMP_PATTERN.matcher(value)
                if (!m.find()) {
                    Log.w(
                        TAG,
                        "Invalid value for $tag : $value"
                    )
                    return
                }
                value = (m.group(1).toInt().toString() + "/1," + m.group(2).toInt() + "/1,"
                        + m.group(3).toInt() + "/1")
            } else {
                try {
                    val doubleValue = value.toDouble()
                    value = Rational(doubleValue).toString()
                } catch (e: NumberFormatException) {
                    Log.w(
                        TAG,
                        "Invalid value for $tag : $value"
                    )
                    return
                }
            }
        }

        for (i in EXIF_TAGS.indices) {
            if (i == IFD_TYPE_THUMBNAIL && !mHasThumbnail) {
                continue
            }
            val exifTag = sExifTagMapsForWriting[i][tag]
            if (exifTag != null) {
                if (value == null) {
                    mAttributes[i].remove(tag)
                    continue
                }
                val guess = guessDataFormat(value)
                val dataFormat =
                    if (exifTag.primaryFormat == guess.first || exifTag.primaryFormat == guess.second) {
                        exifTag.primaryFormat
                    } else if (exifTag.secondaryFormat != -1 && (exifTag.secondaryFormat == guess.first
                                || exifTag.secondaryFormat == guess.second)
                    ) {
                        exifTag.secondaryFormat
                    } else if (exifTag.primaryFormat == IFD_FORMAT_BYTE || exifTag.primaryFormat == IFD_FORMAT_UNDEFINED || exifTag.primaryFormat == IFD_FORMAT_STRING) {
                        exifTag.primaryFormat
                    } else {
                        if (DEBUG) {
                            Log.d(
                                TAG,
                                ("Given tag (" + tag
                                        + ") value didn't match with one of expected "
                                        + "formats: " + IFD_FORMAT_NAMES[exifTag.primaryFormat]
                                        + (if (exifTag.secondaryFormat == -1) "" else ", "
                                        + IFD_FORMAT_NAMES[exifTag.secondaryFormat]) + " (guess: "
                                        + IFD_FORMAT_NAMES[guess.first] + (if (guess.second == -1) "" else ", "
                                        + IFD_FORMAT_NAMES[guess.second]) + ")")
                            )
                        }
                        continue
                    }
                when (dataFormat) {
                    IFD_FORMAT_BYTE -> {
                        mAttributes[i][tag] =
                            ExifAttribute.createByte(
                                value
                            )
                    }

                    IFD_FORMAT_UNDEFINED, IFD_FORMAT_STRING -> {
                        mAttributes[i][tag] =
                            ExifAttribute.createString(
                                value
                            )
                    }

                    IFD_FORMAT_USHORT -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val intArray = IntArray(values.size)
                        var j = 0
                        while (j < values.size) {
                            intArray[j] = values[j].toInt()
                            ++j
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.createUShort(
                                intArray,
                                mExifByteOrder
                            )
                    }

                    IFD_FORMAT_SLONG -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val intArray = IntArray(values.size)
                        var j = 0
                        while (j < values.size) {
                            intArray[j] = values[j].toInt()
                            ++j
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.createSLong(
                                intArray,
                                mExifByteOrder
                            )
                    }

                    IFD_FORMAT_ULONG -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val longArray = LongArray(values.size)
                        var j = 0
                        while (j < values.size) {
                            longArray[j] = values[j].toLong()
                            ++j
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.createULong(
                                longArray,
                                mExifByteOrder
                            )
                    }

                    IFD_FORMAT_URATIONAL -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val rationalArray = Array(values.size) { j ->
                            val numbers = values[j].split("/".toRegex()).toTypedArray()
                            Rational(
                                numbers[0].toDouble().toLong(),
                                numbers[1].toDouble().toLong()
                            )
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.Companion.createURational(
                                rationalArray,
                                mExifByteOrder
                            )
                    }

                    IFD_FORMAT_SRATIONAL -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val rationalArray = Array(values.size) { j ->
                            val numbers = values[j].split("/".toRegex()).toTypedArray()
                            Rational(
                                numbers[0].toDouble().toLong(),
                                numbers[1].toDouble().toLong()
                            )
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.Companion.createSRational(
                                rationalArray,
                                mExifByteOrder
                            )
                    }

                    IFD_FORMAT_DOUBLE -> {
                        val values = value.split(",".toRegex()).toTypedArray()
                        val doubleArray = DoubleArray(values.size)
                        var j = 0
                        while (j < values.size) {
                            doubleArray[j] = values[j].toDouble()
                            ++j
                        }
                        mAttributes[i][tag] =
                            ExifAttribute.createDouble(
                                doubleArray,
                                mExifByteOrder
                            )
                    }

                    else -> {
                        if (DEBUG) {
                            Log.d(
                                TAG,
                                "Data format isn't one of expected formats: $dataFormat"
                            )
                        }
                        continue
                    }
                }
            }
        }
    }

    /**
     * Resets the [.TAG_ORIENTATION] of the image to be [.ORIENTATION_NORMAL].
     */
    fun resetOrientation() {
        setAttribute(TAG_ORIENTATION, ORIENTATION_NORMAL.toString())
    }

    /**
     * Rotates the image by the given degree clockwise. The degree should be a multiple of
     * 90 (e.g, 90, 180, -90, etc.).
     *
     * @param degree The degree of rotation.
     */
    fun rotate(degree: Int) {
        require(degree % 90 == 0) { "degree should be a multiple of 90" }

        val currentOrientation = getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)
        val currentIndex: Int
        var newIndex: Int
        val resultOrientation: Int
        if (ROTATION_ORDER.contains(currentOrientation)) {
            currentIndex = ROTATION_ORDER.indexOf(currentOrientation)
            newIndex = (currentIndex + degree / 90) % 4
            newIndex += if (newIndex < 0) 4 else 0
            resultOrientation = ROTATION_ORDER[newIndex]
        } else if (FLIPPED_ROTATION_ORDER.contains(currentOrientation)) {
            currentIndex = FLIPPED_ROTATION_ORDER.indexOf(currentOrientation)
            newIndex = (currentIndex + degree / 90) % 4
            newIndex += if (newIndex < 0) 4 else 0
            resultOrientation = FLIPPED_ROTATION_ORDER[newIndex]
        } else {
            resultOrientation = ORIENTATION_UNDEFINED
        }

        setAttribute(TAG_ORIENTATION, resultOrientation.toString())
    }

    /**
     * Flips the image vertically.
     */
    fun flipVertically() {
        val currentOrientation = getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)
        val resultOrientation = when (currentOrientation) {
            ORIENTATION_FLIP_HORIZONTAL -> ORIENTATION_ROTATE_180
            ORIENTATION_ROTATE_180 -> ORIENTATION_FLIP_HORIZONTAL
            ORIENTATION_FLIP_VERTICAL -> ORIENTATION_NORMAL
            ORIENTATION_TRANSPOSE -> ORIENTATION_ROTATE_270
            ORIENTATION_ROTATE_90 -> ORIENTATION_TRANSVERSE
            ORIENTATION_TRANSVERSE -> ORIENTATION_ROTATE_90
            ORIENTATION_ROTATE_270 -> ORIENTATION_TRANSPOSE
            ORIENTATION_NORMAL -> ORIENTATION_FLIP_VERTICAL
            ORIENTATION_UNDEFINED -> ORIENTATION_UNDEFINED
            else -> ORIENTATION_UNDEFINED
        }
        setAttribute(TAG_ORIENTATION, resultOrientation.toString())
    }

    /**
     * Flips the image horizontally.
     */
    fun flipHorizontally() {
        val currentOrientation = getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)
        val resultOrientation = when (currentOrientation) {
            ORIENTATION_FLIP_HORIZONTAL -> ORIENTATION_NORMAL
            ORIENTATION_ROTATE_180 -> ORIENTATION_FLIP_VERTICAL
            ORIENTATION_FLIP_VERTICAL -> ORIENTATION_ROTATE_180
            ORIENTATION_TRANSPOSE -> ORIENTATION_ROTATE_90
            ORIENTATION_ROTATE_90 -> ORIENTATION_TRANSPOSE
            ORIENTATION_TRANSVERSE -> ORIENTATION_ROTATE_270
            ORIENTATION_ROTATE_270 -> ORIENTATION_TRANSVERSE
            ORIENTATION_NORMAL -> ORIENTATION_FLIP_HORIZONTAL
            ORIENTATION_UNDEFINED -> ORIENTATION_UNDEFINED
            else -> ORIENTATION_UNDEFINED
        }
        setAttribute(TAG_ORIENTATION, resultOrientation.toString())
    }

    val isFlipped: Boolean
        /**
         * Returns if the current image orientation is flipped.
         *
         * @see .getRotationDegrees
         */
        get() {
            val orientation = getAttributeInt(
                TAG_ORIENTATION,
                ORIENTATION_NORMAL
            )
            return when (orientation) {
                ORIENTATION_FLIP_HORIZONTAL, ORIENTATION_TRANSVERSE, ORIENTATION_FLIP_VERTICAL, ORIENTATION_TRANSPOSE -> true
                else -> false
            }
        }

    val rotationDegrees: Int
        /**
         * Returns the rotation degrees for the current image orientation. If the image is flipped,
         * i.e., [.isFlipped] returns `true`, the rotation degrees will be base on
         * the assumption that the image is first flipped horizontally (along Y-axis), and then do
         * the rotation. For example, [.ORIENTATION_TRANSPOSE] will be interpreted as flipped
         * horizontally first, and then rotate 270 degrees clockwise.
         *
         * @return The rotation degrees of the image after the horizontal flipping is applied, if any.
         * @see .isFlipped
         */
        get() {
            val orientation = getAttributeInt(
                TAG_ORIENTATION,
                ORIENTATION_NORMAL
            )
            return when (orientation) {
                ORIENTATION_ROTATE_90, ORIENTATION_TRANSVERSE -> 90
                ORIENTATION_ROTATE_180, ORIENTATION_FLIP_VERTICAL -> 180
                ORIENTATION_ROTATE_270, ORIENTATION_TRANSPOSE -> 270
                ORIENTATION_UNDEFINED, ORIENTATION_NORMAL, ORIENTATION_FLIP_HORIZONTAL -> 0
                else -> 0
            }
        }

    /**
     * Remove any values of the specified tag.
     *
     * @param tag the name of the tag.
     */
    private fun removeAttribute(tag: String) {
        for (i in EXIF_TAGS.indices) {
            mAttributes[i].remove(tag)
        }
    }

    /**
     * This function decides which parser to read the image data according to the given input stream
     * type and the content of the input stream.
     */
    private fun loadAttributes(`in`: InputStream) {
        var `in` = `in` ?: throw NullPointerException("inputstream shouldn't be null")
        try {
            // Initialize mAttributes.
            for (i in EXIF_TAGS.indices) {
                mAttributes[i] = HashMap()
            }

            // Check file type
            if (!mIsExifDataOnly) {
                `in` = BufferedInputStream(`in`, SIGNATURE_CHECK_SIZE)
                mMimeType = getMimeType(`in`)
            }

            if (shouldSupportSeek(mMimeType)) {
                val inputStream =
                    SeekableByteOrderedDataInputStream(`in`)
                if (mIsExifDataOnly) {
                    getStandaloneAttributes(inputStream)
                } else {
                    if (mMimeType == IMAGE_TYPE_HEIF) {
                        getHeifAttributes(inputStream)
                    } else if (mMimeType == IMAGE_TYPE_ORF) {
                        getOrfAttributes(inputStream)
                    } else if (mMimeType == IMAGE_TYPE_RW2) {
                        getRw2Attributes(inputStream)
                    } else {
                        getRawAttributes(inputStream)
                    }
                }
                // Set thumbnail image offset and length
                inputStream.seek(mOffsetToExifData.toLong())
                setThumbnailData(inputStream)
            } else {
                val inputStream = ByteOrderedDataInputStream(`in`)
                if (mMimeType == IMAGE_TYPE_JPEG) {
                    getJpegAttributes(
                        inputStream,  /* offsetToJpeg= */0,
                        IFD_TYPE_PRIMARY
                    )
                } else if (mMimeType == IMAGE_TYPE_PNG) {
                    getPngAttributes(inputStream)
                } else if (mMimeType == IMAGE_TYPE_RAF) {
                    getRafAttributes(inputStream)
                } else if (mMimeType == IMAGE_TYPE_WEBP) {
                    getWebpAttributes(inputStream)
                }
            }
        } catch (e: IOException) {
            // Ignore exceptions in order to keep the compatibility with the old versions of
            // ExifInterface.
            if (DEBUG) {
                Log.w(
                    TAG, ("Invalid image: ExifInterface got an unsupported image format file"
                            + "(ExifInterface supports JPEG and some RAW image formats only) "
                            + "or a corrupted JPEG file to ExifInterface."), e
                )
            }
        } catch (e: UnsupportedOperationException) {
            if (DEBUG) {
                Log.w(
                    TAG, ("Invalid image: ExifInterface got an unsupported image format file"
                            + "(ExifInterface supports JPEG and some RAW image formats only) "
                            + "or a corrupted JPEG file to ExifInterface."), e
                )
            }
        } finally {
            addDefaultValuesForCompatibility()

            if (DEBUG) {
                printAttributes()
            }
        }
    }

    // Prints out attributes for debugging.
    private fun printAttributes() {
        for (i in mAttributes.indices) {
            Log.d(TAG, "The size of tag group[" + i + "]: " + mAttributes[i].size)
            for ((key, tagValue) in mAttributes[i]) {
                Log.d(
                    TAG, ("tagName: " + key + ", tagType: " + tagValue.toString()
                            + ", tagValue: '" + tagValue!!.getStringValue(mExifByteOrder) + "'")
                )
            }
        }
    }

    /**
     * Save the tag data into the original image file. This is expensive because it involves
     * copying all the data from one file to another and deleting the old file and renaming the
     * other. It's best to use [.setAttribute] to set all attributes to write
     * and make a single call rather than multiple calls for each attribute.
     *
     *
     * This method is supported for JPEG, PNG, and WebP formats.
     *
     *
     * Note: after calling this method, any attempts to obtain range information
     * from [.getAttributeRange] or [.getThumbnailRange]
     * will throw [IllegalStateException], since the offsets may have
     * changed in the newly written file.
     *
     *
     * For WebP format, the Exif data will be stored as an Extended File Format, and it may not be
     * supported for older readers.
     *
     */
    @Throws(IOException::class)
    fun saveAttributes() {
        if (!isSupportedFormatForSavingAttributes(mMimeType)) {
            throw IOException(
                "ExifInterface only supports saving attributes for JPEG, PNG, "
                        + "and WebP formats."
            )
        }
        if (mSeekableFileDescriptor == null && mFilename == null) {
            throw IOException(
                "ExifInterface does not support saving attributes for the current input."
            )
        }
        if (mHasThumbnail && mHasThumbnailStrips && !mAreThumbnailStripsConsecutive) {
            throw IOException(
                "ExifInterface does not support saving attributes when the image "
                        + "file has non-consecutive thumbnail strips"
            )
        }

        // Remember the fact that we've changed the file on disk from what was
        // originally parsed, meaning we can't answer range questions
        mModified = true

        // Keep the thumbnail in memory
        mThumbnailBytes = thumbnail

        var `in`: FileInputStream? = null
        var out: FileOutputStream? = null
        var tempFile: File? = null
        try {
            // Copy the original file to temporary file.
            tempFile = File.createTempFile("temp", "tmp")
            if (mFilename != null) {
                `in` = FileInputStream(mFilename)
            } else {
                // mSeekableFileDescriptor will be non-null only for SDK_INT >= 21, but this check
                // is needed to prevent calling Os.lseek at runtime for SDK < 21.
                if (Build.VERSION.SDK_INT >= 21) {
                    ExifInterfaceUtils.Api21Impl.lseek(
                        mSeekableFileDescriptor,
                        0,
                        OsConstants.SEEK_SET
                    )
                    `in` = FileInputStream(mSeekableFileDescriptor)
                }
            }
            out = FileOutputStream(tempFile)
            ExifInterfaceUtils.copy(`in`!!, out)
        } catch (e: Exception) {
            throw IOException("Failed to copy original file to temp file", e)
        } finally {
            ExifInterfaceUtils.closeQuietly(`in`)
            ExifInterfaceUtils.closeQuietly(out)
        }

        `in` = null
        out = null
        var bufferedIn: BufferedInputStream? = null
        var bufferedOut: BufferedOutputStream? = null
        var shouldKeepTempFile = false
        try {
            // Save the new file.
            `in` = FileInputStream(tempFile)
            if (mFilename != null) {
                out = FileOutputStream(mFilename)
            } else {
                // mSeekableFileDescriptor will be non-null only for SDK_INT >= 21, but this check
                // is needed to prevent calling Os.lseek at runtime for SDK < 21.
                if (Build.VERSION.SDK_INT >= 21) {
                    ExifInterfaceUtils.Api21Impl.lseek(
                        mSeekableFileDescriptor,
                        0,
                        OsConstants.SEEK_SET
                    )
                    out = FileOutputStream(mSeekableFileDescriptor)
                }
            }
            bufferedIn = BufferedInputStream(`in`)
            bufferedOut = BufferedOutputStream(out)
            if (mMimeType == IMAGE_TYPE_JPEG) {
                saveJpegAttributes(bufferedIn, bufferedOut)
            } else if (mMimeType == IMAGE_TYPE_PNG) {
                savePngAttributes(bufferedIn, bufferedOut)
            } else if (mMimeType == IMAGE_TYPE_WEBP) {
                saveWebpAttributes(bufferedIn, bufferedOut)
            }
        } catch (e: Exception) {
            try {
                // Restore original file
                `in` = FileInputStream(tempFile)
                if (mFilename != null) {
                    out = FileOutputStream(mFilename)
                } else {
                    // mSeekableFileDescriptor will be non-null only for SDK_INT >= 21, but this
                    // check is needed to prevent calling Os.lseek at runtime for SDK < 21.
                    if (Build.VERSION.SDK_INT >= 21) {
                        ExifInterfaceUtils.Api21Impl.lseek(
                            mSeekableFileDescriptor,
                            0,
                            OsConstants.SEEK_SET
                        )
                        out = FileOutputStream(mSeekableFileDescriptor)
                    }
                }
                ExifInterfaceUtils.copy(`in`, out!!)
            } catch (exception: Exception) {
                shouldKeepTempFile = true
                throw IOException(
                    "Failed to save new file. Original file is stored in "
                            + tempFile!!.absolutePath, exception
                )
            } finally {
                ExifInterfaceUtils.closeQuietly(`in`)
                ExifInterfaceUtils.closeQuietly(out)
            }
            throw IOException("Failed to save new file", e)
        } finally {
            ExifInterfaceUtils.closeQuietly(bufferedIn)
            ExifInterfaceUtils.closeQuietly(bufferedOut)
            if (!shouldKeepTempFile) {
                tempFile!!.delete()
            }
        }

        // Discard the thumbnail in memory
        mThumbnailBytes = null
    }

    /**
     * Returns true if the image file has a thumbnail.
     */
    fun hasThumbnail(): Boolean {
        return mHasThumbnail
    }

    /**
     * Returns true if the image file has the given attribute defined.
     *
     * @param tag the name of the tag.
     */
    fun hasAttribute(tag: String): Boolean {
        return getExifAttribute(tag) != null
    }

    val thumbnail: ByteArray?
        /**
         * Returns the JPEG compressed thumbnail inside the image file, or `null` if there is no
         * JPEG compressed thumbnail.
         * The returned data can be decoded using
         * [BitmapFactory.decodeByteArray]
         */
        get() {
            if (mThumbnailCompression == DATA_JPEG || mThumbnailCompression == DATA_JPEG_COMPRESSED) {
                return thumbnailBytes
            }
            return null
        }

    val thumbnailBytes: ByteArray?
        /**
         * Returns the thumbnail bytes inside the image file, regardless of the compression type of the
         * thumbnail image.
         */
        get() {
            if (!mHasThumbnail) {
                return null
            }
            if (mThumbnailBytes != null) {
                return mThumbnailBytes
            }

            // Read the thumbnail.
            var `in`: InputStream? = null
            var newFileDescriptor: FileDescriptor? = null
            try {
                if (mAssetInputStream != null) {
                    `in` = mAssetInputStream
                    if (`in`!!.markSupported()) {
                        `in`.reset()
                    } else {
                        Log.d(
                            TAG,
                            "Cannot read thumbnail from inputstream without mark/reset support"
                        )
                        return null
                    }
                } else if (mFilename != null) {
                    `in` = FileInputStream(mFilename)
                } else {
                    // mSeekableFileDescriptor will be non-null only for SDK_INT >= 21, but this check
                    // is needed to prevent calling Os.lseek and Os.dup at runtime for SDK < 21.
                    if (Build.VERSION.SDK_INT >= 21) {
                        newFileDescriptor =
                            ExifInterfaceUtils.Api21Impl.dup(
                                mSeekableFileDescriptor
                            )
                        ExifInterfaceUtils.Api21Impl.lseek(
                            newFileDescriptor,
                            0,
                            OsConstants.SEEK_SET
                        )
                        `in` = FileInputStream(newFileDescriptor)
                    }
                }
                if (`in` == null) {
                    // Should not be reached this.
                    throw FileNotFoundException()
                }

                val inputStream =
                    ByteOrderedDataInputStream(`in`)
                inputStream.skipFully(mThumbnailOffset + mOffsetToExifData)
                // TODO: Need to handle potential OutOfMemoryError
                val buffer = ByteArray(mThumbnailLength)
                inputStream.readFully(buffer)
                mThumbnailBytes = buffer
                return buffer
            } catch (e: Exception) {
                // Couldn't get a thumbnail image.
                Log.d(
                    TAG,
                    "Encountered exception while getting thumbnail",
                    e
                )
            } finally {
                ExifInterfaceUtils.closeQuietly(`in`)
                if (newFileDescriptor != null) {
                    ExifInterfaceUtils.closeFileDescriptor(
                        newFileDescriptor
                    )
                }
            }
            return null
        }

    val thumbnailBitmap: Bitmap?
        /**
         * Creates and returns a Bitmap object of the thumbnail image based on the byte array and the
         * thumbnail compression value, or `null` if the compression type is unsupported.
         */
        get() {
            if (!mHasThumbnail) {
                return null
            } else if (mThumbnailBytes == null) {
                mThumbnailBytes = thumbnailBytes
            }

            if (mThumbnailCompression == DATA_JPEG || mThumbnailCompression == DATA_JPEG_COMPRESSED) {
                return BitmapFactory.decodeByteArray(mThumbnailBytes, 0, mThumbnailLength)
            } else if (mThumbnailCompression == DATA_UNCOMPRESSED) {
                val rgbValues = IntArray(mThumbnailBytes!!.size / 3)
                val alpha = (-0x1000000.toByte()).toByte()
                for (i in rgbValues.indices) {
                    rgbValues[i] = (alpha + (mThumbnailBytes!![3 * i].toInt() shl 16)
                            + (mThumbnailBytes!![3 * i + 1].toInt() shl 8) + mThumbnailBytes!![3 * i + 2])
                }

                val imageLengthAttribute =
                    mAttributes[IFD_TYPE_THUMBNAIL][TAG_THUMBNAIL_IMAGE_LENGTH]
                val imageWidthAttribute =
                    mAttributes[IFD_TYPE_THUMBNAIL][TAG_THUMBNAIL_IMAGE_WIDTH]
                if (imageLengthAttribute != null && imageWidthAttribute != null) {
                    val imageLength = imageLengthAttribute.getIntValue(mExifByteOrder)
                    val imageWidth = imageWidthAttribute.getIntValue(mExifByteOrder)
                    return Bitmap.createBitmap(
                        rgbValues,
                        imageWidth,
                        imageLength,
                        Bitmap.Config.ARGB_8888
                    )
                }
            }
            return null
        }

    val isThumbnailCompressed: Boolean
        /**
         * Returns true if thumbnail image is JPEG Compressed, or false if either thumbnail image does
         * not exist or thumbnail image is uncompressed.
         */
        get() {
            if (!mHasThumbnail) {
                return false
            }
            if (mThumbnailCompression == DATA_JPEG || mThumbnailCompression == DATA_JPEG_COMPRESSED) {
                return true
            }
            return false
        }

    val thumbnailRange: LongArray?
        /**
         * Returns the offset and length of thumbnail inside the image file, or
         * `null` if either there is no thumbnail or the thumbnail bytes are stored
         * non-consecutively.
         *
         * @return two-element array, the offset in the first value, and length in
         * the second, or `null` if no thumbnail was found or the thumbnail strips are
         * not placed consecutively.
         * @throws IllegalStateException if [.saveAttributes] has been
         * called since the underlying file was initially parsed, since
         * that means offsets may have changed.
         */
        get() {
            check(!mModified) { "The underlying file has been modified since being parsed" }

            if (mHasThumbnail) {
                if (mHasThumbnailStrips && !mAreThumbnailStripsConsecutive) {
                    return null
                }
                return longArrayOf(
                    (mThumbnailOffset + mOffsetToExifData).toLong(),
                    mThumbnailLength.toLong()
                )
            }
            return null
        }

    /**
     * Returns the offset and length of the requested tag inside the image file,
     * or `null` if the tag is not contained.
     *
     * @return two-element array, the offset in the first value, and length in
     * the second, or `null` if no tag was found.
     * @throws IllegalStateException if [.saveAttributes] has been
     * called since the underlying file was initially parsed, since
     * that means offsets may have changed.
     */
    fun getAttributeRange(tag: String): LongArray? {
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        check(!mModified) { "The underlying file has been modified since being parsed" }

        val attribute = getExifAttribute(tag)
        return if (attribute != null) {
            longArrayOf(attribute.bytesOffset, attribute.bytes.size.toLong())
        } else {
            null
        }
    }

    /**
     * Returns the raw bytes for the value of the requested tag inside the image
     * file, or `null` if the tag is not contained.
     *
     * @return raw bytes for the value of the requested tag, or `null` if
     * no tag was found.
     */
    fun getAttributeBytes(tag: String): ByteArray? {
        if (tag == null) {
            throw NullPointerException("tag shouldn't be null")
        }
        val attribute = getExifAttribute(tag)
        return attribute?.bytes
    }

    /**
     * Stores the latitude and longitude value in a float array. The first element is the latitude,
     * and the second element is the longitude. Returns false if the Exif tags are not available.
     *
     */
    @Deprecated("Use {@link #getLatLong()} instead.")
    fun getLatLong(output: FloatArray): Boolean {
        val latLong = latLong ?: return false

        output[0] = latLong[0].toFloat()
        output[1] = latLong[1].toFloat()
        return true
    }

    val latLong: DoubleArray?
        /**
         * Gets the latitude and longitude values.
         *
         *
         * If there are valid latitude and longitude values in the image, this method returns a double
         * array where the first element is the latitude and the second element is the longitude.
         * Otherwise, it returns null.
         */
        get() {
            val latValue =
                getAttribute(TAG_GPS_LATITUDE)
            val latRef =
                getAttribute(TAG_GPS_LATITUDE_REF)
            val lngValue =
                getAttribute(TAG_GPS_LONGITUDE)
            val lngRef =
                getAttribute(TAG_GPS_LONGITUDE_REF)

            if (latValue != null && latRef != null && lngValue != null && lngRef != null) {
                try {
                    val latitude =
                        convertRationalLatLonToDouble(
                            latValue,
                            latRef
                        )
                    val longitude =
                        convertRationalLatLonToDouble(
                            lngValue,
                            lngRef
                        )
                    return doubleArrayOf(latitude, longitude)
                } catch (e: IllegalArgumentException) {
                    Log.w(
                        TAG,
                        "Latitude/longitude values are not parsable. "
                                + String.format(
                            "latValue=%s, latRef=%s, lngValue=%s, lngRef=%s",
                            latValue, latRef, lngValue, lngRef
                        )
                    )
                }
            }
            return null
        }

    /**
     * Sets the GPS-related information. It will set GPS processing method, latitude and longitude
     * values, GPS timestamp, and speed information at the same time.
     *
     * @param location the [Location] object returned by GPS service.
     */
    fun setGpsInfo(location: Location?) {
        if (location == null) {
            return
        }
        setAttribute(TAG_GPS_PROCESSING_METHOD, location.provider)
        setLatLong(location.latitude, location.longitude)
        setAltitude(location.altitude)
        // Location objects store speeds in m/sec. Translates it to km/hr here.
        setAttribute(TAG_GPS_SPEED_REF, "K")
        setAttribute(
            TAG_GPS_SPEED, Rational(
                (location.speed
                        * TimeUnit.HOURS.toSeconds(1) / 1000).toDouble()
            ).toString()
        )
        val dateTime = sFormatterPrimary.format(
            Date(location.time)
        ).split("\\s+".toRegex()).toTypedArray()
        setAttribute(TAG_GPS_DATESTAMP, dateTime[0])
        setAttribute(TAG_GPS_TIMESTAMP, dateTime[1])
    }

    /**
     * Sets the latitude and longitude values.
     *
     * @param latitude  the decimal value of latitude. Must be a valid double value between -90.0 and
     * 90.0.
     * @param longitude the decimal value of longitude. Must be a valid double value between -180.0
     * and 180.0.
     * @throws IllegalArgumentException If `latitude` or `longitude` is outside the
     * specified range.
     */
    fun setLatLong(latitude: Double, longitude: Double) {
        require(!(latitude < -90.0 || latitude > 90.0 || java.lang.Double.isNaN(latitude))) { "Latitude value $latitude is not valid." }
        require(!(longitude < -180.0 || longitude > 180.0 || java.lang.Double.isNaN(longitude))) { "Longitude value $longitude is not valid." }
        setAttribute(TAG_GPS_LATITUDE_REF, if (latitude >= 0) "N" else "S")
        setAttribute(TAG_GPS_LATITUDE, convertDecimalDegree(abs(latitude)))
        setAttribute(TAG_GPS_LONGITUDE_REF, if (longitude >= 0) "E" else "W")
        setAttribute(TAG_GPS_LONGITUDE, convertDecimalDegree(abs(longitude)))
    }

    /**
     * Return the altitude in meters. If the exif tag does not exist, return
     * <var>defaultValue</var>.
     *
     * @param defaultValue the value to return if the tag is not available.
     */
    fun getAltitude(defaultValue: Double): Double {
        val altitude = getAttributeDouble(TAG_GPS_ALTITUDE, -1.0)
        val ref = getAttributeInt(TAG_GPS_ALTITUDE_REF, -1)

        return if (altitude >= 0 && ref >= 0) {
            (altitude * (if (ref == 1) -1 else 1))
        } else {
            defaultValue
        }
    }

    /**
     * Sets the altitude in meters.
     */
    fun setAltitude(altitude: Double) {
        val ref = if (altitude >= 0) "0" else "1"
        setAttribute(TAG_GPS_ALTITUDE, Rational(abs(altitude)).toString())
        setAttribute(TAG_GPS_ALTITUDE_REF, ref)
    }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY)
    var dateTime: Long?
        /**
         * Returns parsed [ExifInterface.TAG_DATETIME] value as number of milliseconds since
         * Jan. 1, 1970, midnight local time.
         *
         *
         * Note: The return value includes the first three digits (or less depending on the length
         * of the string) of [ExifInterface.TAG_SUBSEC_TIME].
         *
         * @return null if date time information is unavailable or invalid.
         * @hide
         */
        get() = parseDateTime(
            getAttribute(TAG_DATETIME),
            getAttribute(TAG_SUBSEC_TIME),
            getAttribute(TAG_OFFSET_TIME)
        )
        /**
         * Set the date time value.
         *
         * @param timeStamp number of milliseconds since Jan. 1, 1970, midnight local time.
         * @hide
         */
        set(timeStamp) {
            if (timeStamp == null) {
                throw NullPointerException("Timestamp should not be null.")
            }

            require(timeStamp >= 0) { "Timestamp should a positive value." }

            val subsec = timeStamp % 1000
            var subsecString = subsec.toString()
            for (i in subsecString.length..2) {
                subsecString = "0$subsecString"
            }
            setAttribute(
                TAG_DATETIME,
                sFormatterPrimary.format(
                    Date(timeStamp)
                )
            )
            setAttribute(
                TAG_SUBSEC_TIME,
                subsecString
            )
        }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    val dateTimeDigitized: Long?
        /**
         * Returns parsed [ExifInterface.TAG_DATETIME_DIGITIZED] value as number of
         * milliseconds since Jan. 1, 1970, midnight local time.
         *
         *
         * Note: The return value includes the first three digits (or less depending on the length
         * of the string) of [ExifInterface.TAG_SUBSEC_TIME_DIGITIZED].
         *
         * @return null if digitized date time information is unavailable or invalid.
         * @hide
         */
        get() = parseDateTime(
            getAttribute(TAG_DATETIME_DIGITIZED),
            getAttribute(TAG_SUBSEC_TIME_DIGITIZED),
            getAttribute(TAG_OFFSET_TIME_DIGITIZED)
        )

    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    val dateTimeOriginal: Long?
        /**
         * Returns parsed [ExifInterface.TAG_DATETIME_ORIGINAL] value as number of
         * milliseconds since Jan. 1, 1970, midnight local time.
         *
         *
         * Note: The return value includes the first three digits (or less depending on the length
         * of the string) of [ExifInterface.TAG_SUBSEC_TIME_ORIGINAL].
         *
         * @return null if original date time information is unavailable or invalid.
         * @hide
         */
        get() = parseDateTime(
            getAttribute(TAG_DATETIME_ORIGINAL),
            getAttribute(TAG_SUBSEC_TIME_ORIGINAL),
            getAttribute(TAG_OFFSET_TIME_ORIGINAL)
        )

    @get:SuppressLint("AutoBoxing")
    val gpsDateTime: Long?
        /**
         * Returns number of milliseconds since Jan. 1, 1970, midnight UTC.
         *
         * @return null if the date time information is not available.
         */
        get() {
            val date =
                getAttribute(TAG_GPS_DATESTAMP)
            val time =
                getAttribute(TAG_GPS_TIMESTAMP)
            if (date == null || time == null || (!NON_ZERO_TIME_PATTERN.matcher(
                    date
                ).matches()
                        && !NON_ZERO_TIME_PATTERN.matcher(
                    time
                ).matches())
            ) {
                return null
            }

            val dateTimeString = "$date $time"

            val pos = ParsePosition(0)
            try {
                var dateTime =
                    sFormatterPrimary.parse(
                        dateTimeString,
                        pos
                    )
                if (dateTime == null) {
                    dateTime =
                        sFormatterSecondary.parse(
                            dateTimeString,
                            pos
                        )
                    if (dateTime == null) {
                        return null
                    }
                }
                return dateTime.time
            } catch (e: IllegalArgumentException) {
                return null
            }
        }

    @Throws(IOException::class)
    private fun initForFilename(filename: String) {
        if (filename == null) {
            throw NullPointerException("filename cannot be null")
        }
        var `in`: FileInputStream? = null
        mAssetInputStream = null
        mFilename = filename
        try {
            `in` = FileInputStream(filename)
            mSeekableFileDescriptor =
                if (isSeekableFD(`in`.fd)) {
                    `in`.fd
                } else {
                    null
                }
            loadAttributes(`in`)
        } finally {
            ExifInterfaceUtils.closeQuietly(`in`)
        }
    }

    private fun convertDecimalDegree(decimalDegree: Double): String {
        val degrees = decimalDegree.toLong()
        val minutes = ((decimalDegree - degrees) * 60.0).toLong()
        val seconds = Math.round((decimalDegree - degrees - minutes / 60.0) * 3600.0 * 1e7)
        return "$degrees/1,$minutes/1,$seconds/10000000"
    }

    // Checks the type of image file
    @Throws(IOException::class)
    private fun getMimeType(`in`: BufferedInputStream): Int {
        `in`.mark(SIGNATURE_CHECK_SIZE)
        val signatureCheckBytes = ByteArray(SIGNATURE_CHECK_SIZE)
        `in`.read(signatureCheckBytes)
        `in`.reset()
        if (isJpegFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_JPEG
        } else if (isRafFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_RAF
        } else if (isHeifFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_HEIF
        } else if (isOrfFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_ORF
        } else if (isRw2Format(signatureCheckBytes)) {
            return IMAGE_TYPE_RW2
        } else if (isPngFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_PNG
        } else if (isWebpFormat(signatureCheckBytes)) {
            return IMAGE_TYPE_WEBP
        }
        // Certain file formats (PEF) are identified in readImageFileDirectory()
        return IMAGE_TYPE_UNKNOWN
    }

    /**
     * This method looks at the first 15 bytes to determine if this file is a RAF file.
     * There is no official specification for RAF files from Fuji, but there is an online archive of
     * image file specifications:
     * http://fileformats.archiveteam.org/wiki/Fujifilm_RAF
     */
    @Throws(IOException::class)
    private fun isRafFormat(signatureCheckBytes: ByteArray): Boolean {
        val rafSignatureBytes = RAF_SIGNATURE.toByteArray(Charset.defaultCharset())
        for (i in rafSignatureBytes.indices) {
            if (signatureCheckBytes[i] != rafSignatureBytes[i]) {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun isHeifFormat(signatureCheckBytes: ByteArray): Boolean {
        var signatureInputStream: ByteOrderedDataInputStream? = null
        try {
            signatureInputStream = ByteOrderedDataInputStream(signatureCheckBytes)

            var chunkSize = signatureInputStream.readInt().toLong()
            val chunkType = ByteArray(4)
            signatureInputStream.readFully(chunkType)

            if (!chunkType.contentEquals(HEIF_TYPE_FTYP)) {
                return false
            }

            var chunkDataOffset: Long = 8
            if (chunkSize == 1L) {
                // This indicates that the next 8 bytes represent the chunk size,
                // and chunk data comes after that.
                chunkSize = signatureInputStream.readLong()
                if (chunkSize < 16) {
                    // The smallest valid chunk is 16 bytes long in this case.
                    return false
                }
                chunkDataOffset += 8
            }

            // only sniff up to signatureCheckBytes.length
            if (chunkSize > signatureCheckBytes.size) {
                chunkSize = signatureCheckBytes.size.toLong()
            }

            val chunkDataSize = chunkSize - chunkDataOffset

            // It should at least have major brand (4-byte) and minor version (4-byte).
            // The rest of the chunk (if any) is a list of (4-byte) compatible brands.
            if (chunkDataSize < 8) {
                return false
            }

            val brand = ByteArray(4)
            var isMif1 = false
            var isHeic = false
            for (i in 0..<chunkDataSize / 4) {
                try {
                    signatureInputStream.readFully(brand)
                } catch (e: EOFException) {
                    return false
                }
                if (i == 1L) {
                    // Skip this index, it refers to the minorVersion, not a brand.
                    continue
                }
                if (brand.contentEquals(HEIF_BRAND_MIF1)) {
                    isMif1 = true
                } else if (brand.contentEquals(HEIF_BRAND_HEIC)) {
                    isHeic = true
                }
                if (isMif1 && isHeic) {
                    return true
                }
            }
        } catch (e: Exception) {
            if (DEBUG) {
                Log.d(TAG, "Exception parsing HEIF file type box.", e)
            }
        } finally {
            if (signatureInputStream != null) {
                signatureInputStream.close()
                signatureInputStream = null
            }
        }
        return false
    }

    /**
     * ORF has a similar structure to TIFF but it contains a different signature at the TIFF Header.
     * This method looks at the 2 bytes following the Byte Order bytes to determine if this file is
     * an ORF file.
     * There is no official specification for ORF files from Olympus, but there is an online archive
     * of image file specifications:
     * http://fileformats.archiveteam.org/wiki/Olympus_ORF
     */
    @Throws(IOException::class)
    private fun isOrfFormat(signatureCheckBytes: ByteArray): Boolean {
        var signatureInputStream: ByteOrderedDataInputStream? = null

        try {
            signatureInputStream = ByteOrderedDataInputStream(signatureCheckBytes)

            // Read byte order
            mExifByteOrder = readByteOrder(signatureInputStream)
            // Set byte order
            signatureInputStream.setByteOrder(mExifByteOrder)

            val orfSignature = signatureInputStream.readShort()
            return orfSignature == ORF_SIGNATURE_1 || orfSignature == ORF_SIGNATURE_2
        } catch (e: Exception) {
            // Do nothing
        } finally {
            signatureInputStream?.close()
        }
        return false
    }

    /**
     * RW2 is TIFF-based, but stores 0x55 signature byte instead of 0x42 at the header
     * See http://lclevy.free.fr/raw/
     */
    @Throws(IOException::class)
    private fun isRw2Format(signatureCheckBytes: ByteArray): Boolean {
        var signatureInputStream: ByteOrderedDataInputStream? = null

        try {
            signatureInputStream = ByteOrderedDataInputStream(signatureCheckBytes)

            // Read byte order
            mExifByteOrder = readByteOrder(signatureInputStream)
            // Set byte order
            signatureInputStream.setByteOrder(mExifByteOrder)

            val signatureByte = signatureInputStream.readShort()
            return signatureByte == RW2_SIGNATURE
        } catch (e: Exception) {
            // Do nothing
        } finally {
            signatureInputStream?.close()
        }
        return false
    }

    /**
     * PNG's file signature is first 8 bytes.
     * See PNG (Portable Network Graphics) Specification, Version 1.2, 3.1. PNG file signature
     */
    @Throws(IOException::class)
    private fun isPngFormat(signatureCheckBytes: ByteArray): Boolean {
        for (i in PNG_SIGNATURE.indices) {
            if (signatureCheckBytes[i] != PNG_SIGNATURE[i]) {
                return false
            }
        }
        return true
    }

    /**
     * WebP's file signature is composed of 12 bytes:
     * 'RIFF' (4 bytes) + file length value (4 bytes) + 'WEBP' (4 bytes)
     * See https://developers.google.com/speed/webp/docs/riff_container, Section "WebP File Header"
     */
    @Throws(IOException::class)
    private fun isWebpFormat(signatureCheckBytes: ByteArray): Boolean {
        for (i in WEBP_SIGNATURE_1.indices) {
            if (signatureCheckBytes[i] != WEBP_SIGNATURE_1[i]) {
                return false
            }
        }
        for (i in WEBP_SIGNATURE_2.indices) {
            if (signatureCheckBytes[i + WEBP_SIGNATURE_1.size + WEBP_FILE_SIZE_BYTE_LENGTH]
                != WEBP_SIGNATURE_2[i]
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Loads EXIF attributes from a JPEG input stream.
     *
     * @param in           The input stream that starts with the JPEG data.
     * @param offsetToJpeg The offset to JPEG data for the original input stream.
     * @param imageType    The image type from which to retrieve metadata. Use IFD_TYPE_PRIMARY for
     * primary image, IFD_TYPE_PREVIEW for preview image, and
     * IFD_TYPE_THUMBNAIL for thumbnail image.
     * @throws IOException If the data contains invalid JPEG markers, offsets, or length values.
     */
    @Throws(IOException::class)
    private fun getJpegAttributes(
        `in`: ByteOrderedDataInputStream,
        offsetToJpeg: Int,
        imageType: Int
    ) {
        // See JPEG File Interchange Format Specification, "JFIF Specification"
        if (DEBUG) {
            Log.d(
                TAG,
                "getJpegAttributes starting with: $`in`"
            )
        }
        // JPEG uses Big Endian by default. See https://people.cs.umass.edu/~verts/cs32/endian.html
        `in`.setByteOrder(ByteOrder.BIG_ENDIAN)

        var bytesRead = 0

        var marker: Byte
        if ((`in`.readByte().also { marker = it }) != MARKER) {
            throw IOException("Invalid marker: " + Integer.toHexString(marker.toInt() and 0xff))
        }
        ++bytesRead
        if (`in`.readByte() != MARKER_SOI) {
            throw IOException("Invalid marker: " + Integer.toHexString(marker.toInt() and 0xff))
        }
        ++bytesRead
        while (true) {
            marker = `in`.readByte()
            if (marker != MARKER) {
                throw IOException("Invalid marker:" + Integer.toHexString(marker.toInt() and 0xff))
            }
            ++bytesRead
            marker = `in`.readByte()
            if (DEBUG) {
                Log.d(
                    TAG,
                    "Found JPEG segment indicator: " + Integer.toHexString(marker.toInt() and 0xff)
                )
            }
            ++bytesRead

            // EOI indicates the end of an image and in case of SOS, JPEG image stream starts and
            // the image data will terminate right after.
            if (marker == MARKER_EOI || marker == MARKER_SOS) {
                break
            }
            var length = `in`.readUnsignedShort() - 2
            bytesRead += 2
            if (DEBUG) {
                Log.d(
                    TAG,
                    ("JPEG segment: " + Integer.toHexString(marker.toInt() and 0xff) + " (length: "
                            + (length + 2) + ")")
                )
            }
            if (length < 0) {
                throw IOException("Invalid length")
            }
            when (marker) {
                MARKER_APP1 -> {
                    val start = bytesRead
                    val bytes = ByteArray(length)
                    `in`.readFully(bytes)
                    bytesRead += length
                    length = 0

                    if (ExifInterfaceUtils.startsWith(bytes, IDENTIFIER_EXIF_APP1)) {
                        val value = Arrays.copyOfRange(
                            bytes, IDENTIFIER_EXIF_APP1.size,
                            bytes.size
                        )
                        // Save offset to EXIF data for handling thumbnail and attribute offsets.
                        mOffsetToExifData = (offsetToJpeg
                                +  /* offset to EXIF from JPEG start */start
                                + IDENTIFIER_EXIF_APP1.size)
                        readExifSegment(value, imageType)

                        setThumbnailData(ByteOrderedDataInputStream(value))
                    } else if (ExifInterfaceUtils.startsWith(bytes, IDENTIFIER_XMP_APP1)) {
                        // See XMP Specification Part 3: Storage in Files, 1.1.3 JPEG, Table 6
                        val offset = start + IDENTIFIER_XMP_APP1.size
                        val value = Arrays.copyOfRange(
                            bytes,
                            IDENTIFIER_XMP_APP1.size, bytes.size
                        )
                        // TODO: check if ignoring separate XMP data when tag 700 already exists is
                        //  valid.
                        if (getAttribute(TAG_XMP) == null) {
                            mAttributes[IFD_TYPE_PRIMARY][TAG_XMP] =
                                ExifAttribute(
                                    IFD_FORMAT_BYTE,
                                    value.size,
                                    offset.toLong(),
                                    value
                                )
                            mXmpIsFromSeparateMarker = true
                        }
                    }
                }

                MARKER_COM -> {
                    val bytes = ByteArray(length)
                    `in`.readFully(bytes)
                    length = 0
                    if (getAttribute(TAG_USER_COMMENT) == null) {
                        mAttributes[IFD_TYPE_EXIF][TAG_USER_COMMENT] =
                            ExifAttribute.createString(
                                String(
                                    bytes,
                                    ASCII
                                )
                            )
                    }
                }

                MARKER_SOF0, MARKER_SOF1, MARKER_SOF2, MARKER_SOF3, MARKER_SOF5, MARKER_SOF6, MARKER_SOF7, MARKER_SOF9, MARKER_SOF10, MARKER_SOF11, MARKER_SOF13, MARKER_SOF14, MARKER_SOF15 -> {
                    `in`.skipFully(1)
                    mAttributes[imageType][if (imageType != IFD_TYPE_THUMBNAIL)
                        TAG_IMAGE_LENGTH
                    else
                        TAG_THUMBNAIL_IMAGE_LENGTH] =
                        ExifAttribute.createULong(
                            `in`.readUnsignedShort().toLong(), mExifByteOrder
                        )
                    mAttributes[imageType][if (imageType != IFD_TYPE_THUMBNAIL)
                        TAG_IMAGE_WIDTH
                    else
                        TAG_THUMBNAIL_IMAGE_WIDTH] =
                        ExifAttribute.createULong(
                            `in`.readUnsignedShort().toLong(), mExifByteOrder
                        )
                    length -= 5
                }

                else -> {}
            }
            if (length < 0) {
                throw IOException("Invalid length")
            }
            `in`.skipFully(length)
            bytesRead += length
        }
        // Restore original byte order
        `in`.setByteOrder(mExifByteOrder)
    }

    @Throws(IOException::class)
    private fun getRawAttributes(`in`: SeekableByteOrderedDataInputStream) {
        // Parse TIFF Headers. See JEITA CP-3451C Section 4.5.2. Table 1.
        parseTiffHeaders(`in`)

        // Read TIFF image file directories. See JEITA CP-3451C Section 4.5.2. Figure 6.
        readImageFileDirectory(`in`, IFD_TYPE_PRIMARY)

        // Update ImageLength/Width tags for all image data.
        updateImageSizeValues(`in`, IFD_TYPE_PRIMARY)
        updateImageSizeValues(`in`, IFD_TYPE_PREVIEW)
        updateImageSizeValues(`in`, IFD_TYPE_THUMBNAIL)

        // Check if each image data is in valid position.
        validateImages()

        if (mMimeType == IMAGE_TYPE_PEF) {
            // PEF files contain a MakerNote data, which contains the data for ColorSpace tag.
            // See http://lclevy.free.fr/raw/ and piex.cc PefGetPreviewData()
            val makerNoteAttribute =
                mAttributes[IFD_TYPE_EXIF][TAG_MAKER_NOTE]
            if (makerNoteAttribute != null) {
                // Create an ordered DataInputStream for MakerNote
                val makerNoteDataInputStream =
                    SeekableByteOrderedDataInputStream(makerNoteAttribute.bytes)
                makerNoteDataInputStream.setByteOrder(mExifByteOrder)

                // Skip to MakerNote data
                makerNoteDataInputStream.skipFully(PEF_MAKER_NOTE_SKIP_SIZE)

                // Read IFD data from MakerNote
                readImageFileDirectory(makerNoteDataInputStream, IFD_TYPE_PEF)

                // Update ColorSpace tag
                val colorSpaceAttribute =
                    mAttributes[IFD_TYPE_PEF][TAG_COLOR_SPACE]
                if (colorSpaceAttribute != null) {
                    mAttributes[IFD_TYPE_EXIF][TAG_COLOR_SPACE] =
                        colorSpaceAttribute
                }
            }
        }
    }

    /**
     * RAF files contains a JPEG and a CFA data.
     * The JPEG contains two images, a preview and a thumbnail, while the CFA contains a RAW image.
     * This method looks at the first 160 bytes of a RAF file to retrieve the offset and length
     * values for the JPEG and CFA data.
     * Using that data, it parses the JPEG data to retrieve the preview and thumbnail image data,
     * then parses the CFA metadata to retrieve the primary image length/width values.
     * For data format details, see http://fileformats.archiveteam.org/wiki/Fujifilm_RAF
     */
    @Throws(IOException::class)
    private fun getRafAttributes(`in`: ByteOrderedDataInputStream) {
        if (DEBUG) {
            Log.d(
                TAG,
                "getRafAttributes starting with: $`in`"
            )
        }
        // Retrieve offset & length values
        `in`.skipFully(RAF_OFFSET_TO_JPEG_IMAGE_OFFSET)
        val offsetToJpegBytes = ByteArray(4)
        val jpegLengthBytes = ByteArray(4)
        val cfaHeaderOffsetBytes = ByteArray(4)
        `in`.readFully(offsetToJpegBytes)
        `in`.readFully(jpegLengthBytes)
        `in`.readFully(cfaHeaderOffsetBytes)
        val offsetToJpeg = ByteBuffer.wrap(offsetToJpegBytes).getInt()
        val jpegLength = ByteBuffer.wrap(jpegLengthBytes).getInt()
        val cfaHeaderOffset = ByteBuffer.wrap(cfaHeaderOffsetBytes).getInt()

        val jpegBytes = ByteArray(jpegLength)
        `in`.skipFully(offsetToJpeg - `in`.position())
        `in`.readFully(jpegBytes)

        // Retrieve JPEG image metadata
        val jpegInputStream = ByteOrderedDataInputStream(jpegBytes)
        getJpegAttributes(jpegInputStream, offsetToJpeg, IFD_TYPE_PREVIEW)

        // Skip to CFA header offset.
        `in`.skipFully(cfaHeaderOffset - `in`.position())

        // Retrieve primary image length/width values, if TAG_RAF_IMAGE_SIZE exists
        `in`.setByteOrder(ByteOrder.BIG_ENDIAN)
        val numberOfDirectoryEntry = `in`.readInt()
        if (DEBUG) {
            Log.d(
                TAG,
                "numberOfDirectoryEntry: $numberOfDirectoryEntry"
            )
        }
        // CFA stores some metadata about the RAW image. Since CFA uses proprietary tags, can only
        // find and retrieve image size information tags, while skipping others.
        // See piex.cc RafGetDimension()
        for (i in 0..<numberOfDirectoryEntry) {
            val tagNumber = `in`.readUnsignedShort()
            val numberOfBytes = `in`.readUnsignedShort()
            if (tagNumber == TAG_RAF_IMAGE_SIZE.number) {
                val imageLength = `in`.readShort().toInt()
                val imageWidth = `in`.readShort().toInt()
                val imageLengthAttribute =
                    ExifAttribute.createUShort(imageLength, mExifByteOrder)
                val imageWidthAttribute =
                    ExifAttribute.createUShort(imageWidth, mExifByteOrder)
                mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_LENGTH] =
                    imageLengthAttribute
                mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_WIDTH] =
                    imageWidthAttribute
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "Updated to length: $imageLength, width: $imageWidth"
                    )
                }
                return
            }
            `in`.skipFully(numberOfBytes)
        }
    }

    // Support for getting MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET and
    // MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH was added SDK 28.
    @Throws(IOException::class)
    private fun getHeifAttributes(`in`: SeekableByteOrderedDataInputStream) {
        if (Build.VERSION.SDK_INT >= 28) {
            val retriever = MediaMetadataRetriever()
            try {
                ExifInterfaceUtils.Api23Impl.setDataSource(retriever, object : MediaDataSource() {
                    var mPosition: Long = 0

                    @Throws(IOException::class)
                    override fun close() {
                    }

                    @Throws(IOException::class)
                    override fun readAt(
                        position: Long,
                        buffer: ByteArray,
                        offset: Int,
                        size: Int
                    ): Int {
                        var size = size
                        if (size == 0) {
                            return 0
                        }
                        if (position < 0) {
                            return -1
                        }
                        try {
                            if (mPosition != position) {
                                // We don't allow seek to positions after the available bytes,
                                // the input stream won't be able to seek back then.
                                // However, if we hit an exception before (mPosition set to -1),
                                // let it try the seek in hope it might recover.
                                if (mPosition >= 0 && position >= mPosition + `in`.available()) {
                                    return -1
                                }
                                `in`.seek(position)
                                mPosition = position
                            }

                            // If the read will cause us to go over the available bytes,
                            // reduce the size so that we stay in the available range.
                            // Otherwise the input stream may not be able to seek back.
                            if (size > `in`.available()) {
                                size = `in`.available()
                            }

                            val bytesRead = `in`.read(buffer, offset, size)
                            if (bytesRead >= 0) {
                                mPosition += bytesRead.toLong()
                                return bytesRead
                            }
                        } catch (e: IOException) {
                            // do nothing
                        }
                        mPosition = -1 // need to seek on next read
                        return -1
                    }

                    @Throws(IOException::class)
                    override fun getSize(): Long {
                        return -1
                    }
                })

                val exifOffsetStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_EXIF_OFFSET
                )
                val exifLengthStr = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_EXIF_LENGTH
                )
                val hasImage = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_IMAGE
                )
                val hasVideo = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                )

                var width: String? = null
                var height: String? = null
                var rotation: String? = null
                val metadataValueYes = "yes"
                // If the file has both image and video, prefer image info over video info.
                // App querying ExifInterface is most likely using the bitmap path which
                // picks the image first.
                if (metadataValueYes == hasImage) {
                    width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH
                    )
                    height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT
                    )
                    rotation = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_IMAGE_ROTATION
                    )
                } else if (metadataValueYes == hasVideo) {
                    width = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )
                    height = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )
                    rotation = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                    )
                }

                if (width != null) {
                    mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_WIDTH] =
                        ExifAttribute.createUShort(
                            width.toInt(),
                            mExifByteOrder
                        )
                }

                if (height != null) {
                    mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_LENGTH] =
                        ExifAttribute.createUShort(
                            height.toInt(),
                            mExifByteOrder
                        )
                }

                if (rotation != null) {
                    var orientation = ORIENTATION_NORMAL

                    // all rotation angles in CW
                    when (rotation.toInt()) {
                        90 -> orientation = ORIENTATION_ROTATE_90
                        180 -> orientation = ORIENTATION_ROTATE_180
                        270 -> orientation = ORIENTATION_ROTATE_270
                    }

                    mAttributes[IFD_TYPE_PRIMARY][TAG_ORIENTATION] =
                        ExifAttribute.createUShort(
                            orientation,
                            mExifByteOrder
                        )
                }

                if (exifOffsetStr != null && exifLengthStr != null) {
                    var offset = exifOffsetStr.toInt()
                    var length = exifLengthStr.toInt()
                    if (length <= 6) {
                        throw IOException("Invalid exif length")
                    }
                    `in`.seek(offset.toLong())
                    val identifier = ByteArray(6)
                    `in`.readFully(identifier)
                    offset += 6
                    length -= 6
                    if (!identifier.contentEquals(IDENTIFIER_EXIF_APP1)) {
                        throw IOException("Invalid identifier")
                    }

                    // TODO: Need to handle potential OutOfMemoryError
                    val bytes = ByteArray(length)
                    `in`.readFully(bytes)
                    // Save offset to EXIF data for handling thumbnail and attribute offsets.
                    mOffsetToExifData = offset
                    readExifSegment(bytes, IFD_TYPE_PRIMARY)
                }
                if (DEBUG) {
                    Log.d(TAG, "Heif meta: " + width + "x" + height + ", rotation " + rotation)
                }
            } catch (e: RuntimeException) {
                throw UnsupportedOperationException(
                    "Failed to read EXIF from HEIF file. "
                            + "Given stream is either malformed or unsupported."
                )
            } finally {
                retriever.release()
            }
        } else {
            throw UnsupportedOperationException(
                "Reading EXIF from HEIF files "
                        + "is supported from SDK 28 and above"
            )
        }
    }

    @Throws(IOException::class)
    private fun getStandaloneAttributes(`in`: SeekableByteOrderedDataInputStream) {
        `in`.skipFully(IDENTIFIER_EXIF_APP1.size)
        // TODO: Need to handle potential OutOfMemoryError
        val data = ByteArray(`in`.available())
        `in`.readFully(data)
        // Save offset to EXIF data for handling thumbnail and attribute offsets.
        mOffsetToExifData = IDENTIFIER_EXIF_APP1.size
        readExifSegment(data, IFD_TYPE_PRIMARY)
    }

    /**
     * ORF files contains a primary image data and a MakerNote data that contains preview/thumbnail
     * images. Both data takes the form of IFDs and can therefore be read with the
     * readImageFileDirectory() method.
     * This method reads all the necessary data and updates the primary/preview/thumbnail image
     * information according to the GetOlympusPreviewImage() method in piex.cc.
     * For data format details, see the following:
     * http://fileformats.archiveteam.org/wiki/Olympus_ORF
     * https://libopenraw.freedesktop.org/wiki/Olympus_ORF
     */
    @Throws(IOException::class)
    private fun getOrfAttributes(`in`: SeekableByteOrderedDataInputStream) {
        // Retrieve primary image data
        // Other Exif data will be located in the Makernote.
        getRawAttributes(`in`)

        // Additionally retrieve preview/thumbnail information from MakerNote tag, which contains
        // proprietary tags and therefore does not have offical documentation
        // See GetOlympusPreviewImage() in piex.cc & http://www.exiv2.org/tags-olympus.html
        val makerNoteAttribute =
            mAttributes[IFD_TYPE_EXIF][TAG_MAKER_NOTE]
        if (makerNoteAttribute != null) {
            // Create an ordered DataInputStream for MakerNote
            val makerNoteDataInputStream =
                SeekableByteOrderedDataInputStream(makerNoteAttribute.bytes)
            makerNoteDataInputStream.setByteOrder(mExifByteOrder)

            // There are two types of headers for Olympus MakerNotes
            // See http://www.exiv2.org/makernote.html#R1
            val makerNoteHeader1Bytes = ByteArray(ORF_MAKER_NOTE_HEADER_1.size)
            makerNoteDataInputStream.readFully(makerNoteHeader1Bytes)
            makerNoteDataInputStream.seek(0)
            val makerNoteHeader2Bytes = ByteArray(ORF_MAKER_NOTE_HEADER_2.size)
            makerNoteDataInputStream.readFully(makerNoteHeader2Bytes)
            // Skip the corresponding amount of bytes for each header type
            if (makerNoteHeader1Bytes.contentEquals(ORF_MAKER_NOTE_HEADER_1)) {
                makerNoteDataInputStream.seek(ORF_MAKER_NOTE_HEADER_1_SIZE.toLong())
            } else if (makerNoteHeader2Bytes.contentEquals(ORF_MAKER_NOTE_HEADER_2)) {
                makerNoteDataInputStream.seek(ORF_MAKER_NOTE_HEADER_2_SIZE.toLong())
            }

            // Read IFD data from MakerNote
            readImageFileDirectory(makerNoteDataInputStream, IFD_TYPE_ORF_MAKER_NOTE)

            // Retrieve & update preview image offset & length values
            val imageStartAttribute =
                mAttributes[IFD_TYPE_ORF_CAMERA_SETTINGS][TAG_ORF_PREVIEW_IMAGE_START]
            val imageLengthAttribute =
                mAttributes[IFD_TYPE_ORF_CAMERA_SETTINGS][TAG_ORF_PREVIEW_IMAGE_LENGTH]

            if (imageStartAttribute != null && imageLengthAttribute != null) {
                mAttributes[IFD_TYPE_PREVIEW][TAG_JPEG_INTERCHANGE_FORMAT] =
                    imageStartAttribute
                mAttributes[IFD_TYPE_PREVIEW][TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] =
                    imageLengthAttribute
            }

            // TODO: Check this behavior in other ORF files
            // Retrieve primary image length & width values
            // See piex.cc GetOlympusPreviewImage()
            val aspectFrameAttribute =
                mAttributes[IFD_TYPE_ORF_IMAGE_PROCESSING][TAG_ORF_ASPECT_FRAME]
            if (aspectFrameAttribute != null) {
                val aspectFrameValues = aspectFrameAttribute.getValue(mExifByteOrder) as IntArray?
                if (aspectFrameValues == null || aspectFrameValues.size != 4) {
                    Log.w(
                        TAG, "Invalid aspect frame values. frame="
                                + aspectFrameValues.contentToString()
                    )
                    return
                }
                if (aspectFrameValues[2] > aspectFrameValues[0] &&
                    aspectFrameValues[3] > aspectFrameValues[1]
                ) {
                    var primaryImageWidth = aspectFrameValues[2] - aspectFrameValues[0] + 1
                    var primaryImageLength = aspectFrameValues[3] - aspectFrameValues[1] + 1
                    // Swap width & length values
                    if (primaryImageWidth < primaryImageLength) {
                        primaryImageWidth += primaryImageLength
                        primaryImageLength = primaryImageWidth - primaryImageLength
                        primaryImageWidth -= primaryImageLength
                    }
                    val primaryImageWidthAttribute =
                        ExifAttribute.createUShort(primaryImageWidth, mExifByteOrder)
                    val primaryImageLengthAttribute =
                        ExifAttribute.createUShort(primaryImageLength, mExifByteOrder)

                    mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_WIDTH] =
                        primaryImageWidthAttribute
                    mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_LENGTH] =
                        primaryImageLengthAttribute
                }
            }
        }
    }

    // RW2 contains the primary image data in IFD0 and the preview and/or thumbnail image data in
    // the JpgFromRaw tag
    // See https://libopenraw.freedesktop.org/wiki/Panasonic_RAW/ and piex.cc Rw2GetPreviewData()
    @Throws(IOException::class)
    private fun getRw2Attributes(`in`: SeekableByteOrderedDataInputStream) {
        if (DEBUG) {
            Log.d(
                TAG,
                "getRw2Attributes starting with: $`in`"
            )
        }
        // Retrieve primary image data
        getRawAttributes(`in`)

        // Retrieve preview and/or thumbnail image data
        val jpgFromRawAttribute =
            mAttributes[IFD_TYPE_PRIMARY][TAG_RW2_JPG_FROM_RAW]
        if (jpgFromRawAttribute != null) {
            val jpegInputStream =
                ByteOrderedDataInputStream(jpgFromRawAttribute.bytes)
            getJpegAttributes(
                jpegInputStream, jpgFromRawAttribute.bytesOffset.toInt(),
                IFD_TYPE_PREVIEW
            )
        }

        // Set ISO tag value if necessary
        val rw2IsoAttribute =
            mAttributes[IFD_TYPE_PRIMARY][TAG_RW2_ISO]
        if (rw2IsoAttribute != null && mAttributes[IFD_TYPE_EXIF][TAG_PHOTOGRAPHIC_SENSITIVITY] == null) {
            // Place this attribute only if it doesn't exist
            mAttributes[IFD_TYPE_EXIF][TAG_PHOTOGRAPHIC_SENSITIVITY] =
                rw2IsoAttribute
        }
    }

    // PNG contains the EXIF data as a Special-Purpose Chunk
    @Throws(IOException::class)
    private fun getPngAttributes(`in`: ByteOrderedDataInputStream) {
        if (DEBUG) {
            Log.d(
                TAG,
                "getPngAttributes starting with: $`in`"
            )
        }
        // PNG uses Big Endian by default.
        // See PNG (Portable Network Graphics) Specification, Version 1.2,
        // 2.1. Integers and byte order
        `in`.setByteOrder(ByteOrder.BIG_ENDIAN)

        var bytesRead = 0

        // Skip the signature bytes
        `in`.skipFully(PNG_SIGNATURE.size)
        bytesRead += PNG_SIGNATURE.size

        // Each chunk is made up of four parts:
        //   1) Length: 4-byte unsigned integer indicating the number of bytes in the
        //   Chunk Data field. Excludes Chunk Type and CRC bytes.
        //   2) Chunk Type: 4-byte chunk type code.
        //   3) Chunk Data: The data bytes. Can be zero-length.
        //   4) CRC: 4-byte data calculated on the preceding bytes in the chunk. Always
        //   present.
        // --> 4 (length bytes) + 4 (type bytes) + X (data bytes) + 4 (CRC bytes)
        // See PNG (Portable Network Graphics) Specification, Version 1.2,
        // 3.2. Chunk layout
        try {
            while (true) {
                val length = `in`.readInt()
                bytesRead += 4

                val type = ByteArray(PNG_CHUNK_TYPE_BYTE_LENGTH)
                `in`.readFully(type)
                bytesRead += PNG_CHUNK_TYPE_BYTE_LENGTH

                // The first chunk must be the IHDR chunk
                if (bytesRead == 16 && !type.contentEquals(PNG_CHUNK_TYPE_IHDR)) {
                    throw IOException(
                        "Encountered invalid PNG file--IHDR chunk should appear"
                                + "as the first chunk"
                    )
                }

                if (type.contentEquals(PNG_CHUNK_TYPE_IEND)) {
                    // IEND marks the end of the image.
                    break
                } else if (type.contentEquals(PNG_CHUNK_TYPE_EXIF)) {
                    // TODO: Need to handle potential OutOfMemoryError
                    val data = ByteArray(length)
                    `in`.readFully(data)

                    // Compare CRC values for potential data corruption.
                    val dataCrcValue = `in`.readInt()
                    // Cyclic Redundancy Code used to check for corruption of the data
                    val crc = CRC32()
                    crc.update(type)
                    crc.update(data)
                    if (crc.value.toInt() != dataCrcValue) {
                        throw IOException(
                            ("""Encountered invalid CRC value for PNG-EXIF chunk.
 recorded CRC value: $dataCrcValue, calculated CRC value: ${crc.value}""")
                        )
                    }
                    // Save offset to EXIF data for handling thumbnail and attribute offsets.
                    mOffsetToExifData = bytesRead
                    readExifSegment(data, IFD_TYPE_PRIMARY)
                    validateImages()

                    setThumbnailData(ByteOrderedDataInputStream(data))
                    break
                } else {
                    // Skip to next chunk
                    `in`.skipFully(length + PNG_CHUNK_CRC_BYTE_LENGTH)
                    bytesRead += length + PNG_CHUNK_CRC_BYTE_LENGTH
                }
            }
        } catch (e: EOFException) {
            // Should not reach here. Will only reach here if the file is corrupted or
            // does not follow the PNG specifications
            throw IOException("Encountered corrupt PNG file.")
        }
    }

    // WebP contains EXIF data as a RIFF File Format Chunk
    // All references below can be found in the following link.
    // https://developers.google.com/speed/webp/docs/riff_container
    @Throws(IOException::class)
    private fun getWebpAttributes(`in`: ByteOrderedDataInputStream) {
        if (DEBUG) {
            Log.d(
                TAG,
                "getWebpAttributes starting with: $`in`"
            )
        }
        // WebP uses little-endian by default.
        // See Section "Terminology & Basics"
        `in`.setByteOrder(ByteOrder.LITTLE_ENDIAN)

        `in`.skipFully(WEBP_SIGNATURE_1.size)
        // File size corresponds to the size of the entire file from offset 8.
        // See Section "WebP File Header"
        val fileSize = `in`.readInt() + 8
        var bytesRead = 8

        `in`.skipFully(WEBP_SIGNATURE_2.size)
        bytesRead += WEBP_SIGNATURE_2.size

        try {
            while (true) {
                // TODO: Check the first Chunk Type, and if it is VP8X, check if the chunks are
                // ordered properly.

                // Each chunk is made up of three parts:
                //   1) Chunk FourCC: 4-byte concatenating four ASCII characters.
                //   2) Chunk Size: 4-byte unsigned integer indicating the size of the chunk.
                //                  Excludes Chunk FourCC and Chunk Size bytes.
                //   3) Chunk Payload: data payload. A single padding byte ('0') is added if
                //                     Chunk Size is odd.
                // See Section "RIFF File Format"

                val code = ByteArray(WEBP_CHUNK_TYPE_BYTE_LENGTH)
                `in`.readFully(code)
                bytesRead += WEBP_CHUNK_TYPE_BYTE_LENGTH

                var chunkSize = `in`.readInt()
                bytesRead += 4

                if (WEBP_CHUNK_TYPE_EXIF.contentEquals(code)) {
                    // TODO: Need to handle potential OutOfMemoryError
                    val payload = ByteArray(chunkSize)
                    `in`.readFully(payload)
                    // Save offset to EXIF data for handling thumbnail and attribute offsets.
                    mOffsetToExifData = bytesRead
                    readExifSegment(payload, IFD_TYPE_PRIMARY)

                    setThumbnailData(ByteOrderedDataInputStream(payload))
                    break
                } else {
                    // Add a single padding byte at end if chunk size is odd
                    chunkSize = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize

                    // Check if skipping to next chunk is necessary
                    if (bytesRead + chunkSize == fileSize) {
                        // Reached end of file
                        break
                    } else if (bytesRead + chunkSize > fileSize) {
                        throw IOException("Encountered WebP file with invalid chunk size")
                    }

                    // Skip to next chunk
                    `in`.skipFully(chunkSize)
                    bytesRead += chunkSize
                }
            }
        } catch (e: EOFException) {
            // Should not reach here. Will only reach here if the file is corrupted or
            // does not follow the WebP specifications
            throw IOException("Encountered corrupt WebP file.")
        }
    }

    // Stores a new JPEG image with EXIF attributes into a given output stream.
    @Throws(IOException::class)
    private fun saveJpegAttributes(inputStream: InputStream, outputStream: OutputStream) {
        // See JPEG File Interchange Format Specification, "JFIF Specification"
        if (DEBUG) {
            Log.d(
                TAG, ("saveJpegAttributes starting with (inputStream: " + inputStream
                        + ", outputStream: " + outputStream + ")")
            )
        }
        val dataInputStream = ByteOrderedDataInputStream(inputStream)
        val dataOutputStream =
            ByteOrderedDataOutputStream(outputStream, ByteOrder.BIG_ENDIAN)
        if (dataInputStream.readByte() != MARKER) {
            throw IOException("Invalid marker")
        }
        dataOutputStream.writeByte(MARKER.toInt())
        if (dataInputStream.readByte() != MARKER_SOI) {
            throw IOException("Invalid marker")
        }
        dataOutputStream.writeByte(MARKER_SOI.toInt())

        // Remove XMP data if it is from a separate marker (IDENTIFIER_XMP_APP1, not
        // IDENTIFIER_EXIF_APP1)
        // Will re-add it later after the rest of the file is written
        var xmpAttribute: ExifAttribute? = null
        if (getAttribute(TAG_XMP) != null && mXmpIsFromSeparateMarker) {
            xmpAttribute = mAttributes[IFD_TYPE_PRIMARY].remove(TAG_XMP)
        }

        // Write EXIF APP1 segment
        dataOutputStream.writeByte(MARKER.toInt())
        dataOutputStream.writeByte(MARKER_APP1.toInt())
        writeExifSegment(dataOutputStream)

        // Re-add previously removed XMP data.
        if (xmpAttribute != null) {
            mAttributes[IFD_TYPE_PRIMARY][TAG_XMP] =
                xmpAttribute
        }

        val bytes = ByteArray(4096)

        while (true) {
            var marker = dataInputStream.readByte()
            if (marker != MARKER) {
                throw IOException("Invalid marker")
            }
            marker = dataInputStream.readByte()
            when (marker) {
                MARKER_APP1 -> {
                    var length = dataInputStream.readUnsignedShort() - 2
                    if (length < 0) {
                        throw IOException("Invalid length")
                    }
                    val identifier = ByteArray(6)
                    if (length >= 6) {
                        dataInputStream.readFully(identifier)
                        if (identifier.contentEquals(IDENTIFIER_EXIF_APP1)) {
                            // Skip the original EXIF APP1 segment.
                            dataInputStream.skipFully(length - 6)
                            break
                        }
                    }
                    // Copy non-EXIF APP1 segment.
                    dataOutputStream.writeByte(MARKER.toInt())
                    dataOutputStream.writeByte(marker.toInt())
                    dataOutputStream.writeUnsignedShort(length + 2)
                    if (length >= 6) {
                        length -= 6
                        dataOutputStream.write(identifier)
                    }
                    var read = 0
                    while (length > 0 && (dataInputStream.read(
                            bytes, 0, min(length, bytes.size)
                        ).also { read = it }) >= 0
                    ) {
                        dataOutputStream.write(bytes, 0, read)
                        length -= read
                    }
                }

                MARKER_EOI, MARKER_SOS -> {
                    dataOutputStream.writeByte(MARKER.toInt())
                    dataOutputStream.writeByte(marker.toInt())
                    // Copy all the remaining data
                    ExifInterfaceUtils.copy(dataInputStream, dataOutputStream)
                    return
                }

                else -> {
                    // Copy JPEG segment
                    dataOutputStream.writeByte(MARKER.toInt())
                    dataOutputStream.writeByte(marker.toInt())
                    var length = dataInputStream.readUnsignedShort()
                    dataOutputStream.writeUnsignedShort(length)
                    length -= 2
                    if (length < 0) {
                        throw IOException("Invalid length")
                    }
                    var read = 0
                    while (length > 0 && (dataInputStream.read(
                            bytes, 0, min(length, bytes.size)
                        ).also { read = it }) >= 0
                    ) {
                        dataOutputStream.write(bytes, 0, read)
                        length -= read
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun savePngAttributes(inputStream: InputStream, outputStream: OutputStream) {
        if (DEBUG) {
            Log.d(
                TAG, ("savePngAttributes starting with (inputStream: " + inputStream
                        + ", outputStream: " + outputStream + ")")
            )
        }
        val dataInputStream = ByteOrderedDataInputStream(inputStream)
        val dataOutputStream =
            ByteOrderedDataOutputStream(outputStream, ByteOrder.BIG_ENDIAN)

        // Copy PNG signature bytes
        ExifInterfaceUtils.copy(dataInputStream, dataOutputStream, PNG_SIGNATURE.size)

        // EXIF chunk can appear anywhere between the first (IHDR) and last (IEND) chunks, except
        // between IDAT chunks.
        // Adhering to these rules,
        //   1) if EXIF chunk did not exist in the original file, it will be stored right after the
        //      first chunk,
        //   2) if EXIF chunk existed in the original file, it will be stored in the same location.
        if (mOffsetToExifData == 0) {
            // Copy IHDR chunk bytes
            val ihdrChunkLength = dataInputStream.readInt()
            dataOutputStream.writeInt(ihdrChunkLength)
            ExifInterfaceUtils.copy(
                dataInputStream, dataOutputStream, (PNG_CHUNK_TYPE_BYTE_LENGTH
                        + ihdrChunkLength + PNG_CHUNK_CRC_BYTE_LENGTH)
            )
        } else {
            // Copy up until the point where EXIF chunk length information is stored.
            val copyLength = (mOffsetToExifData - PNG_SIGNATURE.size
                    - 4 /* PNG EXIF chunk length bytes */
                    - PNG_CHUNK_TYPE_BYTE_LENGTH)
            ExifInterfaceUtils.copy(dataInputStream, dataOutputStream, copyLength)

            // Skip to the start of the chunk after the EXIF chunk
            val exifChunkLength = dataInputStream.readInt()
            dataInputStream.skipFully(
                (PNG_CHUNK_TYPE_BYTE_LENGTH + exifChunkLength
                        + PNG_CHUNK_CRC_BYTE_LENGTH)
            )
        }

        // Write EXIF data
        var exifByteArrayOutputStream: ByteArrayOutputStream? = null
        try {
            // A byte array is needed to calculate the CRC value of this chunk which requires
            // the chunk type bytes and the chunk data bytes.
            exifByteArrayOutputStream = ByteArrayOutputStream()
            val exifDataOutputStream =
                ByteOrderedDataOutputStream(exifByteArrayOutputStream, ByteOrder.BIG_ENDIAN)

            // Store Exif data in separate byte array
            writeExifSegment(exifDataOutputStream)
            val exifBytes =
                (exifDataOutputStream.mOutputStream as ByteArrayOutputStream).toByteArray()

            // Write EXIF chunk data
            dataOutputStream.write(exifBytes)

            // Write EXIF chunk CRC
            val crc = CRC32()
            crc.update(exifBytes, 4,  /* skip length bytes */exifBytes.size - 4)
            dataOutputStream.writeInt(crc.value.toInt())
        } finally {
            ExifInterfaceUtils.closeQuietly(exifByteArrayOutputStream)
        }

        // Copy the rest of the file
        ExifInterfaceUtils.copy(dataInputStream, dataOutputStream)
    }

    // A WebP file has a header and a series of chunks.
    // The header is composed of:
    //   "RIFF" + File Size + "WEBP"
    //
    // The structure of the chunks can be divided largely into two categories:
    //   1) Contains only image data,
    //   2) Contains image data and extra data.
    // In the first category, there is only one chunk: type "VP8" (compression with loss) or "VP8L"
    // (lossless compression).
    // In the second category, the first chunk will be of type "VP8X", which contains flags
    // indicating which extra data exist in later chunks. The proceeding chunks must conform to
    // the following order based on type (if they exist):
    //   Color Profile ("ICCP") + Animation Control Data ("ANIM") + Image Data ("VP8"/"VP8L")
    //   + Exif metadata ("EXIF") + XMP metadata ("XMP")
    //
    // And in order to have EXIF data, a WebP file must be of the second structure and thus follow
    // the following rules:
    //   1) "VP8X" chunk as the first chunk,
    //   2) flag for EXIF inside "VP8X" chunk set to 1, and
    //   3) contain the "EXIF" chunk in the correct order amongst other chunks.
    //
    // Based on these rules, this API will support three different cases depending on the contents
    // of the original file:
    //   1) "EXIF" chunk already exists
    //     -> replace it with the new "EXIF" chunk
    //   2) "EXIF" chunk does not exist and the first chunk is "VP8" or "VP8L"
    //     -> add "VP8X" before the "VP8"/"VP8L" chunk (with EXIF flag set to 1), and add new
    //     "EXIF" chunk after the "VP8"/"VP8L" chunk.
    //   3) "EXIF" chunk does not exist and the first chunk is "VP8X"
    //     -> set EXIF flag in "VP8X" chunk to 1, and add new "EXIF" chunk at the proper location.
    //
    // See https://developers.google.com/speed/webp/docs/riff_container for more details.
    @Throws(IOException::class)
    private fun saveWebpAttributes(inputStream: InputStream, outputStream: OutputStream) {
        if (DEBUG) {
            Log.d(
                TAG, ("saveWebpAttributes starting with (inputStream: " + inputStream
                        + ", outputStream: " + outputStream + ")")
            )
        }
        val totalInputStream =
            ByteOrderedDataInputStream(inputStream, ByteOrder.LITTLE_ENDIAN)
        val totalOutputStream =
            ByteOrderedDataOutputStream(outputStream, ByteOrder.LITTLE_ENDIAN)

        // WebP signature
        ExifInterfaceUtils.copy(totalInputStream, totalOutputStream, WEBP_SIGNATURE_1.size)
        // File length will be written after all the chunks have been written
        totalInputStream.skipFully(WEBP_FILE_SIZE_BYTE_LENGTH + WEBP_SIGNATURE_2.size)

        // Create a separate byte array to calculate file length
        var nonHeaderByteArrayOutputStream: ByteArrayOutputStream? = null
        try {
            nonHeaderByteArrayOutputStream = ByteArrayOutputStream()
            val nonHeaderOutputStream =
                ByteOrderedDataOutputStream(nonHeaderByteArrayOutputStream, ByteOrder.LITTLE_ENDIAN)

            if (mOffsetToExifData != 0) {
                // EXIF chunk exists in the original file
                // Tested by webp_with_exif.webp
                val bytesRead = (WEBP_SIGNATURE_1.size + WEBP_FILE_SIZE_BYTE_LENGTH
                        + WEBP_SIGNATURE_2.size)
                ExifInterfaceUtils.copy(
                    totalInputStream, nonHeaderOutputStream,
                    (mOffsetToExifData - bytesRead - WEBP_CHUNK_TYPE_BYTE_LENGTH
                            - WEBP_CHUNK_SIZE_BYTE_LENGTH)
                )

                // Skip input stream to the end of the EXIF chunk
                totalInputStream.skipFully(WEBP_CHUNK_TYPE_BYTE_LENGTH)
                var exifChunkLength = totalInputStream.readInt()
                // RIFF chunks have a single padding byte at the end if the declared chunk size is
                // odd.
                if (exifChunkLength % 2 != 0) {
                    exifChunkLength++
                }
                totalInputStream.skipFully(exifChunkLength)

                // Write new EXIF chunk to output stream
                writeExifSegment(nonHeaderOutputStream)
            } else {
                // EXIF chunk does not exist in the original file
                val firstChunkType = ByteArray(WEBP_CHUNK_TYPE_BYTE_LENGTH)
                totalInputStream.readFully(firstChunkType)

                if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8X)) {
                    // Original file already includes other extra data
                    val size = totalInputStream.readInt()
                    // WebP files have a single padding byte at the end if the chunk size is odd.
                    val data = ByteArray(if ((size % 2) == 1) size + 1 else size)
                    totalInputStream.readFully(data)

                    // Set the EXIF flag to 1
                    data[0] = (data[0].toInt() or (1 shl 3)).toByte()

                    // Retrieve Animation flag--in order to check where EXIF data should start
                    val containsAnimation = ((data[0].toInt() shr 1) and 1) == 1

                    // Write the original VP8X chunk
                    nonHeaderOutputStream.write(WEBP_CHUNK_TYPE_VP8X)
                    nonHeaderOutputStream.writeInt(size)
                    nonHeaderOutputStream.write(data)

                    // Animation control data is composed of 1 ANIM chunk and multiple ANMF
                    // chunks and since the image data (VP8/VP8L) chunks are included in the ANMF
                    // chunks, EXIF data should come after the last ANMF chunk.
                    // Also, because there is no value indicating the amount of ANMF chunks, we need
                    // to keep iterating through chunks until we either reach the end of the file or
                    // the XMP chunk (if it exists).
                    // Tested by webp_with_anim_without_exif.webp
                    if (containsAnimation) {
                        copyChunksUpToGivenChunkType(
                            totalInputStream, nonHeaderOutputStream,
                            WEBP_CHUNK_TYPE_ANIM, null
                        )

                        while (true) {
                            val type = ByteArray(WEBP_CHUNK_TYPE_BYTE_LENGTH)
                            var animationFinished = false
                            try {
                                totalInputStream.readFully(type)
                                animationFinished = !type.contentEquals(WEBP_CHUNK_TYPE_ANMF)
                            } catch (e: EOFException) {
                                animationFinished = true
                            }
                            if (animationFinished) {
                                writeExifSegment(nonHeaderOutputStream)
                                break
                            }
                            copyWebPChunk(totalInputStream, nonHeaderOutputStream, type)
                        }
                    } else {
                        // Skip until we find the VP8 or VP8L chunk
                        copyChunksUpToGivenChunkType(
                            totalInputStream, nonHeaderOutputStream,
                            WEBP_CHUNK_TYPE_VP8, WEBP_CHUNK_TYPE_VP8L
                        )
                        writeExifSegment(nonHeaderOutputStream)
                    }
                } else if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8) || firstChunkType.contentEquals(
                        WEBP_CHUNK_TYPE_VP8L
                    )
                ) {
                    val size = totalInputStream.readInt()
                    var bytesToRead = size
                    // WebP files have a single padding byte at the end if the chunk size is odd.
                    if (size % 2 == 1) {
                        bytesToRead += 1
                    }

                    // Retrieve image width/height
                    var widthAndHeight = 0
                    var width = 0
                    var height = 0
                    var alpha = false
                    // Save VP8 frame data for later
                    val vp8Frame = ByteArray(3)

                    if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8)) {
                        totalInputStream.readFully(vp8Frame)

                        // Check signature
                        val vp8Signature = ByteArray(3)
                        totalInputStream.readFully(vp8Signature)
                        if (!WEBP_VP8_SIGNATURE.contentEquals(vp8Signature)) {
                            throw IOException("Error checking VP8 signature")
                        }

                        // Retrieve image width/height
                        widthAndHeight = totalInputStream.readInt()
                        width = (widthAndHeight shl 18) shr 18
                        height = (widthAndHeight shl 2) shr 18
                        bytesToRead -= (vp8Frame.size + vp8Signature.size + 4)
                    } else if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8L)) {
                        // Check signature
                        val vp8lSignature = totalInputStream.readByte()
                        if (vp8lSignature != WEBP_VP8L_SIGNATURE) {
                            throw IOException("Error checking VP8L signature")
                        }

                        // Retrieve image width/height
                        widthAndHeight = totalInputStream.readInt()
                        // VP8L stores 14-bit 'width - 1' and 'height - 1' values. See "RIFF Header"
                        // of "WebP Lossless Bitstream Specification".
                        width = (widthAndHeight and 0x3FFF) + 1 // Read bits 0 - 13
                        height = ((widthAndHeight and 0xFFFC000) ushr 14) + 1 // Read bits 14 - 27
                        // Retrieve alpha bit 28
                        alpha = (widthAndHeight and (1 shl 28)) != 0
                        bytesToRead -= (1 /* VP8L signature */ + 4)
                    }

                    // Create VP8X with Exif flag set to 1
                    nonHeaderOutputStream.write(WEBP_CHUNK_TYPE_VP8X)
                    nonHeaderOutputStream.writeInt(WEBP_CHUNK_TYPE_VP8X_DEFAULT_LENGTH)
                    val data = ByteArray(WEBP_CHUNK_TYPE_VP8X_DEFAULT_LENGTH)
                    // ALPHA flag
                    if (alpha) {
                        data[0] = (data[0].toInt() or (1 shl 4)).toByte()
                    }
                    // EXIF flag
                    data[0] = (data[0].toInt() or (1 shl 3)).toByte()
                    // VP8X stores Width - 1 and Height - 1 values
                    width -= 1
                    height -= 1
                    data[4] = width.toByte()
                    data[5] = (width shr 8).toByte()
                    data[6] = (width shr 16).toByte()
                    data[7] = height.toByte()
                    data[8] = (height shr 8).toByte()
                    data[9] = (height shr 16).toByte()
                    nonHeaderOutputStream.write(data)

                    // Write VP8 or VP8L data
                    nonHeaderOutputStream.write(firstChunkType)
                    nonHeaderOutputStream.writeInt(size)
                    if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8)) {
                        nonHeaderOutputStream.write(vp8Frame)
                        nonHeaderOutputStream.write(WEBP_VP8_SIGNATURE)
                        nonHeaderOutputStream.writeInt(widthAndHeight)
                    } else if (firstChunkType.contentEquals(WEBP_CHUNK_TYPE_VP8L)) {
                        nonHeaderOutputStream.write(WEBP_VP8L_SIGNATURE.toInt())
                        nonHeaderOutputStream.writeInt(widthAndHeight)
                    }
                    ExifInterfaceUtils.copy(totalInputStream, nonHeaderOutputStream, bytesToRead)

                    // Write EXIF chunk
                    writeExifSegment(nonHeaderOutputStream)
                }
            }

            // Copy the rest of the file
            ExifInterfaceUtils.copy(totalInputStream, nonHeaderOutputStream)

            // Write file length + second signature
            totalOutputStream.writeInt(
                nonHeaderByteArrayOutputStream.size()
                        + WEBP_SIGNATURE_2.size
            )
            totalOutputStream.write(WEBP_SIGNATURE_2)
            nonHeaderByteArrayOutputStream.writeTo(totalOutputStream)
        } catch (e: Exception) {
            throw IOException("Failed to save WebP file", e)
        } finally {
            ExifInterfaceUtils.closeQuietly(nonHeaderByteArrayOutputStream)
        }
    }

    @Throws(IOException::class)
    private fun copyChunksUpToGivenChunkType(
        inputStream: ByteOrderedDataInputStream,
        outputStream: ByteOrderedDataOutputStream, firstGivenType: ByteArray,
        secondGivenType: ByteArray?
    ) {
        while (true) {
            val type = ByteArray(WEBP_CHUNK_TYPE_BYTE_LENGTH)
            inputStream.readFully(type)
            copyWebPChunk(inputStream, outputStream, type)
            if (type.contentEquals(firstGivenType) || (secondGivenType != null && type.contentEquals(
                    secondGivenType
                ))
            ) {
                break
            }
        }
    }

    @Throws(IOException::class)
    private fun copyWebPChunk(
        inputStream: ByteOrderedDataInputStream,
        outputStream: ByteOrderedDataOutputStream, type: ByteArray
    ) {
        val size = inputStream.readInt()
        outputStream.write(type)
        outputStream.writeInt(size)
        // WebP files have a single padding byte at the end if the chunk size is odd.
        ExifInterfaceUtils.copy(inputStream, outputStream, if ((size % 2) == 1) size + 1 else size)
    }

    // Reads the given EXIF byte area and save its tag data into attributes.
    @Throws(IOException::class)
    private fun readExifSegment(exifBytes: ByteArray, imageType: Int) {
        val dataInputStream =
            SeekableByteOrderedDataInputStream(exifBytes)

        // Parse TIFF Headers. See JEITA CP-3451C Section 4.5.2. Table 1.
        parseTiffHeaders(dataInputStream)

        // Read TIFF image file directories. See JEITA CP-3451C Section 4.5.2. Figure 6.
        readImageFileDirectory(dataInputStream, imageType)
    }

    private fun addDefaultValuesForCompatibility() {
        // If DATETIME tag has no value, then set the value to DATETIME_ORIGINAL tag's.
        val valueOfDateTimeOriginal = getAttribute(TAG_DATETIME_ORIGINAL)
        if (valueOfDateTimeOriginal != null && getAttribute(TAG_DATETIME) == null) {
            mAttributes[IFD_TYPE_PRIMARY][TAG_DATETIME] =
                ExifAttribute.createString(
                    valueOfDateTimeOriginal
                )
        }

        // NOTE: The upstream AOSP ExifInterface fills in default 0 values for
        // IMAGE_WIDTH / IMAGE_LENGTH / ORIENTATION / LIGHT_SOURCE when they are absent.
        // That behavior exists to make *writing* a well-formed IFD easier, but this app
        // only uses ExifInterface for *reading*. The synthesized 0s leak into the UI as
        // bogus entries (e.g. a metadata-free image shows "Light source: 0"), so they are
        // intentionally omitted here. Only the DATETIME backfill above is kept because it
        // carries real semantic value.
    }

    @Throws(IOException::class)
    private fun readByteOrder(dataInputStream: ByteOrderedDataInputStream): ByteOrder {
        // Read byte order.
        val byteOrder = dataInputStream.readShort()
        when (byteOrder) {
            BYTE_ALIGN_II -> {
                if (DEBUG) {
                    Log.d(TAG, "readExifSegment: Byte Align II")
                }
                return ByteOrder.LITTLE_ENDIAN
            }

            BYTE_ALIGN_MM -> {
                if (DEBUG) {
                    Log.d(TAG, "readExifSegment: Byte Align MM")
                }
                return ByteOrder.BIG_ENDIAN
            }

            else -> throw IOException("Invalid byte order: " + Integer.toHexString(byteOrder.toInt()))
        }
    }

    @Throws(IOException::class)
    private fun parseTiffHeaders(dataInputStream: ByteOrderedDataInputStream) {
        // Read byte order
        mExifByteOrder = readByteOrder(dataInputStream)
        // Set byte order
        dataInputStream.setByteOrder(mExifByteOrder)

        // Check start code
        val startCode = dataInputStream.readUnsignedShort()
        if (mMimeType != IMAGE_TYPE_ORF && mMimeType != IMAGE_TYPE_RW2 && startCode != START_CODE.toInt()) {
            throw IOException("Invalid start code: " + Integer.toHexString(startCode))
        }

        // Read and skip to first ifd offset
        var firstIfdOffset = dataInputStream.readInt()
        if (firstIfdOffset < 8) {
            throw IOException("Invalid first Ifd offset: $firstIfdOffset")
        }
        firstIfdOffset -= 8
        if (firstIfdOffset > 0) {
            dataInputStream.skipFully(firstIfdOffset)
        }
    }

    // Reads image file directory, which is a tag group in EXIF.
    @Throws(IOException::class)
    private fun readImageFileDirectory(
        dataInputStream: SeekableByteOrderedDataInputStream,
        @IfdType ifdType: Int
    ) {
        // Save offset of current IFD to prevent reading an IFD that is already read.
        mAttributesOffsets.add(dataInputStream.position())

        // See TIFF 6.0 Section 2: TIFF Structure, Figure 1.
        val numberOfDirectoryEntry = dataInputStream.readShort()
        if (DEBUG) {
            Log.d(
                TAG,
                "numberOfDirectoryEntry: $numberOfDirectoryEntry"
            )
        }
        if (numberOfDirectoryEntry <= 0) {
            // Return if the size of entries is negative.
            return
        }

        // See TIFF 6.0 Section 2: TIFF Structure, "Image File Directory".
        for (i in 0..<numberOfDirectoryEntry) {
            val tagNumber = dataInputStream.readUnsignedShort()
            var dataFormat = dataInputStream.readUnsignedShort()
            val numberOfComponents = dataInputStream.readInt()
            // Next four bytes is for data offset or value.
            val nextEntryOffset = dataInputStream.position() + 4L

            // Look up a corresponding tag from tag number
            val tag = sExifTagMapsForReading[ifdType][tagNumber]

            if (DEBUG) {
                Log.d(
                    TAG, String.format(
                        "ifdType: %d, tagNumber: %d, tagName: %s, dataFormat: %d, "
                                + "numberOfComponents: %d", ifdType, tagNumber,
                        tag?.name, dataFormat, numberOfComponents
                    )
                )
            }

            var byteCount: Long = 0
            var valid = false
            if (tag == null) {
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "Skip the tag entry since tag number is not defined: $tagNumber"
                    )
                }
            } else if (dataFormat <= 0 || dataFormat >= IFD_FORMAT_BYTES_PER_FORMAT.size) {
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "Skip the tag entry since data format is invalid: $dataFormat"
                    )
                }
            } else if (!tag.isFormatCompatible(dataFormat)) {
                if (DEBUG) {
                    Log.d(
                        TAG, ("Skip the tag entry since data format ("
                                + IFD_FORMAT_NAMES[dataFormat] + ") is unexpected for tag: "
                                + tag.name)
                    )
                }
            } else {
                if (dataFormat == IFD_FORMAT_UNDEFINED) {
                    dataFormat = tag.primaryFormat
                }
                byteCount = numberOfComponents.toLong() * IFD_FORMAT_BYTES_PER_FORMAT[dataFormat]
                if (byteCount < 0 || byteCount > Int.MAX_VALUE) {
                    if (DEBUG) {
                        Log.d(
                            TAG, "Skip the tag entry since the number of components is invalid: "
                                    + numberOfComponents
                        )
                    }
                } else {
                    valid = true
                }
            }
            if (!valid) {
                dataInputStream.seek(nextEntryOffset)
                continue
            }

            // Read a value from data field or seek to the value offset which is stored in data
            // field if the size of the entry value is bigger than 4.
            if (byteCount > 4) {
                val offset = dataInputStream.readInt()
                if (DEBUG) {
                    Log.d(
                        TAG,
                        "seek to data offset: $offset"
                    )
                }
                if (mMimeType == IMAGE_TYPE_ORF) {
                    if (TAG_MAKER_NOTE == tag!!.name) {
                        // Save offset value for reading thumbnail
                        mOrfMakerNoteOffset = offset
                    } else if (ifdType == IFD_TYPE_ORF_MAKER_NOTE
                        && TAG_ORF_THUMBNAIL_IMAGE == tag.name
                    ) {
                        // Retrieve & update values for thumbnail offset and length values for ORF
                        mOrfThumbnailOffset = offset
                        mOrfThumbnailLength = numberOfComponents

                        val compressionAttribute =
                            ExifAttribute.createUShort(DATA_JPEG, mExifByteOrder)
                        val jpegInterchangeFormatAttribute =
                            ExifAttribute.createULong(mOrfThumbnailOffset.toLong(), mExifByteOrder)
                        val jpegInterchangeFormatLengthAttribute =
                            ExifAttribute.createULong(mOrfThumbnailLength.toLong(), mExifByteOrder)

                        mAttributes[IFD_TYPE_THUMBNAIL][TAG_COMPRESSION] =
                            compressionAttribute
                        mAttributes[IFD_TYPE_THUMBNAIL][TAG_JPEG_INTERCHANGE_FORMAT] =
                            jpegInterchangeFormatAttribute
                        mAttributes[IFD_TYPE_THUMBNAIL][TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] =
                            jpegInterchangeFormatLengthAttribute
                    }
                }
                dataInputStream.seek(offset.toLong())
            }

            // Recursively parse IFD when a IFD pointer tag appears.
            val nextIfdType = sExifPointerTagMap[tagNumber]
            if (DEBUG) {
                Log.d(
                    TAG,
                    "nextIfdType: $nextIfdType byteCount: $byteCount"
                )
            }

            if (nextIfdType != null) {
                var offset = -1L
                // Get offset from data field
                when (dataFormat) {
                    IFD_FORMAT_USHORT -> {
                        offset = dataInputStream.readUnsignedShort().toLong()
                    }

                    IFD_FORMAT_SSHORT -> {
                        offset = dataInputStream.readShort().toLong()
                    }

                    IFD_FORMAT_ULONG -> {
                        offset = dataInputStream.readUnsignedInt()
                    }

                    IFD_FORMAT_SLONG, IFD_FORMAT_IFD -> {
                        offset = dataInputStream.readInt().toLong()
                    }

                    else -> {}
                }
                if (DEBUG) {
                    Log.d(TAG, String.format("Offset: %d, tagName: %s", offset, tag!!.name))
                }

                // Check if the next IFD offset
                // 1. Is a non-negative value (within the length of the input, if known), and
                // 2. Does not point to a previously read IFD.
                if (offset > 0L
                    && (dataInputStream.length() == ByteOrderedDataInputStream.LENGTH_UNSET
                            || offset < dataInputStream.length())
                ) {
                    if (!mAttributesOffsets.contains(offset.toInt())) {
                        dataInputStream.seek(offset)
                        readImageFileDirectory(dataInputStream, nextIfdType)
                    } else {
                        if (DEBUG) {
                            Log.d(
                                TAG, ("Skip jump into the IFD since it has already been read: "
                                        + "IfdType " + nextIfdType + " (at " + offset + ")")
                            )
                        }
                    }
                } else {
                    if (DEBUG) {
                        var message =
                            "Skip jump into the IFD since its offset is invalid: $offset"
                        if (dataInputStream.length() != ByteOrderedDataInputStream.LENGTH_UNSET) {
                            message += " (total length: " + dataInputStream.length() + ")"
                        }
                        Log.d(TAG, message)
                    }
                }

                dataInputStream.seek(nextEntryOffset)
                continue
            }

            val bytesOffset = dataInputStream.position() + mOffsetToExifData
            val bytes = ByteArray(byteCount.toInt())
            dataInputStream.readFully(bytes)
            val attribute = ExifAttribute(
                dataFormat, numberOfComponents,
                bytesOffset.toLong(), bytes
            )
            mAttributes[ifdType][tag!!.name] = attribute

            // DNG files have a DNG Version tag specifying the version of specifications that the
            // image file is following.
            // See http://fileformats.archiveteam.org/wiki/DNG
            if (TAG_DNG_VERSION == tag.name) {
                mMimeType = IMAGE_TYPE_DNG
            }

            // PEF files have a Make or Model tag that begins with "PENTAX" or a compression tag
            // that is 65535.
            // See http://fileformats.archiveteam.org/wiki/Pentax_PEF
            if (((TAG_MAKE == tag.name || TAG_MODEL == tag.name)
                        && attribute.getStringValue(mExifByteOrder)!!.contains(PEF_SIGNATURE))
                || (TAG_COMPRESSION == tag.name
                        && attribute.getIntValue(mExifByteOrder) == 65535)
            ) {
                mMimeType = IMAGE_TYPE_PEF
            }

            // Seek to next tag offset
            if (dataInputStream.position().toLong() != nextEntryOffset) {
                dataInputStream.seek(nextEntryOffset)
            }
        }

        val nextIfdOffset = dataInputStream.readInt()
        if (DEBUG) {
            Log.d(TAG, String.format("nextIfdOffset: %d", nextIfdOffset))
        }
        // Check if the next IFD offset
        // 1. Is a non-negative value, and
        // 2. Does not point to a previously read IFD.
        if (nextIfdOffset > 0L) {
            if (!mAttributesOffsets.contains(nextIfdOffset)) {
                dataInputStream.seek(nextIfdOffset.toLong())
                if (mAttributes[IFD_TYPE_THUMBNAIL].isEmpty()) {
                    // Do not overwrite thumbnail IFD data if it already exists.
                    readImageFileDirectory(dataInputStream, IFD_TYPE_THUMBNAIL)
                } else if (mAttributes[IFD_TYPE_PREVIEW].isEmpty()) {
                    readImageFileDirectory(dataInputStream, IFD_TYPE_PREVIEW)
                }
            } else {
                if (DEBUG) {
                    Log.d(
                        TAG, ("Stop reading file since re-reading an IFD may cause an "
                                + "infinite loop: " + nextIfdOffset)
                    )
                }
            }
        } else {
            if (DEBUG) {
                Log.d(
                    TAG, "Stop reading file since a wrong offset may cause an infinite loop: "
                            + nextIfdOffset
                )
            }
        }
    }

    /**
     * JPEG compressed images do not contain IMAGE_LENGTH & IMAGE_WIDTH tags.
     * This value uses JpegInterchangeFormat(JPEG data offset) value, and calls getJpegAttributes()
     * to locate SOF(Start of Frame) marker and update the image length & width values.
     * See JEITA CP-3451C Table 5 and Section 4.8.1. B.
     */
    @Throws(IOException::class)
    private fun retrieveJpegImageSize(`in`: SeekableByteOrderedDataInputStream, imageType: Int) {
        // Check if image already has IMAGE_LENGTH & IMAGE_WIDTH values

        if (mAttributes[imageType][TAG_IMAGE_LENGTH] == null || mAttributes[imageType][TAG_IMAGE_WIDTH] == null) {
            // Find if offset for JPEG data exists
            val jpegInterchangeFormatAttribute =
                mAttributes[imageType][TAG_JPEG_INTERCHANGE_FORMAT]
            if (jpegInterchangeFormatAttribute != null
                && mAttributes[imageType][TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] != null
            ) {
                val jpegInterchangeFormat =
                    jpegInterchangeFormatAttribute.getIntValue(mExifByteOrder)
                val jpegInterchangeFormatLength =
                    jpegInterchangeFormatAttribute.getIntValue(mExifByteOrder)

                // Searches for SOF marker in JPEG data and updates IMAGE_LENGTH & IMAGE_WIDTH tags
                `in`.seek(jpegInterchangeFormat.toLong())
                val jpegBytes = ByteArray(jpegInterchangeFormatLength)
                `in`.readFully(jpegBytes)
                getJpegAttributes(
                    ByteOrderedDataInputStream(jpegBytes), jpegInterchangeFormat,
                    imageType
                )
            }
        }
    }

    // Sets thumbnail offset & length attributes based on JpegInterchangeFormat or StripOffsets tags
    @Throws(IOException::class)
    private fun setThumbnailData(`in`: ByteOrderedDataInputStream) {
        val thumbnailData: HashMap<*, *> = mAttributes[IFD_TYPE_THUMBNAIL]

        val compressionAttribute =
            thumbnailData[TAG_COMPRESSION] as ExifAttribute?
        if (compressionAttribute != null) {
            mThumbnailCompression = compressionAttribute.getIntValue(mExifByteOrder)
            when (mThumbnailCompression) {
                DATA_JPEG -> {
                    handleThumbnailFromJfif(`in`, thumbnailData)
                }

                DATA_UNCOMPRESSED, DATA_JPEG_COMPRESSED -> {
                    if (isSupportedDataType(thumbnailData)) {
                        handleThumbnailFromStrips(`in`, thumbnailData)
                    }
                }
            }
        } else {
            // Thumbnail data may not contain Compression tag value
            mThumbnailCompression = DATA_JPEG
            handleThumbnailFromJfif(`in`, thumbnailData)
        }
    }

    // Check JpegInterchangeFormat(JFIF) tags to retrieve thumbnail offset & length values
    // and reads the corresponding bytes if stream does not support seek function
    @Throws(IOException::class)
    private fun handleThumbnailFromJfif(
        `in`: ByteOrderedDataInputStream,
        thumbnailData: HashMap<*, *>
    ) {
        val jpegInterchangeFormatAttribute =
            thumbnailData[TAG_JPEG_INTERCHANGE_FORMAT] as ExifAttribute?
        val jpegInterchangeFormatLengthAttribute =
            thumbnailData[TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] as ExifAttribute?
        if (jpegInterchangeFormatAttribute != null
            && jpegInterchangeFormatLengthAttribute != null
        ) {
            var thumbnailOffset = jpegInterchangeFormatAttribute.getIntValue(mExifByteOrder)
            val thumbnailLength = jpegInterchangeFormatLengthAttribute.getIntValue(mExifByteOrder)

            if (mMimeType == IMAGE_TYPE_ORF) {
                // Update offset value since RAF files have IFD data preceding MakerNote data.
                thumbnailOffset += mOrfMakerNoteOffset
            }

            if (thumbnailOffset > 0 && thumbnailLength > 0) {
                mHasThumbnail = true
                if (mFilename == null && mAssetInputStream == null && mSeekableFileDescriptor == null) {
                    // TODO: Need to handle potential OutOfMemoryError
                    // Save the thumbnail in memory if the input doesn't support reading again.
                    val thumbnailBytes = ByteArray(thumbnailLength)
                    `in`.skipFully(thumbnailOffset)
                    `in`.readFully(thumbnailBytes)
                    mThumbnailBytes = thumbnailBytes
                }
                mThumbnailOffset = thumbnailOffset
                mThumbnailLength = thumbnailLength
            }
            if (DEBUG) {
                Log.d(
                    TAG, ("Setting thumbnail attributes with offset: " + thumbnailOffset
                            + ", length: " + thumbnailLength)
                )
            }
        }
    }

    // Check StripOffsets & StripByteCounts tags to retrieve thumbnail offset & length values
    @Throws(IOException::class)
    private fun handleThumbnailFromStrips(
        `in`: ByteOrderedDataInputStream,
        thumbnailData: HashMap<*, *>
    ) {
        val stripOffsetsAttribute =
            thumbnailData[TAG_STRIP_OFFSETS] as ExifAttribute?
        val stripByteCountsAttribute =
            thumbnailData[TAG_STRIP_BYTE_COUNTS] as ExifAttribute?

        if (stripOffsetsAttribute != null && stripByteCountsAttribute != null) {
            val stripOffsets =
                ExifInterfaceUtils.convertToLongArray(stripOffsetsAttribute.getValue(mExifByteOrder))
            val stripByteCounts =
                ExifInterfaceUtils.convertToLongArray(
                    stripByteCountsAttribute.getValue(
                        mExifByteOrder
                    )
                )

            if (stripOffsets == null || stripOffsets.size == 0) {
                Log.w(TAG, "stripOffsets should not be null or have zero length.")
                return
            }
            if (stripByteCounts == null || stripByteCounts.size == 0) {
                Log.w(TAG, "stripByteCounts should not be null or have zero length.")
                return
            }
            if (stripOffsets.size != stripByteCounts.size) {
                Log.w(TAG, "stripOffsets and stripByteCounts should have same length.")
                return
            }

            var totalStripByteCount: Long = 0
            for (byteCount in stripByteCounts) {
                totalStripByteCount += byteCount
            }

            // TODO: Need to handle potential OutOfMemoryError
            // Set thumbnail byte array data for non-consecutive strip bytes
            val totalStripBytes = ByteArray(totalStripByteCount.toInt())

            var bytesRead = 0
            var bytesAdded = 0
            mAreThumbnailStripsConsecutive = true
            mHasThumbnailStrips = mAreThumbnailStripsConsecutive
            mHasThumbnail = mHasThumbnailStrips
            for (i in stripOffsets.indices) {
                val stripOffset = stripOffsets[i].toInt()
                val stripByteCount = stripByteCounts[i].toInt()

                // Check if strips are consecutive
                // TODO: Add test for non-consecutive thumbnail image
                if (i < stripOffsets.size - 1
                    && (stripOffset + stripByteCount).toLong() != stripOffsets[i + 1]
                ) {
                    mAreThumbnailStripsConsecutive = false
                }

                // Skip to offset
                val bytesToSkip = stripOffset - bytesRead
                if (bytesToSkip < 0) {
                    Log.d(TAG, "Invalid strip offset value")
                    return
                }
                try {
                    `in`.skipFully(bytesToSkip)
                } catch (e: EOFException) {
                    Log.d(
                        TAG,
                        "Failed to skip $bytesToSkip bytes."
                    )
                    return
                }
                bytesRead += bytesToSkip
                // TODO: Need to handle potential OutOfMemoryError
                val stripBytes = ByteArray(stripByteCount)
                try {
                    `in`.readFully(stripBytes)
                } catch (e: EOFException) {
                    Log.d(
                        TAG,
                        "Failed to read $stripByteCount bytes."
                    )
                    return
                }
                bytesRead += stripByteCount

                // Add bytes to array
                System.arraycopy(
                    stripBytes, 0, totalStripBytes, bytesAdded,
                    stripBytes.size
                )
                bytesAdded += stripBytes.size
            }
            mThumbnailBytes = totalStripBytes

            if (mAreThumbnailStripsConsecutive) {
                mThumbnailOffset = stripOffsets[0].toInt()
                mThumbnailLength = totalStripBytes.size
            }
        }
    }

    // Check if thumbnail data type is currently supported or not
    @Throws(IOException::class)
    private fun isSupportedDataType(thumbnailData: HashMap<*, *>): Boolean {
        val bitsPerSampleAttribute =
            thumbnailData[TAG_BITS_PER_SAMPLE] as ExifAttribute?
        if (bitsPerSampleAttribute != null) {
            val bitsPerSampleValue = bitsPerSampleAttribute.getValue(mExifByteOrder) as IntArray?

            if (BITS_PER_SAMPLE_RGB.contentEquals(bitsPerSampleValue)) {
                return true
            }

            // See DNG Specification 1.4.0.0. Section 3, Compression.
            if (mMimeType == IMAGE_TYPE_DNG) {
                val photometricInterpretationAttribute =
                    thumbnailData[TAG_PHOTOMETRIC_INTERPRETATION] as ExifAttribute?
                if (photometricInterpretationAttribute != null) {
                    val photometricInterpretationValue =
                        photometricInterpretationAttribute.getIntValue(mExifByteOrder)
                    if ((photometricInterpretationValue == PHOTOMETRIC_INTERPRETATION_BLACK_IS_ZERO
                                && bitsPerSampleValue.contentEquals(BITS_PER_SAMPLE_GREYSCALE_2))
                        || ((photometricInterpretationValue == PHOTOMETRIC_INTERPRETATION_YCBCR)
                                && bitsPerSampleValue.contentEquals(BITS_PER_SAMPLE_RGB))
                    ) {
                        return true
                    } else {
                        // TODO: Add support for lossless Huffman JPEG data
                    }
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Unsupported data type value")
        }
        return false
    }

    // Returns true if the image length and width values are <= 512.
    // See Section 4.8 of http://standardsproposals.bsigroup.com/Home/getPDF/567
    @Throws(IOException::class)
    private fun isThumbnail(map: HashMap<*, *>): Boolean {
        val imageLengthAttribute = map[TAG_IMAGE_LENGTH] as ExifAttribute?
        val imageWidthAttribute = map[TAG_IMAGE_WIDTH] as ExifAttribute?

        if (imageLengthAttribute != null && imageWidthAttribute != null) {
            val imageLengthValue = imageLengthAttribute.getIntValue(mExifByteOrder)
            val imageWidthValue = imageWidthAttribute.getIntValue(mExifByteOrder)
            if (imageLengthValue <= MAX_THUMBNAIL_SIZE && imageWidthValue <= MAX_THUMBNAIL_SIZE) {
                return true
            }
        }
        return false
    }

    // Validate primary, preview, thumbnail image data by comparing image size
    @Throws(IOException::class)
    private fun validateImages() {
        // Swap images based on size (primary > preview > thumbnail)
        swapBasedOnImageSize(IFD_TYPE_PRIMARY, IFD_TYPE_PREVIEW)
        swapBasedOnImageSize(IFD_TYPE_PRIMARY, IFD_TYPE_THUMBNAIL)
        swapBasedOnImageSize(IFD_TYPE_PREVIEW, IFD_TYPE_THUMBNAIL)

        // TODO (b/142296453): Revise image width/height setting logic
        // Check if image has PixelXDimension/PixelYDimension tags, which contain valid image
        // sizes, excluding padding at the right end or bottom end of the image to make sure that
        // the values are multiples of 64. See JEITA CP-3451C Table 5 and Section 4.8.1. B.
        val pixelXDimAttribute =
            mAttributes[IFD_TYPE_EXIF][TAG_PIXEL_X_DIMENSION]
        val pixelYDimAttribute =
            mAttributes[IFD_TYPE_EXIF][TAG_PIXEL_Y_DIMENSION]
        if (pixelXDimAttribute != null && pixelYDimAttribute != null) {
            mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_WIDTH] =
                pixelXDimAttribute
            mAttributes[IFD_TYPE_PRIMARY][TAG_IMAGE_LENGTH] =
                pixelYDimAttribute
        }

        // Check whether thumbnail image exists and whether preview image satisfies the thumbnail
        // image requirements
        if (mAttributes[IFD_TYPE_THUMBNAIL].isEmpty()) {
            if (isThumbnail(mAttributes[IFD_TYPE_PREVIEW])) {
                mAttributes[IFD_TYPE_THUMBNAIL] =
                    mAttributes[IFD_TYPE_PREVIEW]
                mAttributes[IFD_TYPE_PREVIEW] = HashMap()
            }
        }

        // Check if the thumbnail image satisfies the thumbnail size requirements
        if (!isThumbnail(mAttributes[IFD_TYPE_THUMBNAIL])) {
            Log.d(TAG, "No image meets the size requirements of a thumbnail image.")
        }

        // TAG_THUMBNAIL_* tags should be replaced with TAG_* equivalents and vice versa if needed.
        replaceInvalidTags(IFD_TYPE_PRIMARY, TAG_THUMBNAIL_ORIENTATION, TAG_ORIENTATION)
        replaceInvalidTags(IFD_TYPE_PRIMARY, TAG_THUMBNAIL_IMAGE_LENGTH, TAG_IMAGE_LENGTH)
        replaceInvalidTags(IFD_TYPE_PRIMARY, TAG_THUMBNAIL_IMAGE_WIDTH, TAG_IMAGE_WIDTH)
        replaceInvalidTags(IFD_TYPE_PREVIEW, TAG_THUMBNAIL_ORIENTATION, TAG_ORIENTATION)
        replaceInvalidTags(IFD_TYPE_PREVIEW, TAG_THUMBNAIL_IMAGE_LENGTH, TAG_IMAGE_LENGTH)
        replaceInvalidTags(IFD_TYPE_PREVIEW, TAG_THUMBNAIL_IMAGE_WIDTH, TAG_IMAGE_WIDTH)
        replaceInvalidTags(IFD_TYPE_THUMBNAIL, TAG_ORIENTATION, TAG_THUMBNAIL_ORIENTATION)
        replaceInvalidTags(IFD_TYPE_THUMBNAIL, TAG_IMAGE_LENGTH, TAG_THUMBNAIL_IMAGE_LENGTH)
        replaceInvalidTags(IFD_TYPE_THUMBNAIL, TAG_IMAGE_WIDTH, TAG_THUMBNAIL_IMAGE_WIDTH)
    }

    /**
     * If image is uncompressed, ImageWidth/Length tags are used to store size info.
     * However, uncompressed images often store extra pixels around the edges of the final image,
     * which results in larger values for TAG_IMAGE_WIDTH and TAG_IMAGE_LENGTH tags.
     * This method corrects those tag values by checking first the values of TAG_DEFAULT_CROP_SIZE
     * See DNG Specification 1.4.0.0. Section 4. (DefaultCropSize)
     *
     *
     * If image is a RW2 file, valid image sizes are stored in SensorBorder tags.
     * See tiff_parser.cc GetFullDimension32()
     */
    @Throws(IOException::class)
    private fun updateImageSizeValues(`in`: SeekableByteOrderedDataInputStream, imageType: Int) {
        // Uncompressed image valid image size values
        val defaultCropSizeAttribute =
            mAttributes[imageType][TAG_DEFAULT_CROP_SIZE]
        // RW2 image valid image size values
        val topBorderAttribute =
            mAttributes[imageType][TAG_RW2_SENSOR_TOP_BORDER]
        val leftBorderAttribute =
            mAttributes[imageType][TAG_RW2_SENSOR_LEFT_BORDER]
        val bottomBorderAttribute =
            mAttributes[imageType][TAG_RW2_SENSOR_BOTTOM_BORDER]
        val rightBorderAttribute =
            mAttributes[imageType][TAG_RW2_SENSOR_RIGHT_BORDER]

        if (defaultCropSizeAttribute != null) {
            // Update for uncompressed image
            val defaultCropSizeXAttribute: ExifAttribute
            val defaultCropSizeYAttribute: ExifAttribute
            if (defaultCropSizeAttribute.format == IFD_FORMAT_URATIONAL) {
                val defaultCropSizeValue =
                    defaultCropSizeAttribute.getValue(mExifByteOrder) as Array<Rational>?
                if (defaultCropSizeValue == null || defaultCropSizeValue.size != 2) {
                    Log.w(
                        TAG, "Invalid crop size values. cropSize="
                                + defaultCropSizeValue.contentToString()
                    )
                    return
                }
                defaultCropSizeXAttribute =
                    ExifAttribute.createURational(
                        defaultCropSizeValue[0], mExifByteOrder
                    )
                defaultCropSizeYAttribute =
                    ExifAttribute.createURational(
                        defaultCropSizeValue[1], mExifByteOrder
                    )
            } else {
                val defaultCropSizeValue =
                    defaultCropSizeAttribute.getValue(mExifByteOrder) as IntArray?
                if (defaultCropSizeValue == null || defaultCropSizeValue.size != 2) {
                    Log.w(
                        TAG, "Invalid crop size values. cropSize="
                                + defaultCropSizeValue.contentToString()
                    )
                    return
                }
                defaultCropSizeXAttribute =
                    ExifAttribute.createUShort(
                        defaultCropSizeValue[0], mExifByteOrder
                    )
                defaultCropSizeYAttribute =
                    ExifAttribute.createUShort(
                        defaultCropSizeValue[1], mExifByteOrder
                    )
            }
            mAttributes[imageType][TAG_IMAGE_WIDTH] =
                defaultCropSizeXAttribute
            mAttributes[imageType][TAG_IMAGE_LENGTH] =
                defaultCropSizeYAttribute
        } else if (topBorderAttribute != null && leftBorderAttribute != null && bottomBorderAttribute != null && rightBorderAttribute != null) {
            // Update for RW2 image
            val topBorderValue = topBorderAttribute.getIntValue(mExifByteOrder)
            val bottomBorderValue = bottomBorderAttribute.getIntValue(mExifByteOrder)
            val rightBorderValue = rightBorderAttribute.getIntValue(mExifByteOrder)
            val leftBorderValue = leftBorderAttribute.getIntValue(mExifByteOrder)
            if (bottomBorderValue > topBorderValue && rightBorderValue > leftBorderValue) {
                val length = bottomBorderValue - topBorderValue
                val width = rightBorderValue - leftBorderValue
                val imageLengthAttribute =
                    ExifAttribute.createUShort(length, mExifByteOrder)
                val imageWidthAttribute =
                    ExifAttribute.createUShort(width, mExifByteOrder)
                mAttributes[imageType][TAG_IMAGE_LENGTH] =
                    imageLengthAttribute
                mAttributes[imageType][TAG_IMAGE_WIDTH] =
                    imageWidthAttribute
            }
        } else {
            retrieveJpegImageSize(`in`, imageType)
        }
    }

    // Writes an Exif segment into the given output stream.
    @Throws(IOException::class)
    private fun writeExifSegment(dataOutputStream: ByteOrderedDataOutputStream): Int {
        // The following variables are for calculating each IFD tag group size in bytes.
        val ifdOffsets = IntArray(EXIF_TAGS.size)
        val ifdDataSizes = IntArray(EXIF_TAGS.size)

        // Remove IFD pointer tags (we'll re-add it later.)
        for (tag in EXIF_POINTER_TAGS) {
            removeAttribute(tag.name)
        }
        // Remove old thumbnail data
        if (mHasThumbnail) {
            if (mHasThumbnailStrips) {
                removeAttribute(TAG_STRIP_OFFSETS)
                removeAttribute(TAG_STRIP_BYTE_COUNTS)
            } else {
                removeAttribute(TAG_JPEG_INTERCHANGE_FORMAT)
                removeAttribute(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH)
            }
        }

        // Remove null value tags.
        for (ifdType in EXIF_TAGS.indices) {
            for (obj in mAttributes[ifdType].entries.toTypedArray()) {
                val entry = obj as Map.Entry<*, *>
                if (entry.value == null) {
                    mAttributes[ifdType].remove(entry.key)
                }
            }
        }

        // Add IFD pointer tags. The next offset of primary image TIFF IFD will have thumbnail IFD
        // offset when there is one or more tags in the thumbnail IFD.
        if (!mAttributes[IFD_TYPE_EXIF].isEmpty()) {
            mAttributes[IFD_TYPE_PRIMARY][EXIF_POINTER_TAGS[1].name] =
                ExifAttribute.createULong(
                    0,
                    mExifByteOrder
                )
        }
        if (!mAttributes[IFD_TYPE_GPS].isEmpty()) {
            mAttributes[IFD_TYPE_PRIMARY][EXIF_POINTER_TAGS[2].name] =
                ExifAttribute.createULong(
                    0,
                    mExifByteOrder
                )
        }
        if (!mAttributes[IFD_TYPE_INTEROPERABILITY].isEmpty()) {
            mAttributes[IFD_TYPE_EXIF][EXIF_POINTER_TAGS[3].name] =
                ExifAttribute.createULong(
                    0,
                    mExifByteOrder
                )
        }
        if (mHasThumbnail) {
            if (mHasThumbnailStrips) {
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_STRIP_OFFSETS] =
                    ExifAttribute.createUShort(
                        0,
                        mExifByteOrder
                    )
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_STRIP_BYTE_COUNTS] =
                    ExifAttribute.createUShort(
                        mThumbnailLength,
                        mExifByteOrder
                    )
            } else {
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_JPEG_INTERCHANGE_FORMAT] =
                    ExifAttribute.createULong(
                        0,
                        mExifByteOrder
                    )
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_JPEG_INTERCHANGE_FORMAT_LENGTH] =
                    ExifAttribute.createULong(
                        mThumbnailLength.toLong(),
                        mExifByteOrder
                    )
            }
        }

        // Calculate IFD group data area sizes. IFD group data area is assigned to save the entry
        // value which has a bigger size than 4 bytes.
        for (i in EXIF_TAGS.indices) {
            var sum = 0
            for ((_, exifAttribute) in mAttributes[i]) {
                val size = exifAttribute!!.size()
                if (size > 4) {
                    sum += size
                }
            }
            ifdDataSizes[i] += sum
        }

        // Calculate IFD offsets.
        // 8 bytes are for TIFF headers: 2 bytes (byte order) + 2 bytes (identifier) + 4 bytes
        // (offset of IFDs)
        var position = 8
        for (ifdType in EXIF_TAGS.indices) {
            if (!mAttributes[ifdType].isEmpty()) {
                ifdOffsets[ifdType] = position
                position += 2 + mAttributes[ifdType].size * 12 + 4 + ifdDataSizes[ifdType]
            }
        }
        if (mHasThumbnail) {
            val thumbnailOffset = position
            if (mHasThumbnailStrips) {
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_STRIP_OFFSETS] =
                    ExifAttribute.createUShort(
                        thumbnailOffset,
                        mExifByteOrder
                    )
            } else {
                mAttributes[IFD_TYPE_THUMBNAIL][TAG_JPEG_INTERCHANGE_FORMAT] =
                    ExifAttribute.createULong(
                        thumbnailOffset.toLong(),
                        mExifByteOrder
                    )
            }
            mThumbnailOffset = thumbnailOffset
            position += mThumbnailLength
        }

        var totalSize = position
        if (mMimeType == IMAGE_TYPE_JPEG) {
            // Add 8 bytes for APP1 size and identifier data
            totalSize += 8
        }
        if (DEBUG) {
            for (i in EXIF_TAGS.indices) {
                Log.d(
                    TAG, String.format(
                        "index: %d, offsets: %d, tag count: %d, data sizes: %d, "
                                + "total size: %d", i, ifdOffsets[i], mAttributes[i].size,
                        ifdDataSizes[i], totalSize
                    )
                )
            }
        }

        // Update IFD pointer tags with the calculated offsets.
        if (!mAttributes[IFD_TYPE_EXIF].isEmpty()) {
            mAttributes[IFD_TYPE_PRIMARY][EXIF_POINTER_TAGS[1].name] = ExifAttribute.createULong(
                ifdOffsets[IFD_TYPE_EXIF]
                    .toLong(),
                mExifByteOrder
            )
        }
        if (!mAttributes[IFD_TYPE_GPS].isEmpty()) {
            mAttributes[IFD_TYPE_PRIMARY][EXIF_POINTER_TAGS[2].name] = ExifAttribute.createULong(
                ifdOffsets[IFD_TYPE_GPS]
                    .toLong(),
                mExifByteOrder
            )
        }
        if (!mAttributes[IFD_TYPE_INTEROPERABILITY].isEmpty()) {
            mAttributes[IFD_TYPE_EXIF][EXIF_POINTER_TAGS[3].name] = ExifAttribute.createULong(
                ifdOffsets[IFD_TYPE_INTEROPERABILITY]
                    .toLong(), mExifByteOrder
            )
        }

        when (mMimeType) {
            IMAGE_TYPE_JPEG -> {
                check(totalSize <= 0xFFFF) {
                    ("Size of exif data (" + totalSize + " bytes) exceeds the max size of a "
                            + "JPEG APP1 segment (65536 bytes)")
                }
                // Write JPEG specific data (APP1 size, APP1 identifier)
                dataOutputStream.writeUnsignedShort(totalSize)
                dataOutputStream.write(IDENTIFIER_EXIF_APP1)
            }

            IMAGE_TYPE_PNG -> {
                // Write PNG specific data (chunk size, chunk type)
                dataOutputStream.writeInt(totalSize)
                dataOutputStream.write(PNG_CHUNK_TYPE_EXIF)
            }

            IMAGE_TYPE_WEBP -> {
                // Write WebP specific data (chunk type, chunk size)
                dataOutputStream.write(WEBP_CHUNK_TYPE_EXIF)
                dataOutputStream.writeInt(totalSize)
            }
        }

        // Write TIFF Headers. See JEITA CP-3451C Section 4.5.2. Table 1.
        dataOutputStream.writeShort(if (mExifByteOrder == ByteOrder.BIG_ENDIAN) BYTE_ALIGN_MM else BYTE_ALIGN_II)
        dataOutputStream.setByteOrder(mExifByteOrder)
        dataOutputStream.writeUnsignedShort(START_CODE.toInt())
        dataOutputStream.writeUnsignedInt(IFD_OFFSET.toLong())

        // Write IFD groups. See JEITA CP-3451C Section 4.5.8. Figure 9.
        for (ifdType in EXIF_TAGS.indices) {
            if (!mAttributes[ifdType].isEmpty()) {
                // See JEITA CP-3451C Section 4.6.2: IFD structure.
                // Write entry count
                dataOutputStream.writeUnsignedShort(mAttributes[ifdType].size)

                // Write entry info
                var dataOffset = ifdOffsets[ifdType] + 2 + mAttributes[ifdType].size * 12 + 4
                for ((key, attribute) in mAttributes[ifdType]) {
                    // Convert tag name to tag number.
                    val tag = sExifTagMapsForWriting[ifdType][key]
                    val tagNumber = tag!!.number
                    val size = attribute!!.size()

                    dataOutputStream.writeUnsignedShort(tagNumber)
                    dataOutputStream.writeUnsignedShort(attribute.format)
                    dataOutputStream.writeInt(attribute.numberOfComponents)
                    if (size > 4) {
                        dataOutputStream.writeUnsignedInt(dataOffset.toLong())
                        dataOffset += size
                    } else {
                        dataOutputStream.write(attribute.bytes)
                        // Fill zero up to 4 bytes
                        if (size < 4) {
                            for (i in size..3) {
                                dataOutputStream.writeByte(0)
                            }
                        }
                    }
                }

                // Write the next offset. It writes the offset of thumbnail IFD if there is one or
                // more tags in the thumbnail IFD when the current IFD is the primary image TIFF
                // IFD; Otherwise 0.
                if (ifdType == 0 && !mAttributes[IFD_TYPE_THUMBNAIL].isEmpty()) {
                    dataOutputStream.writeUnsignedInt(ifdOffsets[IFD_TYPE_THUMBNAIL].toLong())
                } else {
                    dataOutputStream.writeUnsignedInt(0)
                }

                // Write values of data field exceeding 4 bytes after the next offset.
                for ((_, attribute) in mAttributes[ifdType]) {
                    if (attribute!!.bytes.size > 4) {
                        dataOutputStream.write(attribute.bytes, 0, attribute.bytes.size)
                    }
                }
            }
        }

        // Write thumbnail
        if (mHasThumbnail) {
            dataOutputStream.write(thumbnailBytes!!)
        }

        // For WebP files, add a single padding byte at end if chunk size is odd
        if (mMimeType == IMAGE_TYPE_WEBP && totalSize % 2 == 1) {
            dataOutputStream.writeByte(0)
        }

        // Reset the byte order to big endian in order to write remaining parts of the JPEG file.
        dataOutputStream.setByteOrder(ByteOrder.BIG_ENDIAN)

        return totalSize
    }

    // An input stream class that can parse both little and big endian order data and also
    // supports seeking to any position in the stream via mark/reset.
    private class SeekableByteOrderedDataInputStream : ByteOrderedDataInputStream {
        constructor(bytes: ByteArray) : super(bytes) {
            // No need to check if mark is supported here since ByteOrderedDataInputStream will
            // create a ByteArrayInputStream, which supports mark by default.
            mDataInputStream.mark(Int.MAX_VALUE)
        }

        /**
         * Given input stream should support mark/reset, and should be set to the beginning of
         * the stream.
         */
        constructor(`in`: InputStream) : super(`in`) {
            require(`in`.markSupported()) {
                ("Cannot create "
                        + "SeekableByteOrderedDataInputStream with stream that does not support "
                        + "mark/reset")
            }
            // Mark given InputStream to the maximum value (we can't know the length of the
            // stream for certain) so that InputStream.reset() may be called at any point in the
            // stream to reset the stream to an earlier position.
            mDataInputStream.mark(Int.MAX_VALUE)
        }

        /**
         * Seek to the given absolute position in the stream (i.e. the number of bytes from the
         * beginning of the stream).
         */
        @Throws(IOException::class)
        fun seek(position: Long) {
            var position = position
            if (mPosition > position) {
                mPosition = 0
                mDataInputStream.reset()
            } else {
                position -= mPosition.toLong()
            }
            skipFully(position.toInt())
        }
    }

    // An input stream class that can parse both little and big endian order data.
    private open class ByteOrderedDataInputStream @JvmOverloads constructor(
        `in`: InputStream?,
        byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
    ) :
        InputStream(), DataInput {
        protected val mDataInputStream: DataInputStream = DataInputStream(`in`)
        protected var mPosition: Int

        private var mByteOrder: ByteOrder
        private var mSkipBuffer: ByteArray? = null
        private var mLength: Int

        constructor(bytes: ByteArray) : this(ByteArrayInputStream(bytes), ByteOrder.BIG_ENDIAN) {
            this.mLength = bytes.size
        }

        init {
            mDataInputStream.mark(0)
            mPosition = 0
            mByteOrder = byteOrder
            this.mLength = if (`in` is ByteOrderedDataInputStream)
                `in`.length()
            else
                LENGTH_UNSET
        }

        fun setByteOrder(byteOrder: ByteOrder) {
            mByteOrder = byteOrder
        }

        fun position(): Int {
            return mPosition
        }

        @Throws(IOException::class)
        override fun available(): Int {
            return mDataInputStream.available()
        }

        @Throws(IOException::class)
        override fun read(): Int {
            ++mPosition
            return mDataInputStream.read()
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = mDataInputStream.read(b, off, len)
            mPosition += bytesRead
            return bytesRead
        }

        @Throws(IOException::class)
        override fun readUnsignedByte(): Int {
            ++mPosition
            return mDataInputStream.readUnsignedByte()
        }

        @Throws(IOException::class)
        override fun readLine(): String? {
            Log.d(TAG, "Currently unsupported")
            return null
        }

        @Throws(IOException::class)
        override fun readBoolean(): Boolean {
            ++mPosition
            return mDataInputStream.readBoolean()
        }

        @Throws(IOException::class)
        override fun readChar(): Char {
            mPosition += 2
            return mDataInputStream.readChar()
        }

        @Throws(IOException::class)
        override fun readUTF(): String {
            mPosition += 2
            return mDataInputStream.readUTF()
        }

        @Throws(IOException::class)
        override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
            mPosition += length
            mDataInputStream.readFully(buffer, offset, length)
        }

        @Throws(IOException::class)
        override fun readFully(buffer: ByteArray) {
            mPosition += buffer.size
            mDataInputStream.readFully(buffer)
        }

        @Throws(IOException::class)
        override fun readByte(): Byte {
            ++mPosition
            val ch = mDataInputStream.read()
            if (ch < 0) {
                throw EOFException()
            }
            return ch.toByte()
        }

        @Throws(IOException::class)
        override fun readShort(): Short {
            mPosition += 2
            val ch1 = mDataInputStream.read()
            val ch2 = mDataInputStream.read()
            if ((ch1 or ch2) < 0) {
                throw EOFException()
            }
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                return ((ch2 shl 8) + ch1).toShort()
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                return ((ch1 shl 8) + ch2).toShort()
            }
            throw IOException("Invalid byte order: $mByteOrder")
        }

        @Throws(IOException::class)
        override fun readInt(): Int {
            mPosition += 4
            val ch1 = mDataInputStream.read()
            val ch2 = mDataInputStream.read()
            val ch3 = mDataInputStream.read()
            val ch4 = mDataInputStream.read()
            if ((ch1 or ch2 or ch3 or ch4) < 0) {
                throw EOFException()
            }
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                return ((ch4 shl 24) + (ch3 shl 16) + (ch2 shl 8) + ch1)
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                return ((ch1 shl 24) + (ch2 shl 16) + (ch3 shl 8) + ch4)
            }
            throw IOException("Invalid byte order: $mByteOrder")
        }

        @Throws(IOException::class)
        override fun skipBytes(n: Int): Int {
            throw UnsupportedOperationException("skipBytes is currently unsupported")
        }

        /**
         * Discards n bytes of data from the input stream. This method will block until either
         * the full amount has been skipped or the end of the stream is reached, whichever happens
         * first.
         */
        @Throws(IOException::class)
        fun skipFully(n: Int) {
            var totalSkipped = 0
            while (totalSkipped < n) {
                var skipped = mDataInputStream.skip((n - totalSkipped).toLong()).toInt()
                if (skipped <= 0) {
                    if (mSkipBuffer == null) {
                        mSkipBuffer = ByteArray(SKIP_BUFFER_SIZE)
                    }
                    val bytesToSkip = min(SKIP_BUFFER_SIZE, n - totalSkipped)
                    if ((mDataInputStream.read(mSkipBuffer, 0, bytesToSkip)
                            .also { skipped = it }) == -1
                    ) {
                        throw EOFException("Reached EOF while skipping $n bytes.")
                    }
                }
                totalSkipped += skipped
            }
            mPosition += totalSkipped
        }

        @Throws(IOException::class)
        override fun readUnsignedShort(): Int {
            mPosition += 2
            val ch1 = mDataInputStream.read()
            val ch2 = mDataInputStream.read()
            if ((ch1 or ch2) < 0) {
                throw EOFException()
            }
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                return ((ch2 shl 8) + ch1)
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                return ((ch1 shl 8) + ch2)
            }
            throw IOException("Invalid byte order: $mByteOrder")
        }

        @Throws(IOException::class)
        fun readUnsignedInt(): Long {
            return readInt().toLong() and 0xffffffffL
        }

        @Throws(IOException::class)
        override fun readLong(): Long {
            mPosition += 8
            val ch1 = mDataInputStream.read()
            val ch2 = mDataInputStream.read()
            val ch3 = mDataInputStream.read()
            val ch4 = mDataInputStream.read()
            val ch5 = mDataInputStream.read()
            val ch6 = mDataInputStream.read()
            val ch7 = mDataInputStream.read()
            val ch8 = mDataInputStream.read()
            if ((ch1 or ch2 or ch3 or ch4 or ch5 or ch6 or ch7 or ch8) < 0) {
                throw EOFException()
            }
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                return (((ch8.toLong() shl 56) + (ch7.toLong() shl 48) + (ch6.toLong() shl 40)
                        + (ch5.toLong() shl 32) + (ch4.toLong() shl 24) + (ch3.toLong() shl 16)
                        + (ch2.toLong() shl 8) + ch1.toLong()))
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                return (((ch1.toLong() shl 56) + (ch2.toLong() shl 48) + (ch3.toLong() shl 40)
                        + (ch4.toLong() shl 32) + (ch5.toLong() shl 24) + (ch6.toLong() shl 16)
                        + (ch7.toLong() shl 8) + ch8.toLong()))
            }
            throw IOException("Invalid byte order: $mByteOrder")
        }

        @Throws(IOException::class)
        override fun readFloat(): Float {
            return java.lang.Float.intBitsToFloat(readInt())
        }

        @Throws(IOException::class)
        override fun readDouble(): Double {
            return java.lang.Double.longBitsToDouble(readLong())
        }

        override fun mark(readlimit: Int) {
            throw UnsupportedOperationException("Mark is currently unsupported")
        }

        override fun reset() {
            throw UnsupportedOperationException("Reset is currently unsupported")
        }

        /**
         * Return the total length (in bytes) of the underlying stream if known, otherwise
         * [.LENGTH_UNSET].
         */
        fun length(): Int {
            return mLength
        }

        companion object {
            const val LENGTH_UNSET: Int = -1
        }
    }

    // An output stream to write EXIF data area, which can be written in either little or big endian
    // order.
    private class ByteOrderedDataOutputStream(
        val mOutputStream: OutputStream,
        private var mByteOrder: ByteOrder
    ) :
        FilterOutputStream(mOutputStream) {
        fun setByteOrder(byteOrder: ByteOrder) {
            mByteOrder = byteOrder
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray) {
            mOutputStream.write(bytes)
        }

        @Throws(IOException::class)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            mOutputStream.write(bytes, offset, length)
        }

        @Throws(IOException::class)
        fun writeByte(`val`: Int) {
            mOutputStream.write(`val`)
        }

        @Throws(IOException::class)
        fun writeShort(`val`: Short) {
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                mOutputStream.write((`val`.toInt() ushr 0) and 0xFF)
                mOutputStream.write((`val`.toInt() ushr 8) and 0xFF)
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                mOutputStream.write((`val`.toInt() ushr 8) and 0xFF)
                mOutputStream.write((`val`.toInt() ushr 0) and 0xFF)
            }
        }

        @Throws(IOException::class)
        fun writeInt(`val`: Int) {
            if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
                mOutputStream.write((`val` ushr 0) and 0xFF)
                mOutputStream.write((`val` ushr 8) and 0xFF)
                mOutputStream.write((`val` ushr 16) and 0xFF)
                mOutputStream.write((`val` ushr 24) and 0xFF)
            } else if (mByteOrder == ByteOrder.BIG_ENDIAN) {
                mOutputStream.write((`val` ushr 24) and 0xFF)
                mOutputStream.write((`val` ushr 16) and 0xFF)
                mOutputStream.write((`val` ushr 8) and 0xFF)
                mOutputStream.write((`val` ushr 0) and 0xFF)
            }
        }

        @Throws(IOException::class)
        fun writeUnsignedShort(`val`: Int) {
            require(`val` <= 0xFFFF) {
                ("val is larger than the maximum value of a "
                        + "16-bit unsigned integer")
            }
            writeShort(`val`.toShort())
        }

        @Throws(IOException::class)
        fun writeUnsignedInt(`val`: Long) {
            require(`val` <= 0xFFFFFFFFL) {
                ("val is larger than the maximum value of a "
                        + "32-bit unsigned integer")
            }
            writeInt(`val`.toInt())
        }
    }

    // Swaps image data based on image size
    @Throws(IOException::class)
    private fun swapBasedOnImageSize(@IfdType firstIfdType: Int, @IfdType secondIfdType: Int) {
        if (mAttributes[firstIfdType].isEmpty() || mAttributes[secondIfdType].isEmpty()) {
            if (DEBUG) {
                Log.d(TAG, "Cannot perform swap since only one image data exists")
            }
            return
        }

        val firstImageLengthAttribute =
            mAttributes[firstIfdType][TAG_IMAGE_LENGTH]
        val firstImageWidthAttribute =
            mAttributes[firstIfdType][TAG_IMAGE_WIDTH]
        val secondImageLengthAttribute =
            mAttributes[secondIfdType][TAG_IMAGE_LENGTH]
        val secondImageWidthAttribute =
            mAttributes[secondIfdType][TAG_IMAGE_WIDTH]

        if (firstImageLengthAttribute == null || firstImageWidthAttribute == null) {
            if (DEBUG) {
                Log.d(TAG, "First image does not contain valid size information")
            }
        } else if (secondImageLengthAttribute == null || secondImageWidthAttribute == null) {
            if (DEBUG) {
                Log.d(TAG, "Second image does not contain valid size information")
            }
        } else {
            val firstImageLengthValue = firstImageLengthAttribute.getIntValue(mExifByteOrder)
            val firstImageWidthValue = firstImageWidthAttribute.getIntValue(mExifByteOrder)
            val secondImageLengthValue = secondImageLengthAttribute.getIntValue(mExifByteOrder)
            val secondImageWidthValue = secondImageWidthAttribute.getIntValue(mExifByteOrder)

            if (firstImageLengthValue < secondImageLengthValue &&
                firstImageWidthValue < secondImageWidthValue
            ) {
                val tempMap = mAttributes[firstIfdType]
                mAttributes[firstIfdType] = mAttributes[secondIfdType]
                mAttributes[secondIfdType] = tempMap
            }
        }
    }

    private fun replaceInvalidTags(@IfdType ifdType: Int, invalidTag: String, validTag: String) {
        if (!mAttributes[ifdType].isEmpty()) {
            if (mAttributes[ifdType][invalidTag] != null) {
                mAttributes[ifdType][validTag] = mAttributes[ifdType][invalidTag]
                mAttributes[ifdType].remove(invalidTag)
            }
        }
    }

    companion object {
        private const val TAG = "ExifInterface"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        // The Exif tag names. See JEITA CP-3451C specifications (Exif 2.3) Section 3-8.
        // A. Tags related to image data structure
        /**
         *
         * The number of columns of image data, equal to the number of pixels per row. In JPEG
         * compressed data, this tag shall not be used because a JPEG marker is used instead of it.
         *
         *
         *  * Tag = 256
         *  * Type = Unsigned short or Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_IMAGE_WIDTH: String = "ImageWidth"

        /**
         *
         * The number of rows of image data. In JPEG compressed data, this tag shall not be used
         * because a JPEG marker is used instead of it.
         *
         *
         *  * Tag = 257
         *  * Type = Unsigned short or Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_IMAGE_LENGTH: String = "ImageLength"

        /**
         *
         * The number of bits per image component. In this standard each component of the image is
         * 8 bits, so the value for this tag is 8. See also [.TAG_SAMPLES_PER_PIXEL]. In JPEG
         * compressed data, this tag shall not be used because a JPEG marker is used instead of it.
         *
         *
         *  * Tag = 258
         *  * Type = Unsigned short
         *  * Count = 3
         *  * Default = [.BITS_PER_SAMPLE_RGB]
         *
         */
        const val TAG_BITS_PER_SAMPLE: String = "BitsPerSample"

        /**
         *
         * The compression scheme used for the image data. When a primary image is JPEG compressed,
         * this designation is not necessary. So, this tag shall not be recorded. When thumbnails use
         * JPEG compression, this tag value is set to 6.
         *
         *
         *  * Tag = 259
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .DATA_UNCOMPRESSED
         *
         * @see .DATA_JPEG
         */
        const val TAG_COMPRESSION: String = "Compression"

        /**
         *
         * The pixel composition. In JPEG compressed data, this tag shall not be used because a JPEG
         * marker is used instead of it.
         *
         *
         *  * Tag = 262
         *  * Type = SHORT
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .PHOTOMETRIC_INTERPRETATION_RGB
         *
         * @see .PHOTOMETRIC_INTERPRETATION_YCBCR
         */
        const val TAG_PHOTOMETRIC_INTERPRETATION: String = "PhotometricInterpretation"

        /**
         *
         * The image orientation viewed in terms of rows and columns.
         *
         *
         *  * Tag = 274
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.ORIENTATION_NORMAL]
         *
         *
         *
         *
         * @see .ORIENTATION_UNDEFINED
         *
         * @see .ORIENTATION_NORMAL
         *
         * @see .ORIENTATION_FLIP_HORIZONTAL
         *
         * @see .ORIENTATION_ROTATE_180
         *
         * @see .ORIENTATION_FLIP_VERTICAL
         *
         * @see .ORIENTATION_TRANSPOSE
         *
         * @see .ORIENTATION_ROTATE_90
         *
         * @see .ORIENTATION_TRANSVERSE
         *
         * @see .ORIENTATION_ROTATE_270
         */
        const val TAG_ORIENTATION: String = "Orientation"

        /**
         *
         * The number of components per pixel. Since this standard applies to RGB and YCbCr images,
         * the value set for this tag is 3. In JPEG compressed data, this tag shall not be used because
         * a JPEG marker is used instead of it.
         *
         *
         *  * Tag = 277
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = 3
         *
         */
        const val TAG_SAMPLES_PER_PIXEL: String = "SamplesPerPixel"

        /**
         *
         * Indicates whether pixel components are recorded in chunky or planar format. In JPEG
         * compressed data, this tag shall not be used because a JPEG marker is used instead of it.
         * If this field does not exist, the TIFF default, [.FORMAT_CHUNKY], is assumed.
         *
         *
         *  * Tag = 284
         *  * Type = Unsigned short
         *  * Count = 1
         *
         *
         *
         *
         * @see .FORMAT_CHUNKY
         *
         * @see .FORMAT_PLANAR
         */
        const val TAG_PLANAR_CONFIGURATION: String = "PlanarConfiguration"

        /**
         *
         * The sampling ratio of chrominance components in relation to the luminance component.
         * In JPEG compressed data a JPEG marker is used instead of this tag. So, this tag shall not
         * be recorded.
         *
         *
         *  * Tag = 530
         *  * Type = Unsigned short
         *  * Count = 2
         *
         *  * [2, 1] = YCbCr4:2:2
         *  * [2, 2] = YCbCr4:2:0
         *  * Other = reserved
         *
         *
         */
        const val TAG_Y_CB_CR_SUB_SAMPLING: String = "YCbCrSubSampling"

        /**
         *
         * The position of chrominance components in relation to the luminance component. This field
         * is designated only for JPEG compressed data or uncompressed YCbCr data. The TIFF default is
         * [.Y_CB_CR_POSITIONING_CENTERED]; but when Y:Cb:Cr = 4:2:2 it is recommended in this
         * standard that [.Y_CB_CR_POSITIONING_CO_SITED] be used to record data, in order to
         * improve the image quality when viewed on TV systems. When this field does not exist,
         * the reader shall assume the TIFF default. In the case of Y:Cb:Cr = 4:2:0, the TIFF default
         * ([.Y_CB_CR_POSITIONING_CENTERED]) is recommended. If the Exif/DCF reader does not
         * have the capability of supporting both kinds of positioning, it shall follow the TIFF
         * default regardless of the value in this field. It is preferable that readers can support
         * both centered and co-sited positioning.
         *
         *
         *  * Tag = 531
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.Y_CB_CR_POSITIONING_CENTERED]
         *
         *
         *
         *
         * @see .Y_CB_CR_POSITIONING_CENTERED
         *
         * @see .Y_CB_CR_POSITIONING_CO_SITED
         */
        const val TAG_Y_CB_CR_POSITIONING: String = "YCbCrPositioning"

        /**
         *
         * The number of pixels per [.TAG_RESOLUTION_UNIT] in the [.TAG_IMAGE_WIDTH]
         * direction. When the image resolution is unknown, 72 [dpi] shall be designated.
         *
         *
         *  * Tag = 282
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = 72
         *
         *
         *
         *
         * @see .TAG_Y_RESOLUTION
         *
         * @see .TAG_RESOLUTION_UNIT
         */
        const val TAG_X_RESOLUTION: String = "XResolution"

        /**
         *
         * The number of pixels per [.TAG_RESOLUTION_UNIT] in the [.TAG_IMAGE_WIDTH]
         * direction. The same value as [.TAG_X_RESOLUTION] shall be designated.
         *
         *
         *  * Tag = 283
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = 72
         *
         *
         *
         *
         * @see .TAG_X_RESOLUTION
         *
         * @see .TAG_RESOLUTION_UNIT
         */
        const val TAG_Y_RESOLUTION: String = "YResolution"

        /**
         *
         * The unit for measuring [.TAG_X_RESOLUTION] and [.TAG_Y_RESOLUTION]. The same
         * unit is used for both [.TAG_X_RESOLUTION] and [.TAG_Y_RESOLUTION]. If the image
         * resolution is unknown, [.RESOLUTION_UNIT_INCHES] shall be designated.
         *
         *
         *  * Tag = 296
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.RESOLUTION_UNIT_INCHES]
         *
         *
         *
         *
         * @see .RESOLUTION_UNIT_INCHES
         *
         * @see .RESOLUTION_UNIT_CENTIMETERS
         *
         * @see .TAG_X_RESOLUTION
         *
         * @see .TAG_Y_RESOLUTION
         */
        const val TAG_RESOLUTION_UNIT: String = "ResolutionUnit"

        // B. Tags related to recording offset
        /**
         *
         * For each strip, the byte offset of that strip. It is recommended that this be selected
         * so the number of strip bytes does not exceed 64 KBytes.In the case of JPEG compressed data,
         * this designation is not necessary. So, this tag shall not be recorded.
         *
         *
         *  * Tag = 273
         *  * Type = Unsigned short or Unsigned long
         *  * Count = StripsPerImage (for [.FORMAT_CHUNKY])
         * or [.TAG_SAMPLES_PER_PIXEL] * StripsPerImage
         * (for [.FORMAT_PLANAR])
         *  * Default = None
         *
         *
         *
         * StripsPerImage = floor(([.TAG_IMAGE_LENGTH] + [.TAG_ROWS_PER_STRIP] - 1)
         * / [.TAG_ROWS_PER_STRIP])
         *
         *
         *
         * @see .TAG_ROWS_PER_STRIP
         *
         * @see .TAG_STRIP_BYTE_COUNTS
         */
        const val TAG_STRIP_OFFSETS: String = "StripOffsets"

        /**
         *
         * The number of rows per strip. This is the number of rows in the image of one strip when
         * an image is divided into strips. In the case of JPEG compressed data, this designation is
         * not necessary. So, this tag shall not be recorded.
         *
         *
         *  * Tag = 278
         *  * Type = Unsigned short or Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .TAG_STRIP_OFFSETS
         *
         * @see .TAG_STRIP_BYTE_COUNTS
         */
        const val TAG_ROWS_PER_STRIP: String = "RowsPerStrip"

        /**
         *
         * The total number of bytes in each strip. In the case of JPEG compressed data, this
         * designation is not necessary. So, this tag shall not be recorded.
         *
         *
         *  * Tag = 279
         *  * Type = Unsigned short or Unsigned long
         *  * Count = StripsPerImage (when using [.FORMAT_CHUNKY])
         * or [.TAG_SAMPLES_PER_PIXEL] * StripsPerImage
         * (when using [.FORMAT_PLANAR])
         *  * Default = None
         *
         *
         *
         * StripsPerImage = floor(([.TAG_IMAGE_LENGTH] + [.TAG_ROWS_PER_STRIP] - 1)
         * / [.TAG_ROWS_PER_STRIP])
         */
        const val TAG_STRIP_BYTE_COUNTS: String = "StripByteCounts"

        /**
         *
         * The offset to the start byte (SOI) of JPEG compressed thumbnail data. This shall not be
         * used for primary image JPEG data.
         *
         *
         *  * Tag = 513
         *  * Type = Unsigned long
         *  * Default = None
         *
         */
        const val TAG_JPEG_INTERCHANGE_FORMAT: String = "JPEGInterchangeFormat"

        /**
         *
         * The number of bytes of JPEG compressed thumbnail data. This is not used for primary image
         * JPEG data. JPEG thumbnails are not divided but are recorded as a continuous JPEG bitstream
         * from SOI to EOI. APPn and COM markers should not be recorded. Compressed thumbnails shall be
         * recorded in no more than 64 KBytes, including all other data to be recorded in APP1.
         *
         *
         *  * Tag = 514
         *  * Type = Unsigned long
         *  * Default = None
         *
         */
        const val TAG_JPEG_INTERCHANGE_FORMAT_LENGTH: String = "JPEGInterchangeFormatLength"

        // C. Tags related to Image Data Characteristics
        /**
         *
         * A transfer function for the image, described in tabular style. Normally this tag need not
         * be used, since color space is specified in [.TAG_COLOR_SPACE].
         *
         *
         *  * Tag = 301
         *  * Type = Unsigned short
         *  * Count = 3 * 256
         *  * Default = None
         *
         */
        const val TAG_TRANSFER_FUNCTION: String = "TransferFunction"

        /**
         *
         * The chromaticity of the white point of the image. Normally this tag need not be used,
         * since color space is specified in [.TAG_COLOR_SPACE].
         *
         *
         *  * Tag = 318
         *  * Type = Unsigned rational
         *  * Count = 2
         *  * Default = None
         *
         */
        const val TAG_WHITE_POINT: String = "WhitePoint"

        /**
         *
         * The chromaticity of the three primary colors of the image. Normally this tag need not
         * be used, since color space is specified in [.TAG_COLOR_SPACE].
         *
         *
         *  * Tag = 319
         *  * Type = Unsigned rational
         *  * Count = 6
         *  * Default = None
         *
         */
        const val TAG_PRIMARY_CHROMATICITIES: String = "PrimaryChromaticities"

        /**
         *
         * The matrix coefficients for transformation from RGB to YCbCr image data. About
         * the default value, please refer to JEITA CP-3451C Spec, Annex D.
         *
         *
         *  * Tag = 529
         *  * Type = Unsigned rational
         *  * Count = 3
         *
         */
        const val TAG_Y_CB_CR_COEFFICIENTS: String = "YCbCrCoefficients"

        /**
         *
         * The reference black point value and reference white point value. No defaults are given
         * in TIFF, but the values below are given as defaults here. The color space is declared in
         * a color space information tag, with the default being the value that gives the optimal image
         * characteristics Interoperability these conditions
         *
         *
         *  * Tag = 532
         *  * Type = RATIONAL
         *  * Count = 6
         *  * Default = [0, 255, 0, 255, 0, 255] (when [.TAG_PHOTOMETRIC_INTERPRETATION]
         * is [.PHOTOMETRIC_INTERPRETATION_RGB])
         * or [0, 255, 0, 128, 0, 128] (when [.TAG_PHOTOMETRIC_INTERPRETATION]
         * is [.PHOTOMETRIC_INTERPRETATION_YCBCR])
         *
         */
        const val TAG_REFERENCE_BLACK_WHITE: String = "ReferenceBlackWhite"

        // D. Other tags
        /**
         *
         * The date and time of image creation. In this standard it is the date and time the file
         * was changed. The format is "YYYY:MM:DD HH:MM:SS" with time shown in 24-hour format, and
         * the date and time separated by one blank character (`0x20`). When the date and time
         * are unknown, all the character spaces except colons (":") should be filled with blank
         * characters, or else the Interoperability field should be filled with blank characters.
         * The character string length is 20 Bytes including NULL for termination. When the field is
         * left blank, it is treated as unknown.
         *
         *
         *  * Tag = 306
         *  * Type = String
         *  * Length = 19
         *  * Default = None
         *
         *
         *
         * Note: The format "YYYY-MM-DD HH:MM:SS" is also supported for reading. For writing,
         * however, calling [.setAttribute] with the "YYYY-MM-DD HH:MM:SS"
         * format will automatically convert it to the primary format, "YYYY:MM:DD HH:MM:SS".
         */
        const val TAG_DATETIME: String = "DateTime"

        /**
         *
         * An ASCII string giving the title of the image. It is possible to be added a comment
         * such as "1988 company picnic" or the like. Two-byte character codes cannot be used. When
         * a 2-byte code is necessary, [.TAG_USER_COMMENT] is to be used.
         *
         *
         *  * Tag = 270
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_IMAGE_DESCRIPTION: String = "ImageDescription"

        /**
         *
         * The manufacturer of the recording equipment. This is the manufacturer of the DSC,
         * scanner, video digitizer or other equipment that generated the image. When the field is left
         * blank, it is treated as unknown.
         *
         *
         *  * Tag = 271
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_MAKE: String = "Make"

        /**
         *
         * The model name or model number of the equipment. This is the model name of number of
         * the DSC, scanner, video digitizer or other equipment that generated the image. When
         * the field is left blank, it is treated as unknown.
         *
         *
         *  * Tag = 272
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_MODEL: String = "Model"

        /**
         *
         * This tag records the name and version of the software or firmware of the camera or image
         * input device used to generate the image. The detailed format is not specified, but it is
         * recommended that the example shown below be followed. When the field is left blank, it is
         * treated as unknown.
         *
         *
         * Ex.) "Exif Software Version 1.00a".
         *
         *
         *  * Tag = 305
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_SOFTWARE: String = "Software"

        /**
         *
         * This tag records the name of the camera owner, photographer or image creator.
         * The detailed format is not specified, but it is recommended that the information be written
         * as in the example below for ease of Interoperability. When the field is left blank, it is
         * treated as unknown.
         *
         *
         * Ex.) "Camera owner, John Smith; Photographer, Michael Brown; Image creator,
         * Ken James"
         *
         *
         *  * Tag = 315
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_ARTIST: String = "Artist"

        /**
         *
         * Copyright information. In this standard the tag is used to indicate both the photographer
         * and editor copyrights. It is the copyright notice of the person or organization claiming
         * rights to the image. The Interoperability copyright statement including date and rights
         * should be written in this field; e.g., "Copyright, John Smith, 19xx. All rights reserved."
         * In this standard the field records both the photographer and editor copyrights, with each
         * recorded in a separate part of the statement. When there is a clear distinction between
         * the photographer and editor copyrights, these are to be written in the order of photographer
         * followed by editor copyright, separated by NULL (in this case, since the statement also ends
         * with a NULL, there are two NULL codes) (see example 1). When only the photographer copyright
         * is given, it is terminated by one NULL code (see example 2). When only the editor copyright
         * is given, the photographer copyright part consists of one space followed by a terminating
         * NULL code, then the editor copyright is given (see example 3). When the field is left blank,
         * it is treated as unknown.
         *
         *
         * Ex. 1) When both the photographer copyright and editor copyright are given.
         *  * Photographer copyright + NULL + editor copyright + NULL
         *
         * Ex. 2) When only the photographer copyright is given.
         *  * Photographer copyright + NULL
         *
         * Ex. 3) When only the editor copyright is given.
         *  * Space (`0x20`) + NULL + editor copyright + NULL
         *
         *
         *  * Tag = 315
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_COPYRIGHT: String = "Copyright"

        // Exif IFD Attribute Information
        // A. Tags related to version
        /**
         *
         * The version of this standard supported. Nonexistence of this field is taken to mean
         * nonconformance to the standard. In according with conformance to this standard, this tag
         * shall be recorded like "0230” as 4-byte ASCII.
         *
         *
         *  * Tag = 36864
         *  * Type = Undefined
         *  * Length = 4
         *  * Default = "0230"
         *
         */
        const val TAG_EXIF_VERSION: String = "ExifVersion"

        /**
         *
         * The Flashpix format version supported by a FPXR file. If the FPXR function supports
         * Flashpix format Ver. 1.0, this is indicated similarly to [.TAG_EXIF_VERSION] by
         * recording "0100" as 4-byte ASCII.
         *
         *
         *  * Tag = 40960
         *  * Type = Undefined
         *  * Length = 4
         *  * Default = "0100"
         *
         */
        const val TAG_FLASHPIX_VERSION: String = "FlashpixVersion"

        // B. Tags related to image data characteristics
        /**
         *
         * The color space information tag is always recorded as the color space specifier.
         * Normally [.COLOR_SPACE_S_RGB] is used to define the color space based on the PC
         * monitor conditions and environment. If a color space other than [.COLOR_SPACE_S_RGB]
         * is used, [.COLOR_SPACE_UNCALIBRATED] is set. Image data recorded as
         * [.COLOR_SPACE_UNCALIBRATED] may be treated as [.COLOR_SPACE_S_RGB] when it is
         * converted to Flashpix.
         *
         *
         *  * Tag = 40961
         *  * Type = Unsigned short
         *  * Count = 1
         *
         *
         *
         *
         * @see .COLOR_SPACE_S_RGB
         *
         * @see .COLOR_SPACE_UNCALIBRATED
         */
        const val TAG_COLOR_SPACE: String = "ColorSpace"

        /**
         *
         * Indicates the value of coefficient gamma. The formula of transfer function used for image
         * reproduction is expressed as follows.
         *
         *
         * (Reproduced value) = (Input value) ^ gamma
         *
         *
         * Both reproduced value and input value indicate normalized value, whose minimum value is
         * 0 and maximum value is 1.
         *
         *
         *  * Tag = 42240
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GAMMA: String = "Gamma"

        // C. Tags related to image configuration
        /**
         *
         * Information specific to compressed data. When a compressed file is recorded, the valid
         * width of the meaningful image shall be recorded in this tag, whether or not there is padding
         * data or a restart marker. This tag shall not exist in an uncompressed file.
         *
         *
         *  * Tag = 40962
         *  * Type = Unsigned short or Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_PIXEL_X_DIMENSION: String = "PixelXDimension"

        /**
         *
         * Information specific to compressed data. When a compressed file is recorded, the valid
         * height of the meaningful image shall be recorded in this tag, whether or not there is
         * padding data or a restart marker. This tag shall not exist in an uncompressed file.
         * Since data padding is unnecessary in the vertical direction, the number of lines recorded
         * in this valid image height tag will in fact be the same as that recorded in the SOF.
         *
         *
         *  * Tag = 40963
         *  * Type = Unsigned short or Unsigned long
         *  * Count = 1
         *
         */
        const val TAG_PIXEL_Y_DIMENSION: String = "PixelYDimension"

        /**
         *
         * Information specific to compressed data. The channels of each component are arranged
         * in order from the 1st component to the 4th. For uncompressed data the data arrangement is
         * given in the [.TAG_PHOTOMETRIC_INTERPRETATION]. However, since
         * [.TAG_PHOTOMETRIC_INTERPRETATION] can only express the order of Y, Cb and Cr, this tag
         * is provided for cases when compressed data uses components other than Y, Cb, and Cr and to
         * enable support of other sequences.
         *
         *
         *  * Tag = 37121
         *  * Type = Undefined
         *  * Length = 4
         *  * Default = 4 5 6 0 (if RGB uncompressed) or 1 2 3 0 (other cases)
         *
         *  * 0 = does not exist
         *  * 1 = Y
         *  * 2 = Cb
         *  * 3 = Cr
         *  * 4 = R
         *  * 5 = G
         *  * 6 = B
         *  * other = reserved
         *
         *
         */
        const val TAG_COMPONENTS_CONFIGURATION: String = "ComponentsConfiguration"

        /**
         *
         * Information specific to compressed data. The compression mode used for a compressed image
         * is indicated in unit bits per pixel.
         *
         *
         *  * Tag = 37122
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_COMPRESSED_BITS_PER_PIXEL: String = "CompressedBitsPerPixel"

        // D. Tags related to user information
        /**
         *
         * A tag for manufacturers of Exif/DCF writers to record any desired information.
         * The contents are up to the manufacturer, but this tag shall not be used for any other than
         * its intended purpose.
         *
         *
         *  * Tag = 37500
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_MAKER_NOTE: String = "MakerNote"

        /**
         *
         * A tag for Exif users to write keywords or comments on the image besides those in
         * [.TAG_IMAGE_DESCRIPTION], and without the character code limitations of it.
         *
         *
         *  * Tag = 37510
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_USER_COMMENT: String = "UserComment"

        // E. Tags related to related file information
        /**
         *
         * This tag is used to record the name of an audio file related to the image data. The only
         * relational information recorded here is the Exif audio file name and extension (an ASCII
         * string consisting of 8 characters + '.' + 3 characters). The path is not recorded.
         *
         *
         * When using this tag, audio files shall be recorded in conformance to the Exif audio
         * format. Writers can also store the data such as Audio within APP2 as Flashpix extension
         * stream data. Audio files shall be recorded in conformance to the Exif audio format.
         *
         *
         *  * Tag = 40964
         *  * Type = String
         *  * Length = 12
         *  * Default = None
         *
         */
        const val TAG_RELATED_SOUND_FILE: String = "RelatedSoundFile"

        // F. Tags related to date and time
        /**
         *
         * The date and time when the original image data was generated. For a DSC the date and time
         * the picture was taken are recorded. The format is "YYYY:MM:DD HH:MM:SS" with time shown in
         * 24-hour format, and the date and time separated by one blank character (`0x20`).
         * When the date and time are unknown, all the character spaces except colons (":") should be
         * filled with blank characters, or else the Interoperability field should be filled with blank
         * characters. When the field is left blank, it is treated as unknown.
         *
         *
         *  * Tag = 36867
         *  * Type = String
         *  * Length = 19
         *  * Default = None
         *
         *
         *
         * Note: The format "YYYY-MM-DD HH:MM:SS" is also supported for reading. For writing,
         * however, calling [.setAttribute] with the "YYYY-MM-DD HH:MM:SS"
         * format will automatically convert it to the primary format, "YYYY:MM:DD HH:MM:SS".
         */
        const val TAG_DATETIME_ORIGINAL: String = "DateTimeOriginal"

        /**
         *
         * The date and time when the image was stored as digital data. If, for example, an image
         * was captured by DSC and at the same time the file was recorded, then
         * [.TAG_DATETIME_ORIGINAL] and this tag will have the same contents. The format is
         * "YYYY:MM:DD HH:MM:SS" with time shown in 24-hour format, and the date and time separated by
         * one blank character (`0x20`). When the date and time are unknown, all the character
         * spaces except colons (":")should be filled with blank characters, or else
         * the Interoperability field should be filled with blank characters. When the field is left
         * blank, it is treated as unknown.
         *
         *
         *  * Tag = 36868
         *  * Type = String
         *  * Length = 19
         *  * Default = None
         *
         *
         *
         * Note: The format "YYYY-MM-DD HH:MM:SS" is also supported for reading. For writing,
         * however, calling [.setAttribute] with the "YYYY-MM-DD HH:MM:SS"
         * format will automatically convert it to the primary format, "YYYY:MM:DD HH:MM:SS".
         */
        const val TAG_DATETIME_DIGITIZED: String = "DateTimeDigitized"

        /**
         *
         * A tag used to record the offset from UTC (the time difference from Universal Time
         * Coordinated including daylight saving time) of the time of DateTime tag. The format when
         * recording the offset is "±HH:MM". The part of "±" shall be recorded as "+" or "-". When
         * the offsets are unknown, all the character spaces except colons (":") should be filled
         * with blank characters, or else the Interoperability field should be filled with blank
         * characters. The character string length is 7 Bytes including NULL for termination. When
         * the field is left blank, it is treated as unknown.
         *
         *
         *  * Tag = 36880
         *  * Type = String
         *  * Length = 7
         *  * Default = None
         *
         */
        const val TAG_OFFSET_TIME: String = "OffsetTime"

        /**
         *
         * A tag used to record the offset from UTC (the time difference from Universal Time
         * Coordinated including daylight saving time) of the time of DateTimeOriginal tag. The format
         * when recording the offset is "±HH:MM". The part of "±" shall be recorded as "+" or "-". When
         * the offsets are unknown, all the character spaces except colons (":") should be filled
         * with blank characters, or else the Interoperability field should be filled with blank
         * characters. The character string length is 7 Bytes including NULL for termination. When
         * the field is left blank, it is treated as unknown.
         *
         *
         *  * Tag = 36881
         *  * Type = String
         *  * Length = 7
         *  * Default = None
         *
         */
        const val TAG_OFFSET_TIME_ORIGINAL: String = "OffsetTimeOriginal"

        /**
         *
         * A tag used to record the offset from UTC (the time difference from Universal Time
         * Coordinated including daylight saving time) of the time of DateTimeDigitized tag. The format
         * when recording the offset is "±HH:MM". The part of "±" shall be recorded as "+" or "-". When
         * the offsets are unknown, all the character spaces except colons (":") should be filled
         * with blank characters, or else the Interoperability field should be filled with blank
         * characters. The character string length is 7 Bytes including NULL for termination. When
         * the field is left blank, it is treated as unknown.
         *
         *
         *  * Tag = 36882
         *  * Type = String
         *  * Length = 7
         *  * Default = None
         *
         */
        const val TAG_OFFSET_TIME_DIGITIZED: String = "OffsetTimeDigitized"

        /**
         *
         * A tag used to record fractions of seconds for [.TAG_DATETIME].
         *
         *
         *  * Tag = 37520
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_SUBSEC_TIME: String = "SubSecTime"

        /**
         *
         * A tag used to record fractions of seconds for [.TAG_DATETIME_ORIGINAL].
         *
         *
         *  * Tag = 37521
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_SUBSEC_TIME_ORIGINAL: String = "SubSecTimeOriginal"

        /**
         *
         * A tag used to record fractions of seconds for [.TAG_DATETIME_DIGITIZED].
         *
         *
         *  * Tag = 37522
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_SUBSEC_TIME_DIGITIZED: String = "SubSecTimeDigitized"

        // G. Tags related to picture-taking condition
        /**
         *
         * Exposure time, given in seconds.
         *
         *
         *  * Tag = 33434
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_EXPOSURE_TIME: String = "ExposureTime"

        /**
         *
         * The F number.
         *
         *
         *  * Tag = 33437
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_F_NUMBER: String = "FNumber"

        /**
         *
         * TThe class of the program used by the camera to set exposure when the picture is taken.
         * The tag values are as follows.
         *
         *
         *  * Tag = 34850
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.EXPOSURE_PROGRAM_NOT_DEFINED]
         *
         *
         *
         *
         * @see .EXPOSURE_PROGRAM_NOT_DEFINED
         *
         * @see .EXPOSURE_PROGRAM_MANUAL
         *
         * @see .EXPOSURE_PROGRAM_NORMAL
         *
         * @see .EXPOSURE_PROGRAM_APERTURE_PRIORITY
         *
         * @see .EXPOSURE_PROGRAM_SHUTTER_PRIORITY
         *
         * @see .EXPOSURE_PROGRAM_CREATIVE
         *
         * @see .EXPOSURE_PROGRAM_ACTION
         *
         * @see .EXPOSURE_PROGRAM_PORTRAIT_MODE
         *
         * @see .EXPOSURE_PROGRAM_LANDSCAPE_MODE
         */
        const val TAG_EXPOSURE_PROGRAM: String = "ExposureProgram"

        /**
         *
         * Indicates the spectral sensitivity of each channel of the camera used. The tag value is
         * an ASCII string compatible with the standard developed by the ASTM Technical committee.
         *
         *
         *  * Tag = 34852
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_SPECTRAL_SENSITIVITY: String = "SpectralSensitivity"

        /**
         * @see .TAG_PHOTOGRAPHIC_SENSITIVITY
         *
         */
        @Deprecated("Use {@link #TAG_PHOTOGRAPHIC_SENSITIVITY} instead.")
        const val TAG_ISO_SPEED_RATINGS: String = "ISOSpeedRatings"

        /**
         *
         * This tag indicates the sensitivity of the camera or input device when the image was shot.
         * More specifically, it indicates one of the following values that are parameters defined in
         * ISO 12232: standard output sensitivity (SOS), recommended exposure index (REI), or ISO
         * speed. Accordingly, if a tag corresponding to a parameter that is designated by
         * [.TAG_SENSITIVITY_TYPE] is recorded, the values of the tag and of this tag are
         * the same. However, if the value is 65535 or higher, the value of this tag shall be 65535.
         * When recording this tag, [.TAG_SENSITIVITY_TYPE] should also be recorded. In addition,
         * while “Count = Any”, only 1 count should be used when recording this tag.
         *
         *
         *  * Tag = 34855
         *  * Type = Unsigned short
         *  * Count = Any
         *  * Default = None
         *
         */
        const val TAG_PHOTOGRAPHIC_SENSITIVITY: String = "PhotographicSensitivity"

        /**
         *
         * Indicates the Opto-Electric Conversion Function (OECF) specified in ISO 14524. OECF is
         * the relationship between the camera optical input and the image values.
         *
         *
         *  * Tag = 34856
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_OECF: String = "OECF"

        /**
         *
         * This tag indicates which one of the parameters of ISO12232 is
         * [.TAG_PHOTOGRAPHIC_SENSITIVITY]. Although it is an optional tag, it should be recorded
         * when [.TAG_PHOTOGRAPHIC_SENSITIVITY] is recorded.
         *
         *
         *  * Tag = 34864
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .SENSITIVITY_TYPE_UNKNOWN
         *
         * @see .SENSITIVITY_TYPE_SOS
         *
         * @see .SENSITIVITY_TYPE_REI
         *
         * @see .SENSITIVITY_TYPE_ISO_SPEED
         *
         * @see .SENSITIVITY_TYPE_SOS_AND_REI
         *
         * @see .SENSITIVITY_TYPE_SOS_AND_ISO
         *
         * @see .SENSITIVITY_TYPE_REI_AND_ISO
         *
         * @see .SENSITIVITY_TYPE_SOS_AND_REI_AND_ISO
         */
        const val TAG_SENSITIVITY_TYPE: String = "SensitivityType"

        /**
         *
         * This tag indicates the standard output sensitivity value of a camera or input device
         * defined in ISO 12232. When recording this tag, [.TAG_PHOTOGRAPHIC_SENSITIVITY] and
         * [.TAG_SENSITIVITY_TYPE] shall also be recorded.
         *
         *
         *  * Tag = 34865
         *  * Type = Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_STANDARD_OUTPUT_SENSITIVITY: String = "StandardOutputSensitivity"

        /**
         *
         * This tag indicates the recommended exposure index value of a camera or input device
         * defined in ISO 12232. When recording this tag, [.TAG_PHOTOGRAPHIC_SENSITIVITY] and
         * [.TAG_SENSITIVITY_TYPE] shall also be recorded.
         *
         *
         *  * Tag = 34866
         *  * Type = Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_RECOMMENDED_EXPOSURE_INDEX: String = "RecommendedExposureIndex"

        /**
         *
         * This tag indicates the ISO speed value of a camera or input device that is defined in
         * ISO 12232. When recording this tag, [.TAG_PHOTOGRAPHIC_SENSITIVITY] and
         * [.TAG_SENSITIVITY_TYPE] shall also be recorded.
         *
         *
         *  * Tag = 34867
         *  * Type = Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_ISO_SPEED: String = "ISOSpeed"

        /**
         *
         * This tag indicates the ISO speed latitude yyy value of a camera or input device that is
         * defined in ISO 12232. However, this tag shall not be recorded without [.TAG_ISO_SPEED]
         * and [.TAG_ISO_SPEED_LATITUDE_ZZZ].
         *
         *
         *  * Tag = 34868
         *  * Type = Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_ISO_SPEED_LATITUDE_YYY: String = "ISOSpeedLatitudeyyy"

        /**
         *
         * This tag indicates the ISO speed latitude zzz value of a camera or input device that is
         * defined in ISO 12232. However, this tag shall not be recorded without [.TAG_ISO_SPEED]
         * and [.TAG_ISO_SPEED_LATITUDE_YYY].
         *
         *
         *  * Tag = 34869
         *  * Type = Unsigned long
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_ISO_SPEED_LATITUDE_ZZZ: String = "ISOSpeedLatitudezzz"

        /**
         *
         * Shutter speed. The unit is the APEX setting.
         *
         *
         *  * Tag = 37377
         *  * Type = Signed rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_SHUTTER_SPEED_VALUE: String = "ShutterSpeedValue"

        /**
         *
         * The lens aperture. The unit is the APEX value.
         *
         *
         *  * Tag = 37378
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_APERTURE_VALUE: String = "ApertureValue"

        /**
         *
         * The value of brightness. The unit is the APEX value. Ordinarily it is given in the range
         * of -99.99 to 99.99. Note that if the numerator of the recorded value is 0xFFFFFFFF,
         * Unknown shall be indicated.
         *
         *
         *  * Tag = 37379
         *  * Type = Signed rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_BRIGHTNESS_VALUE: String = "BrightnessValue"

        /**
         *
         * The exposure bias. The unit is the APEX value. Ordinarily it is given in the range of
         * -99.99 to 99.99.
         *
         *
         *  * Tag = 37380
         *  * Type = Signed rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_EXPOSURE_BIAS_VALUE: String = "ExposureBiasValue"

        /**
         *
         * The smallest F number of the lens. The unit is the APEX value. Ordinarily it is given
         * in the range of 00.00 to 99.99, but it is not limited to this range.
         *
         *
         *  * Tag = 37381
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_MAX_APERTURE_VALUE: String = "MaxApertureValue"

        /**
         *
         * The distance to the subject, given in meters. Note that if the numerator of the recorded
         * value is 0xFFFFFFFF, Infinity shall be indicated; and if the numerator is 0, Distance
         * unknown shall be indicated.
         *
         *
         *  * Tag = 37382
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_SUBJECT_DISTANCE: String = "SubjectDistance"

        /**
         *
         * The metering mode.
         *
         *
         *  * Tag = 37383
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.METERING_MODE_UNKNOWN]
         *
         *
         *
         *
         * @see .METERING_MODE_UNKNOWN
         *
         * @see .METERING_MODE_AVERAGE
         *
         * @see .METERING_MODE_CENTER_WEIGHT_AVERAGE
         *
         * @see .METERING_MODE_SPOT
         *
         * @see .METERING_MODE_MULTI_SPOT
         *
         * @see .METERING_MODE_PATTERN
         *
         * @see .METERING_MODE_PARTIAL
         *
         * @see .METERING_MODE_OTHER
         */
        const val TAG_METERING_MODE: String = "MeteringMode"

        /**
         *
         * The kind of light source.
         *
         *
         *  * Tag = 37384
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.LIGHT_SOURCE_UNKNOWN]
         *
         *
         *
         *
         * @see .LIGHT_SOURCE_UNKNOWN
         *
         * @see .LIGHT_SOURCE_DAYLIGHT
         *
         * @see .LIGHT_SOURCE_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_TUNGSTEN
         *
         * @see .LIGHT_SOURCE_FLASH
         *
         * @see .LIGHT_SOURCE_FINE_WEATHER
         *
         * @see .LIGHT_SOURCE_CLOUDY_WEATHER
         *
         * @see .LIGHT_SOURCE_SHADE
         *
         * @see .LIGHT_SOURCE_DAYLIGHT_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_DAY_WHITE_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_COOL_WHITE_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_WHITE_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_WARM_WHITE_FLUORESCENT
         *
         * @see .LIGHT_SOURCE_STANDARD_LIGHT_A
         *
         * @see .LIGHT_SOURCE_STANDARD_LIGHT_B
         *
         * @see .LIGHT_SOURCE_STANDARD_LIGHT_C
         *
         * @see .LIGHT_SOURCE_D55
         *
         * @see .LIGHT_SOURCE_D65
         *
         * @see .LIGHT_SOURCE_D75
         *
         * @see .LIGHT_SOURCE_D50
         *
         * @see .LIGHT_SOURCE_ISO_STUDIO_TUNGSTEN
         *
         * @see .LIGHT_SOURCE_OTHER
         */
        const val TAG_LIGHT_SOURCE: String = "LightSource"

        /**
         *
         * This tag indicates the status of flash when the image was shot. Bit 0 indicates the flash
         * firing status, bits 1 and 2 indicate the flash return status, bits 3 and 4 indicate
         * the flash mode, bit 5 indicates whether the flash function is present, and bit 6 indicates
         * "red eye" mode.
         *
         *
         *  * Tag = 37385
         *  * Type = Unsigned short
         *  * Count = 1
         *
         *
         *
         *
         * @see .FLAG_FLASH_FIRED
         *
         * @see .FLAG_FLASH_RETURN_LIGHT_NOT_DETECTED
         *
         * @see .FLAG_FLASH_RETURN_LIGHT_DETECTED
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_FIRING
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION
         *
         * @see .FLAG_FLASH_MODE_AUTO
         *
         * @see .FLAG_FLASH_NO_FLASH_FUNCTION
         *
         * @see .FLAG_FLASH_RED_EYE_SUPPORTED
         */
        const val TAG_FLASH: String = "Flash"

        /**
         *
         * This tag indicates the location and area of the main subject in the overall scene.
         *
         *
         *  * Tag = 37396
         *  * Type = Unsigned short
         *  * Count = 2 or 3 or 4
         *  * Default = None
         *
         *
         *
         * The subject location and area are defined by Count values as follows.
         *
         *
         *  * Count = 2 Indicates the location of the main subject as coordinates. The first value
         * is the X coordinate and the second is the Y coordinate.
         *  * Count = 3 The area of the main subject is given as a circle. The circular area is
         * expressed as center coordinates and diameter. The first value is
         * the center X coordinate, the second is the center Y coordinate, and
         * the third is the diameter.
         *  * Count = 4 The area of the main subject is given as a rectangle. The rectangular
         * area is expressed as center coordinates and area dimensions. The first
         * value is the center X coordinate, the second is the center Y coordinate,
         * the third is the width of the area, and the fourth is the height of
         * the area.
         *
         *
         *
         * Note that the coordinate values, width, and height are expressed in relation to the upper
         * left as origin, prior to rotation processing as per [.TAG_ORIENTATION].
         */
        const val TAG_SUBJECT_AREA: String = "SubjectArea"

        /**
         *
         * The actual focal length of the lens, in mm. Conversion is not made to the focal length
         * of a 35mm film camera.
         *
         *
         *  * Tag = 37386
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_FOCAL_LENGTH: String = "FocalLength"

        /**
         *
         * Indicates the strobe energy at the time the image is captured, as measured in Beam Candle
         * Power Seconds (BCPS).
         *
         *
         *  * Tag = 41483
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_FLASH_ENERGY: String = "FlashEnergy"

        /**
         *
         * This tag records the camera or input device spatial frequency table and SFR values in
         * the direction of image width, image height, and diagonal direction, as specified in
         * ISO 12233.
         *
         *
         *  * Tag = 41484
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_SPATIAL_FREQUENCY_RESPONSE: String = "SpatialFrequencyResponse"

        /**
         *
         * Indicates the number of pixels in the image width (X) direction per
         * [.TAG_FOCAL_PLANE_RESOLUTION_UNIT] on the camera focal plane.
         *
         *
         *  * Tag = 41486
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_FOCAL_PLANE_X_RESOLUTION: String = "FocalPlaneXResolution"

        /**
         *
         * Indicates the number of pixels in the image height (Y) direction per
         * [.TAG_FOCAL_PLANE_RESOLUTION_UNIT] on the camera focal plane.
         *
         *
         *  * Tag = 41487
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_FOCAL_PLANE_Y_RESOLUTION: String = "FocalPlaneYResolution"

        /**
         *
         * Indicates the unit for measuring [.TAG_FOCAL_PLANE_X_RESOLUTION] and
         * [.TAG_FOCAL_PLANE_Y_RESOLUTION]. This value is the same as
         * [.TAG_RESOLUTION_UNIT].
         *
         *
         *  * Tag = 41488
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.RESOLUTION_UNIT_INCHES]
         *
         *
         *
         *
         * @see .TAG_RESOLUTION_UNIT
         *
         * @see .RESOLUTION_UNIT_INCHES
         *
         * @see .RESOLUTION_UNIT_CENTIMETERS
         */
        const val TAG_FOCAL_PLANE_RESOLUTION_UNIT: String = "FocalPlaneResolutionUnit"

        /**
         *
         * Indicates the location of the main subject in the scene. The value of this tag represents
         * the pixel at the center of the main subject relative to the left edge, prior to rotation
         * processing as per [.TAG_ORIENTATION]. The first value indicates the X column number
         * and second indicates the Y row number. When a camera records the main subject location,
         * it is recommended that [.TAG_SUBJECT_AREA] be used instead of this tag.
         *
         *
         *  * Tag = 41492
         *  * Type = Unsigned short
         *  * Count = 2
         *  * Default = None
         *
         */
        const val TAG_SUBJECT_LOCATION: String = "SubjectLocation"

        /**
         *
         * Indicates the exposure index selected on the camera or input device at the time the image
         * is captured.
         *
         *
         *  * Tag = 41493
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_EXPOSURE_INDEX: String = "ExposureIndex"

        /**
         *
         * Indicates the image sensor type on the camera or input device.
         *
         *
         *  * Tag = 41495
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .SENSOR_TYPE_NOT_DEFINED
         *
         * @see .SENSOR_TYPE_ONE_CHIP
         *
         * @see .SENSOR_TYPE_TWO_CHIP
         *
         * @see .SENSOR_TYPE_THREE_CHIP
         *
         * @see .SENSOR_TYPE_COLOR_SEQUENTIAL
         *
         * @see .SENSOR_TYPE_TRILINEAR
         *
         * @see .SENSOR_TYPE_COLOR_SEQUENTIAL_LINEAR
         */
        const val TAG_SENSING_METHOD: String = "SensingMethod"

        /**
         *
         * Indicates the image source. If a DSC recorded the image, this tag value always shall
         * be set to [.FILE_SOURCE_DSC].
         *
         *
         *  * Tag = 41728
         *  * Type = Undefined
         *  * Length = 1
         *  * Default = [.FILE_SOURCE_DSC]
         *
         *
         *
         *
         * @see .FILE_SOURCE_OTHER
         *
         * @see .FILE_SOURCE_TRANSPARENT_SCANNER
         *
         * @see .FILE_SOURCE_REFLEX_SCANNER
         *
         * @see .FILE_SOURCE_DSC
         */
        const val TAG_FILE_SOURCE: String = "FileSource"

        /**
         *
         * Indicates the type of scene. If a DSC recorded the image, this tag value shall always
         * be set to [.SCENE_TYPE_DIRECTLY_PHOTOGRAPHED].
         *
         *
         *  * Tag = 41729
         *  * Type = Undefined
         *  * Length = 1
         *  * Default = 1
         *
         *
         *
         *
         * @see .SCENE_TYPE_DIRECTLY_PHOTOGRAPHED
         */
        const val TAG_SCENE_TYPE: String = "SceneType"

        /**
         *
         * Indicates the color filter array (CFA) geometric pattern of the image sensor when
         * a one-chip color area sensor is used. It does not apply to all sensing methods.
         *
         *
         *  * Tag = 41730
         *  * Type = Undefined
         *  * Default = None
         *
         *
         *
         *
         * @see .TAG_SENSING_METHOD
         *
         * @see .SENSOR_TYPE_ONE_CHIP
         */
        const val TAG_CFA_PATTERN: String = "CFAPattern"

        /**
         *
         * This tag indicates the use of special processing on image data, such as rendering geared
         * to output. When special processing is performed, the Exif/DCF reader is expected to disable
         * or minimize any further processing.
         *
         *
         *  * Tag = 41985
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.RENDERED_PROCESS_NORMAL]
         *
         *
         *
         *
         * @see .RENDERED_PROCESS_NORMAL
         *
         * @see .RENDERED_PROCESS_CUSTOM
         */
        const val TAG_CUSTOM_RENDERED: String = "CustomRendered"

        /**
         *
         * This tag indicates the exposure mode set when the image was shot.
         * In [.EXPOSURE_MODE_AUTO_BRACKET], the camera shoots a series of frames of the same
         * scene at different exposure settings.
         *
         *
         *  * Tag = 41986
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .EXPOSURE_MODE_AUTO
         *
         * @see .EXPOSURE_MODE_MANUAL
         *
         * @see .EXPOSURE_MODE_AUTO_BRACKET
         */
        const val TAG_EXPOSURE_MODE: String = "ExposureMode"

        /**
         *
         * This tag indicates the white balance mode set when the image was shot.
         *
         *
         *  * Tag = 41987
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .WHITEBALANCE_AUTO
         *
         * @see .WHITEBALANCE_MANUAL
         */
        const val TAG_WHITE_BALANCE: String = "WhiteBalance"

        /**
         *
         * This tag indicates the digital zoom ratio when the image was shot. If the numerator of
         * the recorded value is 0, this indicates that digital zoom was not used.
         *
         *
         *  * Tag = 41988
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_DIGITAL_ZOOM_RATIO: String = "DigitalZoomRatio"

        /**
         *
         * This tag indicates the equivalent focal length assuming a 35mm film camera, in mm.
         * A value of 0 means the focal length is unknown. Note that this tag differs from
         * [.TAG_FOCAL_LENGTH].
         *
         *
         *  * Tag = 41989
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_FOCAL_LENGTH_IN_35MM_FILM: String = "FocalLengthIn35mmFilm"

        /**
         *
         * This tag indicates the type of scene that was shot. It may also be used to record
         * the mode in which the image was shot. Note that this differs from
         * [.TAG_SCENE_TYPE].
         *
         *
         *  * Tag = 41990
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = 0
         *
         *
         *
         *
         * @see .SCENE_CAPTURE_TYPE_STANDARD
         *
         * @see .SCENE_CAPTURE_TYPE_LANDSCAPE
         *
         * @see .SCENE_CAPTURE_TYPE_PORTRAIT
         *
         * @see .SCENE_CAPTURE_TYPE_NIGHT
         */
        const val TAG_SCENE_CAPTURE_TYPE: String = "SceneCaptureType"

        /**
         *
         * This tag indicates the degree of overall image gain adjustment.
         *
         *
         *  * Tag = 41991
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .GAIN_CONTROL_NONE
         *
         * @see .GAIN_CONTROL_LOW_GAIN_UP
         *
         * @see .GAIN_CONTROL_HIGH_GAIN_UP
         *
         * @see .GAIN_CONTROL_LOW_GAIN_DOWN
         *
         * @see .GAIN_CONTROL_HIGH_GAIN_DOWN
         */
        const val TAG_GAIN_CONTROL: String = "GainControl"

        /**
         *
         * This tag indicates the direction of contrast processing applied by the camera when
         * the image was shot.
         *
         *
         *  * Tag = 41992
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.CONTRAST_NORMAL]
         *
         *
         *
         *
         * @see .CONTRAST_NORMAL
         *
         * @see .CONTRAST_SOFT
         *
         * @see .CONTRAST_HARD
         */
        const val TAG_CONTRAST: String = "Contrast"

        /**
         *
         * This tag indicates the direction of saturation processing applied by the camera when
         * the image was shot.
         *
         *
         *  * Tag = 41993
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.SATURATION_NORMAL]
         *
         *
         *
         *
         * @see .SATURATION_NORMAL
         *
         * @see .SATURATION_LOW
         *
         * @see .SATURATION_HIGH
         */
        const val TAG_SATURATION: String = "Saturation"

        /**
         *
         * This tag indicates the direction of sharpness processing applied by the camera when
         * the image was shot.
         *
         *
         *  * Tag = 41994
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = [.SHARPNESS_NORMAL]
         *
         *
         *
         *
         * @see .SHARPNESS_NORMAL
         *
         * @see .SHARPNESS_SOFT
         *
         * @see .SHARPNESS_HARD
         */
        const val TAG_SHARPNESS: String = "Sharpness"

        /**
         *
         * This tag indicates information on the picture-taking conditions of a particular camera
         * model. The tag is used only to indicate the picture-taking conditions in the Exif/DCF
         * reader.
         *
         *
         *  * Tag = 41995
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_DEVICE_SETTING_DESCRIPTION: String = "DeviceSettingDescription"

        /**
         *
         * This tag indicates the distance to the subject.
         *
         *
         *  * Tag = 41996
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .SUBJECT_DISTANCE_RANGE_UNKNOWN
         *
         * @see .SUBJECT_DISTANCE_RANGE_MACRO
         *
         * @see .SUBJECT_DISTANCE_RANGE_CLOSE_VIEW
         *
         * @see .SUBJECT_DISTANCE_RANGE_DISTANT_VIEW
         */
        const val TAG_SUBJECT_DISTANCE_RANGE: String = "SubjectDistanceRange"

        // H. Other tags
        /**
         *
         * This tag indicates an identifier assigned uniquely to each image. It is recorded as
         * an ASCII string equivalent to hexadecimal notation and 128-bit fixed length.
         *
         *
         *  * Tag = 42016
         *  * Type = String
         *  * Length = 32
         *  * Default = None
         *
         */
        const val TAG_IMAGE_UNIQUE_ID: String = "ImageUniqueID"

        /**
         *
         * This tag records the owner of a camera used in photography as an ASCII string.
         *
         *
         *  * Tag = 42032
         *  * Type = String
         *  * Default = None
         *
         *
         *
         *
         */
        @Deprecated("Use {@link #TAG_CAMERA_OWNER_NAME} instead.")
        const val TAG_CAMARA_OWNER_NAME: String = "CameraOwnerName"

        /**
         *
         * This tag records the owner of a camera used in photography as an ASCII string.
         *
         *
         *  * Tag = 42032
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_CAMERA_OWNER_NAME: String = "CameraOwnerName"

        /**
         *
         * This tag records the serial number of the body of the camera that was used in photography
         * as an ASCII string.
         *
         *
         *  * Tag = 42033
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_BODY_SERIAL_NUMBER: String = "BodySerialNumber"

        /**
         *
         * This tag notes minimum focal length, maximum focal length, minimum F number in the
         * minimum focal length, and minimum F number in the maximum focal length, which are
         * specification information for the lens that was used in photography. When the minimum
         * F number is unknown, the notation is 0/0.
         *
         *
         *  * Tag = 42034
         *  * Type = Unsigned rational
         *  * Count = 4
         *  * Default = None
         *
         *  * Value 1 := Minimum focal length (unit: mm)
         *  * Value 2 : = Maximum focal length (unit: mm)
         *  * Value 3 : = Minimum F number in the minimum focal length
         *  * Value 4 : = Minimum F number in the maximum focal length
         *
         *
         */
        const val TAG_LENS_SPECIFICATION: String = "LensSpecification"

        /**
         *
         * This tag records the lens manufacturer as an ASCII string.
         *
         *
         *  * Tag = 42035
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_LENS_MAKE: String = "LensMake"

        /**
         *
         * This tag records the lens’s model name and model number as an ASCII string.
         *
         *
         *  * Tag = 42036
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_LENS_MODEL: String = "LensModel"

        /**
         *
         * This tag records the serial number of the interchangeable lens that was used in
         * photography as an ASCII string.
         *
         *
         *  * Tag = 42037
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_LENS_SERIAL_NUMBER: String = "LensSerialNumber"

        // GPS Attribute Information
        /**
         *
         * Indicates the version of GPS Info IFD. The version is given as 2.3.0.0. This tag is
         * mandatory when GPS-related tags are present. Note that this tag is written as a different
         * byte than [.TAG_EXIF_VERSION].
         *
         *
         *  * Tag = 0
         *  * Type = Byte
         *  * Count = 4
         *  * Default = 2.3.0.0
         *
         *  * 2300 = Version 2.3
         *  * Other = reserved
         *
         *
         */
        const val TAG_GPS_VERSION_ID: String = "GPSVersionID"

        /**
         *
         * Indicates whether the latitude is north or south latitude.
         *
         *
         *  * Tag = 1
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .LATITUDE_NORTH
         *
         * @see .LATITUDE_SOUTH
         */
        const val TAG_GPS_LATITUDE_REF: String = "GPSLatitudeRef"

        /**
         *
         * Indicates the latitude. The latitude is expressed as three RATIONAL values giving
         * the degrees, minutes, and seconds, respectively. If latitude is expressed as degrees,
         * minutes and seconds, a typical format would be dd/1,mm/1,ss/1. When degrees and minutes are
         * used and, for example, fractions of minutes are given up to two decimal places, the format
         * would be dd/1,mmmm/100,0/1.
         *
         *
         *  * Tag = 2
         *  * Type = Unsigned rational
         *  * Count = 3
         *  * Default = None
         *
         */
        const val TAG_GPS_LATITUDE: String = "GPSLatitude"

        /**
         *
         * Indicates whether the longitude is east or west longitude.
         *
         *
         *  * Tag = 3
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .LONGITUDE_EAST
         *
         * @see .LONGITUDE_WEST
         */
        const val TAG_GPS_LONGITUDE_REF: String = "GPSLongitudeRef"

        /**
         *
         * Indicates the longitude. The longitude is expressed as three RATIONAL values giving
         * the degrees, minutes, and seconds, respectively. If longitude is expressed as degrees,
         * minutes and seconds, a typical format would be ddd/1,mm/1,ss/1. When degrees and minutes
         * are used and, for example, fractions of minutes are given up to two decimal places,
         * the format would be ddd/1,mmmm/100,0/1.
         *
         *
         *  * Tag = 4
         *  * Type = Unsigned rational
         *  * Count = 3
         *  * Default = None
         *
         */
        const val TAG_GPS_LONGITUDE: String = "GPSLongitude"

        /**
         *
         * Indicates the altitude used as the reference altitude. If the reference is sea level
         * and the altitude is above sea level, 0 is given. If the altitude is below sea level,
         * a value of 1 is given and the altitude is indicated as an absolute value in
         * [.TAG_GPS_ALTITUDE].
         *
         *
         *  * Tag = 5
         *  * Type = Byte
         *  * Count = 1
         *  * Default = 0
         *
         *
         *
         *
         * @see .ALTITUDE_ABOVE_SEA_LEVEL
         *
         * @see .ALTITUDE_BELOW_SEA_LEVEL
         */
        const val TAG_GPS_ALTITUDE_REF: String = "GPSAltitudeRef"

        /**
         *
         * Indicates the altitude based on the reference in [.TAG_GPS_ALTITUDE_REF].
         * The reference unit is meters.
         *
         *
         *  * Tag = 6
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_ALTITUDE: String = "GPSAltitude"

        /**
         *
         * Indicates the time as UTC (Coordinated Universal Time). TimeStamp is expressed as three
         * unsigned rational values giving the hour, minute, and second.
         *
         *
         *  * Tag = 7
         *  * Type = Unsigned rational
         *  * Count = 3
         *  * Default = None
         *
         */
        const val TAG_GPS_TIMESTAMP: String = "GPSTimeStamp"

        /**
         *
         * Indicates the GPS satellites used for measurements. This tag may be used to describe
         * the number of satellites, their ID number, angle of elevation, azimuth, SNR and other
         * information in ASCII notation. The format is not specified. If the GPS receiver is incapable
         * of taking measurements, value of the tag shall be set to `null`.
         *
         *
         *  * Tag = 8
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_GPS_SATELLITES: String = "GPSSatellites"

        /**
         *
         * Indicates the status of the GPS receiver when the image is recorded. 'A' means
         * measurement is in progress, and 'V' means the measurement is interrupted.
         *
         *
         *  * Tag = 9
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .GPS_MEASUREMENT_IN_PROGRESS
         *
         * @see .GPS_MEASUREMENT_INTERRUPTED
         */
        const val TAG_GPS_STATUS: String = "GPSStatus"

        /**
         *
         * Indicates the GPS measurement mode. Originally it was defined for GPS, but it may
         * be used for recording a measure mode to record the position information provided from
         * a mobile base station or wireless LAN as well as GPS.
         *
         *
         *  * Tag = 10
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .GPS_MEASUREMENT_2D
         *
         * @see .GPS_MEASUREMENT_3D
         */
        const val TAG_GPS_MEASURE_MODE: String = "GPSMeasureMode"

        /**
         *
         * Indicates the GPS DOP (data degree of precision). An HDOP value is written during
         * two-dimensional measurement, and PDOP during three-dimensional measurement.
         *
         *
         *  * Tag = 11
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_DOP: String = "GPSDOP"

        /**
         *
         * Indicates the unit used to express the GPS receiver speed of movement.
         *
         *
         *  * Tag = 12
         *  * Type = String
         *  * Length = 1
         *  * Default = [.GPS_SPEED_KILOMETERS_PER_HOUR]
         *
         *
         *
         *
         * @see .GPS_SPEED_KILOMETERS_PER_HOUR
         *
         * @see .GPS_SPEED_MILES_PER_HOUR
         *
         * @see .GPS_SPEED_KNOTS
         */
        const val TAG_GPS_SPEED_REF: String = "GPSSpeedRef"

        /**
         *
         * Indicates the speed of GPS receiver movement.
         *
         *
         *  * Tag = 13
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_SPEED: String = "GPSSpeed"

        /**
         *
         * Indicates the reference for giving the direction of GPS receiver movement.
         *
         *
         *  * Tag = 14
         *  * Type = String
         *  * Length = 1
         *  * Default = [.GPS_DIRECTION_TRUE]
         *
         *
         *
         *
         * @see .GPS_DIRECTION_TRUE
         *
         * @see .GPS_DIRECTION_MAGNETIC
         */
        const val TAG_GPS_TRACK_REF: String = "GPSTrackRef"

        /**
         *
         * Indicates the direction of GPS receiver movement.
         * The range of values is from 0.00 to 359.99.
         *
         *
         *  * Tag = 15
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_TRACK: String = "GPSTrack"

        /**
         *
         * Indicates the reference for giving the direction of the image when it is captured.
         *
         *
         *  * Tag = 16
         *  * Type = String
         *  * Length = 1
         *  * Default = [.GPS_DIRECTION_TRUE]
         *
         *
         *
         *
         * @see .GPS_DIRECTION_TRUE
         *
         * @see .GPS_DIRECTION_MAGNETIC
         */
        const val TAG_GPS_IMG_DIRECTION_REF: String = "GPSImgDirectionRef"

        /**
         *
         * ndicates the direction of the image when it was captured.
         * The range of values is from 0.00 to 359.99.
         *
         *
         *  * Tag = 17
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_IMG_DIRECTION: String = "GPSImgDirection"

        /**
         *
         * Indicates the geodetic survey data used by the GPS receiver. If the survey data is
         * restricted to Japan,the value of this tag is 'TOKYO' or 'WGS-84'. If a GPS Info tag is
         * recorded, it is strongly recommended that this tag be recorded.
         *
         *
         *  * Tag = 18
         *  * Type = String
         *  * Default = None
         *
         */
        const val TAG_GPS_MAP_DATUM: String = "GPSMapDatum"

        /**
         *
         * Indicates whether the latitude of the destination point is north or south latitude.
         *
         *
         *  * Tag = 19
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .LATITUDE_NORTH
         *
         * @see .LATITUDE_SOUTH
         */
        const val TAG_GPS_DEST_LATITUDE_REF: String = "GPSDestLatitudeRef"

        /**
         *
         * Indicates the latitude of the destination point. The latitude is expressed as three
         * unsigned rational values giving the degrees, minutes, and seconds, respectively.
         * If latitude is expressed as degrees, minutes and seconds, a typical format would be
         * dd/1,mm/1,ss/1. When degrees and minutes are used and, for example, fractions of minutes
         * are given up to two decimal places, the format would be dd/1, mmmm/100, 0/1.
         *
         *
         *  * Tag = 20
         *  * Type = Unsigned rational
         *  * Count = 3
         *  * Default = None
         *
         */
        const val TAG_GPS_DEST_LATITUDE: String = "GPSDestLatitude"

        /**
         *
         * Indicates whether the longitude of the destination point is east or west longitude.
         *
         *
         *  * Tag = 21
         *  * Type = String
         *  * Length = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .LONGITUDE_EAST
         *
         * @see .LONGITUDE_WEST
         */
        const val TAG_GPS_DEST_LONGITUDE_REF: String = "GPSDestLongitudeRef"

        /**
         *
         * Indicates the longitude of the destination point. The longitude is expressed as three
         * unsigned rational values giving the degrees, minutes, and seconds, respectively.
         * If longitude is expressed as degrees, minutes and seconds, a typical format would be ddd/1,
         * mm/1, ss/1. When degrees and minutes are used and, for example, fractions of minutes are
         * given up to two decimal places, the format would be ddd/1, mmmm/100, 0/1.
         *
         *
         *  * Tag = 22
         *  * Type = Unsigned rational
         *  * Count = 3
         *  * Default = None
         *
         */
        const val TAG_GPS_DEST_LONGITUDE: String = "GPSDestLongitude"

        /**
         *
         * Indicates the reference used for giving the bearing to the destination point.
         *
         *
         *  * Tag = 23
         *  * Type = String
         *  * Length = 1
         *  * Default = [.GPS_DIRECTION_TRUE]
         *
         *
         *
         *
         * @see .GPS_DIRECTION_TRUE
         *
         * @see .GPS_DIRECTION_MAGNETIC
         */
        const val TAG_GPS_DEST_BEARING_REF: String = "GPSDestBearingRef"

        /**
         *
         * Indicates the bearing to the destination point.
         * The range of values is from 0.00 to 359.99.
         *
         *
         *  * Tag = 24
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_DEST_BEARING: String = "GPSDestBearing"

        /**
         *
         * Indicates the unit used to express the distance to the destination point.
         *
         *
         *  * Tag = 25
         *  * Type = String
         *  * Length = 1
         *  * Default = [.GPS_DISTANCE_KILOMETERS]
         *
         *
         *
         *
         * @see .GPS_DISTANCE_KILOMETERS
         *
         * @see .GPS_DISTANCE_MILES
         *
         * @see .GPS_DISTANCE_NAUTICAL_MILES
         */
        const val TAG_GPS_DEST_DISTANCE_REF: String = "GPSDestDistanceRef"

        /**
         *
         * Indicates the distance to the destination point.
         *
         *
         *  * Tag = 26
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_DEST_DISTANCE: String = "GPSDestDistance"

        /**
         *
         * A character string recording the name of the method used for location finding.
         * The first byte indicates the character code used, and this is followed by the name of
         * the method.
         *
         *
         *  * Tag = 27
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_GPS_PROCESSING_METHOD: String = "GPSProcessingMethod"

        /**
         *
         * A character string recording the name of the GPS area. The first byte indicates
         * the character code used, and this is followed by the name of the GPS area.
         *
         *
         *  * Tag = 28
         *  * Type = Undefined
         *  * Default = None
         *
         */
        const val TAG_GPS_AREA_INFORMATION: String = "GPSAreaInformation"

        /**
         *
         * A character string recording date and time information relative to UTC (Coordinated
         * Universal Time). The format is "YYYY:MM:DD".
         *
         *
         *  * Tag = 29
         *  * Type = String
         *  * Length = 10
         *  * Default = None
         *
         */
        const val TAG_GPS_DATESTAMP: String = "GPSDateStamp"

        /**
         *
         * Indicates whether differential correction is applied to the GPS receiver.
         *
         *
         *  * Tag = 30
         *  * Type = Unsigned short
         *  * Count = 1
         *  * Default = None
         *
         *
         *
         *
         * @see .GPS_MEASUREMENT_NO_DIFFERENTIAL
         *
         * @see .GPS_MEASUREMENT_DIFFERENTIAL_CORRECTED
         */
        const val TAG_GPS_DIFFERENTIAL: String = "GPSDifferential"

        /**
         *
         * This tag indicates horizontal positioning errors in meters.
         *
         *
         *  * Tag = 31
         *  * Type = Unsigned rational
         *  * Count = 1
         *  * Default = None
         *
         */
        const val TAG_GPS_H_POSITIONING_ERROR: String = "GPSHPositioningError"

        // Interoperability IFD Attribute Information
        /**
         *
         * Indicates the identification of the Interoperability rule.
         *
         *
         *  * Tag = 1
         *  * Type = String
         *  * Length = 4
         *  * Default = None
         *
         *  * "R98" = Indicates a file conforming to R98 file specification of Recommended
         * Exif Interoperability Rules (Exif R 98) or to DCF basic file stipulated
         * by Design Rule for Camera File System.
         *  * "THM" = Indicates a file conforming to DCF thumbnail file stipulated by Design
         * rule for Camera File System.
         *  * “R03” = Indicates a file conforming to DCF Option File stipulated by Design rule
         * for Camera File System.
         *
         *
         */
        const val TAG_INTEROPERABILITY_INDEX: String = "InteroperabilityIndex"

        /**
         * @see .TAG_IMAGE_LENGTH
         */
        const val TAG_THUMBNAIL_IMAGE_LENGTH: String = "ThumbnailImageLength"

        /**
         * @see .TAG_IMAGE_WIDTH
         */
        const val TAG_THUMBNAIL_IMAGE_WIDTH: String = "ThumbnailImageWidth"

        // TODO: Unhide this when it can be public.
        /**
         * @hide
         * @see .TAG_ORIENTATION
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        const val TAG_THUMBNAIL_ORIENTATION: String = "ThumbnailOrientation"

        /**
         * Type is int. DNG Specification 1.4.0.0. Section 4
         */
        const val TAG_DNG_VERSION: String = "DNGVersion"

        /**
         * Type is int. DNG Specification 1.4.0.0. Section 4
         */
        const val TAG_DEFAULT_CROP_SIZE: String = "DefaultCropSize"

        /**
         * Type is undefined. See Olympus MakerNote tags in http://www.exiv2.org/tags-olympus.html.
         */
        const val TAG_ORF_THUMBNAIL_IMAGE: String = "ThumbnailImage"

        /**
         * Type is int. See Olympus Camera Settings tags in http://www.exiv2.org/tags-olympus.html.
         */
        const val TAG_ORF_PREVIEW_IMAGE_START: String = "PreviewImageStart"

        /**
         * Type is int. See Olympus Camera Settings tags in http://www.exiv2.org/tags-olympus.html.
         */
        const val TAG_ORF_PREVIEW_IMAGE_LENGTH: String = "PreviewImageLength"

        /**
         * Type is int. See Olympus Image Processing tags in http://www.exiv2.org/tags-olympus.html.
         */
        const val TAG_ORF_ASPECT_FRAME: String = "AspectFrame"

        /**
         * Type is int. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_SENSOR_BOTTOM_BORDER: String = "SensorBottomBorder"

        /**
         * Type is int. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_SENSOR_LEFT_BORDER: String = "SensorLeftBorder"

        /**
         * Type is int. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_SENSOR_RIGHT_BORDER: String = "SensorRightBorder"

        /**
         * Type is int. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_SENSOR_TOP_BORDER: String = "SensorTopBorder"

        /**
         * Type is int. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_ISO: String = "ISO"

        /**
         * Type is undefined. See PanasonicRaw tags in
         * http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html
         */
        const val TAG_RW2_JPG_FROM_RAW: String = "JpgFromRaw"

        /**
         * Type is byte[]. See [Extensible
         * Metadata Platform (XMP)](https://en.wikipedia.org/wiki/Extensible_Metadata_Platform) for details on contents.
         */
        const val TAG_XMP: String = "Xmp"

        /**
         * Type is int. See JEITA CP-3451C Spec Section 3: Bilevel Images.
         */
        const val TAG_NEW_SUBFILE_TYPE: String = "NewSubfileType"

        /**
         * Type is int. See JEITA CP-3451C Spec Section 3: Bilevel Images.
         */
        const val TAG_SUBFILE_TYPE: String = "SubfileType"

        /**
         * Private tags used for pointing the other IFD offsets.
         * The types of the following tags are int.
         * See JEITA CP-3451C Section 4.6.3: Exif-specific IFD.
         * For SubIFD, see Note 1 of Adobe PageMaker® 6.0 TIFF Technical Notes.
         */
        private const val TAG_EXIF_IFD_POINTER = "ExifIFDPointer"
        private const val TAG_GPS_INFO_IFD_POINTER = "GPSInfoIFDPointer"
        private const val TAG_INTEROPERABILITY_IFD_POINTER = "InteroperabilityIFDPointer"
        private const val TAG_SUB_IFD_POINTER = "SubIFDPointer"

        // Proprietary pointer tags used for ORF files.
        // See http://www.exiv2.org/tags-olympus.html
        private const val TAG_ORF_CAMERA_SETTINGS_IFD_POINTER = "CameraSettingsIFDPointer"
        private const val TAG_ORF_IMAGE_PROCESSING_IFD_POINTER = "ImageProcessingIFDPointer"

        private const val MAX_THUMBNAIL_SIZE = 512

        // Constants used for the Orientation Exif tag.
        const val ORIENTATION_UNDEFINED: Int = 0
        const val ORIENTATION_NORMAL: Int = 1

        /**
         * Indicates the image is left right reversed mirror.
         */
        const val ORIENTATION_FLIP_HORIZONTAL: Int = 2

        /**
         * Indicates the image is rotated by 180 degree clockwise.
         */
        const val ORIENTATION_ROTATE_180: Int = 3

        /**
         * Indicates the image is upside down mirror, it can also be represented by flip
         * horizontally firstly and rotate 180 degree clockwise.
         */
        const val ORIENTATION_FLIP_VERTICAL: Int = 4

        /**
         * Indicates the image is flipped about top-left <--> bottom-right axis, it can also be
         * represented by flip horizontally firstly and rotate 270 degree clockwise.
         */
        const val ORIENTATION_TRANSPOSE: Int = 5

        /**
         * Indicates the image is rotated by 90 degree clockwise.
         */
        const val ORIENTATION_ROTATE_90: Int = 6

        /**
         * Indicates the image is flipped about top-right <--> bottom-left axis, it can also be
         * represented by flip horizontally firstly and rotate 90 degree clockwise.
         */
        const val ORIENTATION_TRANSVERSE: Int = 7

        /**
         * Indicates the image is rotated by 270 degree clockwise.
         */
        const val ORIENTATION_ROTATE_270: Int = 8
        private val ROTATION_ORDER: List<Int> = Arrays.asList(
            ORIENTATION_NORMAL,
            ORIENTATION_ROTATE_90, ORIENTATION_ROTATE_180, ORIENTATION_ROTATE_270
        )
        private val FLIPPED_ROTATION_ORDER: List<Int> = Arrays.asList(
            ORIENTATION_FLIP_HORIZONTAL, ORIENTATION_TRANSVERSE, ORIENTATION_FLIP_VERTICAL,
            ORIENTATION_TRANSPOSE
        )

        /**
         * The constant used by [.TAG_PLANAR_CONFIGURATION] to denote Chunky format.
         */
        const val FORMAT_CHUNKY: Short = 1

        /**
         * The constant used by [.TAG_PLANAR_CONFIGURATION] to denote Planar format.
         */
        const val FORMAT_PLANAR: Short = 2

        /**
         * The constant used by [.TAG_Y_CB_CR_POSITIONING] to denote Centered positioning.
         */
        const val Y_CB_CR_POSITIONING_CENTERED: Short = 1

        /**
         * The constant used by [.TAG_Y_CB_CR_POSITIONING] to denote Co-sited positioning.
         */
        const val Y_CB_CR_POSITIONING_CO_SITED: Short = 2

        /**
         * The constant used to denote resolution unit as inches.
         */
        const val RESOLUTION_UNIT_INCHES: Short = 2

        /**
         * The constant used to denote resolution unit as centimeters.
         */
        const val RESOLUTION_UNIT_CENTIMETERS: Short = 3

        /**
         * The constant used by [.TAG_COLOR_SPACE] to denote sRGB color space.
         */
        const val COLOR_SPACE_S_RGB: Int = 1

        /**
         * The constant used by [.TAG_COLOR_SPACE] to denote Uncalibrated.
         */
        const val COLOR_SPACE_UNCALIBRATED: Int = 65535

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is not defined.
         */
        const val EXPOSURE_PROGRAM_NOT_DEFINED: Short = 0

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Manual.
         */
        const val EXPOSURE_PROGRAM_MANUAL: Short = 1

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Normal.
         */
        const val EXPOSURE_PROGRAM_NORMAL: Short = 2

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is
         * Aperture priority.
         */
        const val EXPOSURE_PROGRAM_APERTURE_PRIORITY: Short = 3

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is
         * Shutter priority.
         */
        const val EXPOSURE_PROGRAM_SHUTTER_PRIORITY: Short = 4

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Creative
         * program (biased toward depth of field).
         */
        const val EXPOSURE_PROGRAM_CREATIVE: Short = 5

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Action
         * program (biased toward fast shutter speed).
         */
        const val EXPOSURE_PROGRAM_ACTION: Short = 6

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Portrait
         * mode (for closeup photos with the background out of focus).
         */
        const val EXPOSURE_PROGRAM_PORTRAIT_MODE: Short = 7

        /**
         * The constant used by [.TAG_EXPOSURE_PROGRAM] to denote exposure program is Landscape
         * mode (for landscape photos with the background in focus).
         */
        const val EXPOSURE_PROGRAM_LANDSCAPE_MODE: Short = 8

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is unknown.
         */
        const val SENSITIVITY_TYPE_UNKNOWN: Short = 0

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Standard
         * output sensitivity (SOS).
         */
        const val SENSITIVITY_TYPE_SOS: Short = 1

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Recommended
         * exposure index (REI).
         */
        const val SENSITIVITY_TYPE_REI: Short = 2

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is ISO speed.
         */
        const val SENSITIVITY_TYPE_ISO_SPEED: Short = 3

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Standard
         * output sensitivity (SOS) and recommended exposure index (REI).
         */
        const val SENSITIVITY_TYPE_SOS_AND_REI: Short = 4

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Standard
         * output sensitivity (SOS) and ISO speed.
         */
        const val SENSITIVITY_TYPE_SOS_AND_ISO: Short = 5

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Recommended
         * exposure index (REI) and ISO speed.
         */
        const val SENSITIVITY_TYPE_REI_AND_ISO: Short = 6

        /**
         * The constant used by [.TAG_SENSITIVITY_TYPE] to denote sensitivity type is Standard
         * output sensitivity (SOS) and recommended exposure index (REI) and ISO speed.
         */
        const val SENSITIVITY_TYPE_SOS_AND_REI_AND_ISO: Short = 7

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is unknown.
         */
        const val METERING_MODE_UNKNOWN: Short = 0

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is Average.
         */
        const val METERING_MODE_AVERAGE: Short = 1

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is
         * CenterWeightedAverage.
         */
        const val METERING_MODE_CENTER_WEIGHT_AVERAGE: Short = 2

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is Spot.
         */
        const val METERING_MODE_SPOT: Short = 3

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is MultiSpot.
         */
        const val METERING_MODE_MULTI_SPOT: Short = 4

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is Pattern.
         */
        const val METERING_MODE_PATTERN: Short = 5

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is Partial.
         */
        const val METERING_MODE_PARTIAL: Short = 6

        /**
         * The constant used by [.TAG_METERING_MODE] to denote metering mode is other.
         */
        const val METERING_MODE_OTHER: Short = 255

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is unknown.
         */
        const val LIGHT_SOURCE_UNKNOWN: Short = 0

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Daylight.
         */
        const val LIGHT_SOURCE_DAYLIGHT: Short = 1

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Fluorescent.
         */
        const val LIGHT_SOURCE_FLUORESCENT: Short = 2

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Tungsten
         * (incandescent light).
         */
        const val LIGHT_SOURCE_TUNGSTEN: Short = 3

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Flash.
         */
        const val LIGHT_SOURCE_FLASH: Short = 4

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Fine weather.
         */
        const val LIGHT_SOURCE_FINE_WEATHER: Short = 9

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Cloudy weather.
         */
        const val LIGHT_SOURCE_CLOUDY_WEATHER: Short = 10

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Shade.
         */
        const val LIGHT_SOURCE_SHADE: Short = 11

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Daylight fluorescent
         * (D 5700 - 7100K).
         */
        const val LIGHT_SOURCE_DAYLIGHT_FLUORESCENT: Short = 12

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Day white
         * fluorescent (N 4600 - 5500K).
         */
        const val LIGHT_SOURCE_DAY_WHITE_FLUORESCENT: Short = 13

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Cool white
         * fluorescent (W 3800 - 4500K).
         */
        const val LIGHT_SOURCE_COOL_WHITE_FLUORESCENT: Short = 14

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is White fluorescent
         * (WW 3250 - 3800K).
         */
        const val LIGHT_SOURCE_WHITE_FLUORESCENT: Short = 15

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Warm white
         * fluorescent (L 2600 - 3250K).
         */
        const val LIGHT_SOURCE_WARM_WHITE_FLUORESCENT: Short = 16

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Standard light A.
         */
        const val LIGHT_SOURCE_STANDARD_LIGHT_A: Short = 17

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Standard light B.
         */
        const val LIGHT_SOURCE_STANDARD_LIGHT_B: Short = 18

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is Standard light C.
         */
        const val LIGHT_SOURCE_STANDARD_LIGHT_C: Short = 19

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is D55.
         */
        const val LIGHT_SOURCE_D55: Short = 20

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is D65.
         */
        const val LIGHT_SOURCE_D65: Short = 21

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is D75.
         */
        const val LIGHT_SOURCE_D75: Short = 22

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is D50.
         */
        const val LIGHT_SOURCE_D50: Short = 23

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is ISO studio tungsten.
         */
        const val LIGHT_SOURCE_ISO_STUDIO_TUNGSTEN: Short = 24

        /**
         * The constant used by [.TAG_LIGHT_SOURCE] to denote light source is other.
         */
        const val LIGHT_SOURCE_OTHER: Short = 255

        /**
         * The flag used by [.TAG_FLASH] to indicate whether the flash is fired.
         */
        const val FLAG_FLASH_FIRED: Short = 1

        /**
         * The flag used by [.TAG_FLASH] to indicate strobe return light is not detected.
         */
        const val FLAG_FLASH_RETURN_LIGHT_NOT_DETECTED: Short = 4

        /**
         * The flag used by [.TAG_FLASH] to indicate strobe return light is detected.
         */
        const val FLAG_FLASH_RETURN_LIGHT_DETECTED: Short = 6

        /**
         * The flag used by [.TAG_FLASH] to indicate the camera's flash mode is Compulsory flash
         * firing.
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION
         *
         * @see .FLAG_FLASH_MODE_AUTO
         */
        const val FLAG_FLASH_MODE_COMPULSORY_FIRING: Short = 8

        /**
         * The flag used by [.TAG_FLASH] to indicate the camera's flash mode is Compulsory flash
         * suppression.
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_FIRING
         *
         * @see .FLAG_FLASH_MODE_AUTO
         */
        const val FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION: Short = 16

        /**
         * The flag used by [.TAG_FLASH] to indicate the camera's flash mode is Auto.
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_FIRING
         *
         * @see .FLAG_FLASH_MODE_COMPULSORY_SUPPRESSION
         */
        const val FLAG_FLASH_MODE_AUTO: Short = 24

        /**
         * The flag used by [.TAG_FLASH] to indicate no flash function is present.
         */
        const val FLAG_FLASH_NO_FLASH_FUNCTION: Short = 32

        /**
         * The flag used by [.TAG_FLASH] to indicate red-eye reduction is supported.
         */
        const val FLAG_FLASH_RED_EYE_SUPPORTED: Short = 64

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is not
         * defined.
         */
        const val SENSOR_TYPE_NOT_DEFINED: Short = 1

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is One-chip
         * color area sensor.
         */
        const val SENSOR_TYPE_ONE_CHIP: Short = 2

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is Two-chip
         * color area sensor.
         */
        const val SENSOR_TYPE_TWO_CHIP: Short = 3

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is
         * Three-chip color area sensor.
         */
        const val SENSOR_TYPE_THREE_CHIP: Short = 4

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is Color
         * sequential area sensor.
         */
        const val SENSOR_TYPE_COLOR_SEQUENTIAL: Short = 5

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is Trilinear
         * sensor.
         */
        const val SENSOR_TYPE_TRILINEAR: Short = 7

        /**
         * The constant used by [.TAG_SENSING_METHOD] to denote the image sensor type is Color
         * sequential linear sensor.
         */
        const val SENSOR_TYPE_COLOR_SEQUENTIAL_LINEAR: Short = 8

        /**
         * The constant used by [.TAG_FILE_SOURCE] to denote the source is other.
         */
        const val FILE_SOURCE_OTHER: Short = 0

        /**
         * The constant used by [.TAG_FILE_SOURCE] to denote the source is scanner of transparent
         * type.
         */
        const val FILE_SOURCE_TRANSPARENT_SCANNER: Short = 1

        /**
         * The constant used by [.TAG_FILE_SOURCE] to denote the source is scanner of reflex type.
         */
        const val FILE_SOURCE_REFLEX_SCANNER: Short = 2

        /**
         * The constant used by [.TAG_FILE_SOURCE] to denote the source is DSC.
         */
        const val FILE_SOURCE_DSC: Short = 3

        /**
         * The constant used by [.TAG_SCENE_TYPE] to denote the scene is directly photographed.
         */
        const val SCENE_TYPE_DIRECTLY_PHOTOGRAPHED: Short = 1

        /**
         * The constant used by [.TAG_CUSTOM_RENDERED] to denote no special processing is used.
         */
        const val RENDERED_PROCESS_NORMAL: Short = 0

        /**
         * The constant used by [.TAG_CUSTOM_RENDERED] to denote special processing is used.
         */
        const val RENDERED_PROCESS_CUSTOM: Short = 1

        /**
         * The constant used by [.TAG_EXPOSURE_MODE] to denote the exposure mode is Auto.
         */
        const val EXPOSURE_MODE_AUTO: Short = 0

        /**
         * The constant used by [.TAG_EXPOSURE_MODE] to denote the exposure mode is Manual.
         */
        const val EXPOSURE_MODE_MANUAL: Short = 1

        /**
         * The constant used by [.TAG_EXPOSURE_MODE] to denote the exposure mode is Auto bracket.
         */
        const val EXPOSURE_MODE_AUTO_BRACKET: Short = 2

        /**
         * The constant used by [.TAG_WHITE_BALANCE] to denote the white balance is Auto.
         *
         */
        @Deprecated("Use {@link #WHITE_BALANCE_AUTO} instead.")
        const val WHITEBALANCE_AUTO: Int = 0

        /**
         * The constant used by [.TAG_WHITE_BALANCE] to denote the white balance is Manual.
         *
         */
        @Deprecated("Use {@link #WHITE_BALANCE_MANUAL} instead.")
        const val WHITEBALANCE_MANUAL: Int = 1

        /**
         * The constant used by [.TAG_WHITE_BALANCE] to denote the white balance is Auto.
         */
        const val WHITE_BALANCE_AUTO: Short = 0

        /**
         * The constant used by [.TAG_WHITE_BALANCE] to denote the white balance is Manual.
         */
        const val WHITE_BALANCE_MANUAL: Short = 1

        /**
         * The constant used by [.TAG_SCENE_CAPTURE_TYPE] to denote the scene capture type is
         * Standard.
         */
        const val SCENE_CAPTURE_TYPE_STANDARD: Short = 0

        /**
         * The constant used by [.TAG_SCENE_CAPTURE_TYPE] to denote the scene capture type is
         * Landscape.
         */
        const val SCENE_CAPTURE_TYPE_LANDSCAPE: Short = 1

        /**
         * The constant used by [.TAG_SCENE_CAPTURE_TYPE] to denote the scene capture type is
         * Portrait.
         */
        const val SCENE_CAPTURE_TYPE_PORTRAIT: Short = 2

        /**
         * The constant used by [.TAG_SCENE_CAPTURE_TYPE] to denote the scene capture type is
         * Night scene.
         */
        const val SCENE_CAPTURE_TYPE_NIGHT: Short = 3

        /**
         * The constant used by [.TAG_GAIN_CONTROL] to denote none gain adjustment.
         */
        const val GAIN_CONTROL_NONE: Short = 0

        /**
         * The constant used by [.TAG_GAIN_CONTROL] to denote low gain up.
         */
        const val GAIN_CONTROL_LOW_GAIN_UP: Short = 1

        /**
         * The constant used by [.TAG_GAIN_CONTROL] to denote high gain up.
         */
        const val GAIN_CONTROL_HIGH_GAIN_UP: Short = 2

        /**
         * The constant used by [.TAG_GAIN_CONTROL] to denote low gain down.
         */
        const val GAIN_CONTROL_LOW_GAIN_DOWN: Short = 3

        /**
         * The constant used by [.TAG_GAIN_CONTROL] to denote high gain down.
         */
        const val GAIN_CONTROL_HIGH_GAIN_DOWN: Short = 4

        /**
         * The constant used by [.TAG_CONTRAST] to denote normal contrast.
         */
        const val CONTRAST_NORMAL: Short = 0

        /**
         * The constant used by [.TAG_CONTRAST] to denote soft contrast.
         */
        const val CONTRAST_SOFT: Short = 1

        /**
         * The constant used by [.TAG_CONTRAST] to denote hard contrast.
         */
        const val CONTRAST_HARD: Short = 2

        /**
         * The constant used by [.TAG_SATURATION] to denote normal saturation.
         */
        const val SATURATION_NORMAL: Short = 0

        /**
         * The constant used by [.TAG_SATURATION] to denote low saturation.
         */
        const val SATURATION_LOW: Short = 0

        /**
         * The constant used by [.TAG_SHARPNESS] to denote high saturation.
         */
        const val SATURATION_HIGH: Short = 0

        /**
         * The constant used by [.TAG_SHARPNESS] to denote normal sharpness.
         */
        const val SHARPNESS_NORMAL: Short = 0

        /**
         * The constant used by [.TAG_SHARPNESS] to denote soft sharpness.
         */
        const val SHARPNESS_SOFT: Short = 1

        /**
         * The constant used by [.TAG_SHARPNESS] to denote hard sharpness.
         */
        const val SHARPNESS_HARD: Short = 2

        /**
         * The constant used by [.TAG_SUBJECT_DISTANCE_RANGE] to denote the subject distance range
         * is unknown.
         */
        const val SUBJECT_DISTANCE_RANGE_UNKNOWN: Short = 0

        /**
         * The constant used by [.TAG_SUBJECT_DISTANCE_RANGE] to denote the subject distance range
         * is Macro.
         */
        const val SUBJECT_DISTANCE_RANGE_MACRO: Short = 1

        /**
         * The constant used by [.TAG_SUBJECT_DISTANCE_RANGE] to denote the subject distance range
         * is Close view.
         */
        const val SUBJECT_DISTANCE_RANGE_CLOSE_VIEW: Short = 2

        /**
         * The constant used by [.TAG_SUBJECT_DISTANCE_RANGE] to denote the subject distance range
         * is Distant view.
         */
        const val SUBJECT_DISTANCE_RANGE_DISTANT_VIEW: Short = 3

        /**
         * The constant used by GPS latitude-related tags to denote the latitude is North latitude.
         *
         * @see .TAG_GPS_LATITUDE_REF
         *
         * @see .TAG_GPS_DEST_LATITUDE_REF
         */
        const val LATITUDE_NORTH: String = "N"

        /**
         * The constant used by GPS latitude-related tags to denote the latitude is South latitude.
         *
         * @see .TAG_GPS_LATITUDE_REF
         *
         * @see .TAG_GPS_DEST_LATITUDE_REF
         */
        const val LATITUDE_SOUTH: String = "S"

        /**
         * The constant used by GPS longitude-related tags to denote the longitude is East longitude.
         *
         * @see .TAG_GPS_LONGITUDE_REF
         *
         * @see .TAG_GPS_DEST_LONGITUDE_REF
         */
        const val LONGITUDE_EAST: String = "E"

        /**
         * The constant used by GPS longitude-related tags to denote the longitude is West longitude.
         *
         * @see .TAG_GPS_LONGITUDE_REF
         *
         * @see .TAG_GPS_DEST_LONGITUDE_REF
         */
        const val LONGITUDE_WEST: String = "W"

        /**
         * The constant used by [.TAG_GPS_ALTITUDE_REF] to denote the altitude is above sea level.
         */
        const val ALTITUDE_ABOVE_SEA_LEVEL: Short = 0

        /**
         * The constant used by [.TAG_GPS_ALTITUDE_REF] to denote the altitude is below sea level.
         */
        const val ALTITUDE_BELOW_SEA_LEVEL: Short = 1

        /**
         * The constant used by [.TAG_GPS_STATUS] to denote GPS measurement is in progress.
         */
        const val GPS_MEASUREMENT_IN_PROGRESS: String = "A"

        /**
         * The constant used by [.TAG_GPS_STATUS] to denote GPS measurement is interrupted.
         */
        const val GPS_MEASUREMENT_INTERRUPTED: String = "V"

        /**
         * The constant used by [.TAG_GPS_MEASURE_MODE] to denote GPS measurement is
         * 2-dimensional.
         */
        const val GPS_MEASUREMENT_2D: String = "2"

        /**
         * The constant used by [.TAG_GPS_MEASURE_MODE] to denote GPS measurement is
         * 3-dimensional.
         */
        const val GPS_MEASUREMENT_3D: String = "3"

        /**
         * The constant used by [.TAG_GPS_SPEED_REF] to denote the speed unit is kilometers per
         * hour.
         */
        const val GPS_SPEED_KILOMETERS_PER_HOUR: String = "K"

        /**
         * The constant used by [.TAG_GPS_SPEED_REF] to denote the speed unit is miles per hour.
         */
        const val GPS_SPEED_MILES_PER_HOUR: String = "M"

        /**
         * The constant used by [.TAG_GPS_SPEED_REF] to denote the speed unit is knots.
         */
        const val GPS_SPEED_KNOTS: String = "N"

        /**
         * The constant used by GPS attributes to denote the direction is true direction.
         */
        const val GPS_DIRECTION_TRUE: String = "T"

        /**
         * The constant used by GPS attributes to denote the direction is magnetic direction.
         */
        const val GPS_DIRECTION_MAGNETIC: String = "M"

        /**
         * The constant used by [.TAG_GPS_DEST_DISTANCE_REF] to denote the distance unit is
         * kilometers.
         */
        const val GPS_DISTANCE_KILOMETERS: String = "K"

        /**
         * The constant used by [.TAG_GPS_DEST_DISTANCE_REF] to denote the distance unit is miles.
         */
        const val GPS_DISTANCE_MILES: String = "M"

        /**
         * The constant used by [.TAG_GPS_DEST_DISTANCE_REF] to denote the distance unit is
         * nautical miles.
         */
        const val GPS_DISTANCE_NAUTICAL_MILES: String = "N"

        /**
         * The constant used by [.TAG_GPS_DIFFERENTIAL] to denote no differential correction is
         * applied.
         */
        const val GPS_MEASUREMENT_NO_DIFFERENTIAL: Short = 0

        /**
         * The constant used by [.TAG_GPS_DIFFERENTIAL] to denote differential correction is
         * applied.
         */
        const val GPS_MEASUREMENT_DIFFERENTIAL_CORRECTED: Short = 1

        /**
         * The constant used by [.TAG_COMPRESSION] to denote the image is not compressed.
         */
        const val DATA_UNCOMPRESSED: Int = 1

        /**
         * The constant used by [.TAG_COMPRESSION] to denote the image is huffman compressed.
         */
        const val DATA_HUFFMAN_COMPRESSED: Int = 2

        /**
         * The constant used by [.TAG_COMPRESSION] to denote the image is JPEG.
         */
        const val DATA_JPEG: Int = 6

        /**
         * The constant used by [.TAG_COMPRESSION], see DNG Specification 1.4.0.0.
         * Section 3, Compression
         */
        const val DATA_JPEG_COMPRESSED: Int = 7

        /**
         * The constant used by [.TAG_COMPRESSION], see DNG Specification 1.4.0.0.
         * Section 3, Compression
         */
        const val DATA_DEFLATE_ZIP: Int = 8

        /**
         * The constant used by [.TAG_COMPRESSION] to denote the image is pack-bits compressed.
         */
        const val DATA_PACK_BITS_COMPRESSED: Int = 32773

        /**
         * The constant used by [.TAG_COMPRESSION], see DNG Specification 1.4.0.0.
         * Section 3, Compression
         */
        const val DATA_LOSSY_JPEG: Int = 34892

        /**
         * The constant used by [.TAG_BITS_PER_SAMPLE].
         * See JEITA CP-3451C Spec Section 6, Differences from Palette Color Images
         */
        val BITS_PER_SAMPLE_RGB: IntArray = intArrayOf(8, 8, 8)

        /**
         * The constant used by [.TAG_BITS_PER_SAMPLE].
         * See JEITA CP-3451C Spec Section 4, Differences from Bilevel Images
         */
        val BITS_PER_SAMPLE_GREYSCALE_1: IntArray = intArrayOf(4)

        /**
         * The constant used by [.TAG_BITS_PER_SAMPLE].
         * See JEITA CP-3451C Spec Section 4, Differences from Bilevel Images
         */
        val BITS_PER_SAMPLE_GREYSCALE_2: IntArray = intArrayOf(8)

        /**
         * The constant used by [.TAG_PHOTOMETRIC_INTERPRETATION].
         */
        const val PHOTOMETRIC_INTERPRETATION_WHITE_IS_ZERO: Int = 0

        /**
         * The constant used by [.TAG_PHOTOMETRIC_INTERPRETATION].
         */
        const val PHOTOMETRIC_INTERPRETATION_BLACK_IS_ZERO: Int = 1

        /**
         * The constant used by [.TAG_PHOTOMETRIC_INTERPRETATION].
         */
        const val PHOTOMETRIC_INTERPRETATION_RGB: Int = 2

        /**
         * The constant used by [.TAG_PHOTOMETRIC_INTERPRETATION].
         */
        const val PHOTOMETRIC_INTERPRETATION_YCBCR: Int = 6

        /**
         * The constant used by [.TAG_NEW_SUBFILE_TYPE]. See JEITA CP-3451C Spec Section 8.
         */
        const val ORIGINAL_RESOLUTION_IMAGE: Int = 0

        /**
         * The constant used by [.TAG_NEW_SUBFILE_TYPE]. See JEITA CP-3451C Spec Section 8.
         */
        const val REDUCED_RESOLUTION_IMAGE: Int = 1

        /**
         * Constant used to indicate that the input stream contains the full image data.
         *
         *
         * The format of the image data should follow one of the image formats supported by this class.
         */
        const val STREAM_TYPE_FULL_IMAGE_DATA: Int = 0

        /**
         * Constant used to indicate that the input stream contains only Exif data.
         *
         *
         * The format of the Exif-only data must follow the below structure:
         * Exif Identifier Code ("Exif\0\0") + TIFF header + IFD data
         * See JEITA CP-3451C Section 4.5.2 and 4.5.4 specifications for more details.
         */
        const val STREAM_TYPE_EXIF_DATA_ONLY: Int = 1

        // Maximum size for checking file type signature (see image_type_recognition_lite.cc)
        private const val SIGNATURE_CHECK_SIZE = 5000

        val JPEG_SIGNATURE: ByteArray = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        private const val RAF_SIGNATURE = "FUJIFILMCCD-RAW"
        private const val RAF_OFFSET_TO_JPEG_IMAGE_OFFSET = 84

        private val HEIF_TYPE_FTYP =
            byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        private val HEIF_BRAND_MIF1 =
            byteArrayOf('m'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), '1'.code.toByte())
        private val HEIF_BRAND_HEIC =
            byteArrayOf('h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte())

        // See http://fileformats.archiveteam.org/wiki/Olympus_ORF
        private const val ORF_SIGNATURE_1: Short = 0x4f52
        private const val ORF_SIGNATURE_2: Short = 0x5352

        // There are two formats for Olympus Makernote Headers. Each has different identifiers and
        // offsets to the actual data.
        // See http://www.exiv2.org/makernote.html#R1
        private val ORF_MAKER_NOTE_HEADER_1 = byteArrayOf(
            0x4f.toByte(), 0x4c.toByte(),
            0x59.toByte(), 0x4d.toByte(), 0x50.toByte(), 0x00.toByte()
        ) // "OLYMP\0"
        private val ORF_MAKER_NOTE_HEADER_2 = byteArrayOf(
            0x4f.toByte(),
            0x4c.toByte(),
            0x59.toByte(),
            0x4d.toByte(),
            0x50.toByte(),
            0x55.toByte(),
            0x53.toByte(),
            0x00.toByte(),
            0x49.toByte(),
            0x49.toByte()
        ) // "OLYMPUS\0II"
        private const val ORF_MAKER_NOTE_HEADER_1_SIZE = 8
        private const val ORF_MAKER_NOTE_HEADER_2_SIZE = 12

        // See http://fileformats.archiveteam.org/wiki/RW2
        private const val RW2_SIGNATURE: Short = 0x0055

        // See http://fileformats.archiveteam.org/wiki/Pentax_PEF
        private const val PEF_SIGNATURE = "PENTAX"

        // See http://www.exiv2.org/makernote.html#R11
        private const val PEF_MAKER_NOTE_SKIP_SIZE = 6

        // See PNG (Portable Network Graphics) Specification, Version 1.2,
        // 3.1. PNG file signature
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4e.toByte(),
            0x47.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x1a.toByte(), 0x0a.toByte()
        )

        // See PNG (Portable Network Graphics) Specification, Version 1.2,
        // 3.7. eXIf Exchangeable Image File (Exif) Profile
        private val PNG_CHUNK_TYPE_EXIF = byteArrayOf(
            0x65.toByte(), 0x58.toByte(),
            0x49.toByte(), 0x66.toByte()
        )
        private val PNG_CHUNK_TYPE_IHDR = byteArrayOf(
            0x49.toByte(), 0x48.toByte(),
            0x44.toByte(), 0x52.toByte()
        )
        private val PNG_CHUNK_TYPE_IEND = byteArrayOf(
            0x49.toByte(), 0x45.toByte(),
            0x4e.toByte(), 0x44.toByte()
        )
        private const val PNG_CHUNK_TYPE_BYTE_LENGTH = 4
        private const val PNG_CHUNK_CRC_BYTE_LENGTH = 4

        // See https://developers.google.com/speed/webp/docs/riff_container, Section "WebP File Header"
        private val WEBP_SIGNATURE_1 =
            byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        private val WEBP_SIGNATURE_2 =
            byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        private const val WEBP_FILE_SIZE_BYTE_LENGTH = 4
        private val WEBP_CHUNK_TYPE_EXIF = byteArrayOf(
            0x45.toByte(), 0x58.toByte(),
            0x49.toByte(), 0x46.toByte()
        )
        private val WEBP_VP8_SIGNATURE = byteArrayOf(
            0x9d.toByte(), 0x01.toByte(),
            0x2a.toByte()
        )
        private const val WEBP_VP8L_SIGNATURE = 0x2f.toByte()
        private val WEBP_CHUNK_TYPE_VP8X = "VP8X".toByteArray(Charset.defaultCharset())
        private val WEBP_CHUNK_TYPE_VP8L = "VP8L".toByteArray(Charset.defaultCharset())
        private val WEBP_CHUNK_TYPE_VP8 = "VP8 ".toByteArray(Charset.defaultCharset())
        private val WEBP_CHUNK_TYPE_ANIM = "ANIM".toByteArray(Charset.defaultCharset())
        private val WEBP_CHUNK_TYPE_ANMF = "ANMF".toByteArray(Charset.defaultCharset())
        private const val WEBP_CHUNK_TYPE_VP8X_DEFAULT_LENGTH = 10
        private const val WEBP_CHUNK_TYPE_BYTE_LENGTH = 4
        private const val WEBP_CHUNK_SIZE_BYTE_LENGTH = 4

        private var sFormatterPrimary =
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        private var sFormatterSecondary: SimpleDateFormat

        // See Exchangeable image file format for digital still cameras: Exif version 2.2.
        // The following values are for parsing EXIF data area. There are tag groups in EXIF data area.
        // They are called "Image File Directory". They have multiple data formats to cover various
        // image metadata from GPS longitude to camera model name.
        // Types of Exif byte alignments (see JEITA CP-3451C Section 4.5.2)
        const val BYTE_ALIGN_II: Short = 0x4949 // II: Intel order
        const val BYTE_ALIGN_MM: Short = 0x4d4d // MM: Motorola order

        // TIFF Header Fixed Constant (see JEITA CP-3451C Section 4.5.2)
        const val START_CODE: Byte = 0x2a // 42
        private const val IFD_OFFSET = 8

        // Formats for the value in IFD entry (See TIFF 6.0 Section 2, "Image File Directory".)
        private const val IFD_FORMAT_BYTE = 1
        private const val IFD_FORMAT_STRING = 2
        private const val IFD_FORMAT_USHORT = 3
        private const val IFD_FORMAT_ULONG = 4
        private const val IFD_FORMAT_URATIONAL = 5
        private const val IFD_FORMAT_SBYTE = 6
        private const val IFD_FORMAT_UNDEFINED = 7
        private const val IFD_FORMAT_SSHORT = 8
        private const val IFD_FORMAT_SLONG = 9
        private const val IFD_FORMAT_SRATIONAL = 10
        private const val IFD_FORMAT_SINGLE = 11
        private const val IFD_FORMAT_DOUBLE = 12

        // Format indicating a new IFD entry (See Adobe PageMaker® 6.0 TIFF Technical Notes, "New Tag")
        private const val IFD_FORMAT_IFD = 13

        private const val SKIP_BUFFER_SIZE = 8192

        // Names for the data formats for debugging purpose.
        val IFD_FORMAT_NAMES: Array<String> = arrayOf(
            "", "BYTE", "STRING", "USHORT", "ULONG", "URATIONAL", "SBYTE", "UNDEFINED", "SSHORT",
            "SLONG", "SRATIONAL", "SINGLE", "DOUBLE", "IFD"
        )

        // Sizes of the components of each IFD value format
        val IFD_FORMAT_BYTES_PER_FORMAT: IntArray = intArrayOf(
            0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8, 1
        )

        val EXIF_ASCII_PREFIX: ByteArray = byteArrayOf(
            0x41, 0x53, 0x43, 0x49, 0x49, 0x0, 0x0, 0x0
        )

        // Primary image IFD TIFF tags (See JEITA CP-3451C Section 4.6.8 Tag Support Levels)
        private val IFD_TIFF_TAGS = arrayOf(
            // For below two, see TIFF 6.0 Spec Section 3: Bilevel Images.
            ExifTag(TAG_NEW_SUBFILE_TYPE, 254, IFD_FORMAT_ULONG),
            ExifTag(TAG_SUBFILE_TYPE, 255, IFD_FORMAT_ULONG),
            ExifTag(TAG_IMAGE_WIDTH, 256, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_IMAGE_LENGTH, 257, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_BITS_PER_SAMPLE, 258, IFD_FORMAT_USHORT),
            ExifTag(TAG_COMPRESSION, 259, IFD_FORMAT_USHORT),
            ExifTag(TAG_PHOTOMETRIC_INTERPRETATION, 262, IFD_FORMAT_USHORT),
            ExifTag(TAG_IMAGE_DESCRIPTION, 270, IFD_FORMAT_STRING),
            ExifTag(TAG_MAKE, 271, IFD_FORMAT_STRING),
            ExifTag(TAG_MODEL, 272, IFD_FORMAT_STRING),
            ExifTag(TAG_STRIP_OFFSETS, 273, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_ORIENTATION, 274, IFD_FORMAT_USHORT),
            ExifTag(TAG_SAMPLES_PER_PIXEL, 277, IFD_FORMAT_USHORT),
            ExifTag(TAG_ROWS_PER_STRIP, 278, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_STRIP_BYTE_COUNTS, 279, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_X_RESOLUTION, 282, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_Y_RESOLUTION, 283, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_PLANAR_CONFIGURATION, 284, IFD_FORMAT_USHORT),
            ExifTag(TAG_RESOLUTION_UNIT, 296, IFD_FORMAT_USHORT),
            ExifTag(TAG_TRANSFER_FUNCTION, 301, IFD_FORMAT_USHORT),
            ExifTag(TAG_SOFTWARE, 305, IFD_FORMAT_STRING),
            ExifTag(TAG_DATETIME, 306, IFD_FORMAT_STRING),
            ExifTag(TAG_ARTIST, 315, IFD_FORMAT_STRING),
            ExifTag(TAG_WHITE_POINT, 318, IFD_FORMAT_URATIONAL),
            ExifTag(
                TAG_PRIMARY_CHROMATICITIES,
                319,
                IFD_FORMAT_URATIONAL
            ),  // See Adobe PageMaker® 6.0 TIFF Technical Notes, Note 1.
            ExifTag(TAG_SUB_IFD_POINTER, 330, IFD_FORMAT_ULONG),
            ExifTag(TAG_JPEG_INTERCHANGE_FORMAT, 513, IFD_FORMAT_ULONG),
            ExifTag(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, 514, IFD_FORMAT_ULONG),
            ExifTag(TAG_Y_CB_CR_COEFFICIENTS, 529, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_Y_CB_CR_SUB_SAMPLING, 530, IFD_FORMAT_USHORT),
            ExifTag(TAG_Y_CB_CR_POSITIONING, 531, IFD_FORMAT_USHORT),
            ExifTag(TAG_REFERENCE_BLACK_WHITE, 532, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_COPYRIGHT, 33432, IFD_FORMAT_STRING),
            ExifTag(TAG_EXIF_IFD_POINTER, 34665, IFD_FORMAT_ULONG),
            ExifTag(TAG_GPS_INFO_IFD_POINTER, 34853, IFD_FORMAT_ULONG),  // RW2 file tags
            // See http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/PanasonicRaw.html)
            ExifTag(TAG_RW2_SENSOR_TOP_BORDER, 4, IFD_FORMAT_ULONG),
            ExifTag(TAG_RW2_SENSOR_LEFT_BORDER, 5, IFD_FORMAT_ULONG),
            ExifTag(TAG_RW2_SENSOR_BOTTOM_BORDER, 6, IFD_FORMAT_ULONG),
            ExifTag(TAG_RW2_SENSOR_RIGHT_BORDER, 7, IFD_FORMAT_ULONG),
            ExifTag(TAG_RW2_ISO, 23, IFD_FORMAT_USHORT),
            ExifTag(TAG_RW2_JPG_FROM_RAW, 46, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_XMP, 700, IFD_FORMAT_BYTE),
        )

        // Primary image IFD Exif Private tags (See JEITA CP-3451C Section 4.6.8 Tag Support Levels)
        private val IFD_EXIF_TAGS = arrayOf(
            ExifTag(TAG_EXPOSURE_TIME, 33434, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_F_NUMBER, 33437, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_EXPOSURE_PROGRAM, 34850, IFD_FORMAT_USHORT),
            ExifTag(TAG_SPECTRAL_SENSITIVITY, 34852, IFD_FORMAT_STRING),
            ExifTag(TAG_PHOTOGRAPHIC_SENSITIVITY, 34855, IFD_FORMAT_USHORT),
            ExifTag(TAG_OECF, 34856, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_SENSITIVITY_TYPE, 34864, IFD_FORMAT_USHORT),
            ExifTag(TAG_STANDARD_OUTPUT_SENSITIVITY, 34865, IFD_FORMAT_ULONG),
            ExifTag(TAG_RECOMMENDED_EXPOSURE_INDEX, 34866, IFD_FORMAT_ULONG),
            ExifTag(TAG_ISO_SPEED, 34867, IFD_FORMAT_ULONG),
            ExifTag(TAG_ISO_SPEED_LATITUDE_YYY, 34868, IFD_FORMAT_ULONG),
            ExifTag(TAG_ISO_SPEED_LATITUDE_ZZZ, 34869, IFD_FORMAT_ULONG),
            ExifTag(TAG_EXIF_VERSION, 36864, IFD_FORMAT_STRING),
            ExifTag(TAG_DATETIME_ORIGINAL, 36867, IFD_FORMAT_STRING),
            ExifTag(TAG_DATETIME_DIGITIZED, 36868, IFD_FORMAT_STRING),
            ExifTag(TAG_OFFSET_TIME, 36880, IFD_FORMAT_STRING),
            ExifTag(TAG_OFFSET_TIME_ORIGINAL, 36881, IFD_FORMAT_STRING),
            ExifTag(TAG_OFFSET_TIME_DIGITIZED, 36882, IFD_FORMAT_STRING),
            ExifTag(TAG_COMPONENTS_CONFIGURATION, 37121, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_COMPRESSED_BITS_PER_PIXEL, 37122, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_SHUTTER_SPEED_VALUE, 37377, IFD_FORMAT_SRATIONAL),
            ExifTag(TAG_APERTURE_VALUE, 37378, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_BRIGHTNESS_VALUE, 37379, IFD_FORMAT_SRATIONAL),
            ExifTag(TAG_EXPOSURE_BIAS_VALUE, 37380, IFD_FORMAT_SRATIONAL),
            ExifTag(TAG_MAX_APERTURE_VALUE, 37381, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_SUBJECT_DISTANCE, 37382, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_METERING_MODE, 37383, IFD_FORMAT_USHORT),
            ExifTag(TAG_LIGHT_SOURCE, 37384, IFD_FORMAT_USHORT),
            ExifTag(TAG_FLASH, 37385, IFD_FORMAT_USHORT),
            ExifTag(TAG_FOCAL_LENGTH, 37386, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_SUBJECT_AREA, 37396, IFD_FORMAT_USHORT),
            ExifTag(TAG_MAKER_NOTE, 37500, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_USER_COMMENT, 37510, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_SUBSEC_TIME, 37520, IFD_FORMAT_STRING),
            ExifTag(TAG_SUBSEC_TIME_ORIGINAL, 37521, IFD_FORMAT_STRING),
            ExifTag(TAG_SUBSEC_TIME_DIGITIZED, 37522, IFD_FORMAT_STRING),
            ExifTag(TAG_FLASHPIX_VERSION, 40960, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_COLOR_SPACE, 40961, IFD_FORMAT_USHORT),
            ExifTag(TAG_PIXEL_X_DIMENSION, 40962, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_PIXEL_Y_DIMENSION, 40963, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
            ExifTag(TAG_RELATED_SOUND_FILE, 40964, IFD_FORMAT_STRING),
            ExifTag(TAG_INTEROPERABILITY_IFD_POINTER, 40965, IFD_FORMAT_ULONG),
            ExifTag(TAG_FLASH_ENERGY, 41483, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_SPATIAL_FREQUENCY_RESPONSE, 41484, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_FOCAL_PLANE_X_RESOLUTION, 41486, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_FOCAL_PLANE_Y_RESOLUTION, 41487, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_FOCAL_PLANE_RESOLUTION_UNIT, 41488, IFD_FORMAT_USHORT),
            ExifTag(TAG_SUBJECT_LOCATION, 41492, IFD_FORMAT_USHORT),
            ExifTag(TAG_EXPOSURE_INDEX, 41493, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_SENSING_METHOD, 41495, IFD_FORMAT_USHORT),
            ExifTag(TAG_FILE_SOURCE, 41728, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_SCENE_TYPE, 41729, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_CFA_PATTERN, 41730, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_CUSTOM_RENDERED, 41985, IFD_FORMAT_USHORT),
            ExifTag(TAG_EXPOSURE_MODE, 41986, IFD_FORMAT_USHORT),
            ExifTag(TAG_WHITE_BALANCE, 41987, IFD_FORMAT_USHORT),
            ExifTag(TAG_DIGITAL_ZOOM_RATIO, 41988, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_FOCAL_LENGTH_IN_35MM_FILM, 41989, IFD_FORMAT_USHORT),
            ExifTag(TAG_SCENE_CAPTURE_TYPE, 41990, IFD_FORMAT_USHORT),
            ExifTag(TAG_GAIN_CONTROL, 41991, IFD_FORMAT_USHORT),
            ExifTag(TAG_CONTRAST, 41992, IFD_FORMAT_USHORT),
            ExifTag(TAG_SATURATION, 41993, IFD_FORMAT_USHORT),
            ExifTag(TAG_SHARPNESS, 41994, IFD_FORMAT_USHORT),
            ExifTag(TAG_DEVICE_SETTING_DESCRIPTION, 41995, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_SUBJECT_DISTANCE_RANGE, 41996, IFD_FORMAT_USHORT),
            ExifTag(TAG_IMAGE_UNIQUE_ID, 42016, IFD_FORMAT_STRING),
            ExifTag(TAG_CAMERA_OWNER_NAME, 42032, IFD_FORMAT_STRING),
            ExifTag(TAG_BODY_SERIAL_NUMBER, 42033, IFD_FORMAT_STRING),
            ExifTag(TAG_LENS_SPECIFICATION, 42034, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_LENS_MAKE, 42035, IFD_FORMAT_STRING),
            ExifTag(TAG_LENS_MODEL, 42036, IFD_FORMAT_STRING),
            ExifTag(TAG_GAMMA, 42240, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_DNG_VERSION, 50706, IFD_FORMAT_BYTE),
            ExifTag(TAG_DEFAULT_CROP_SIZE, 50720, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG)
        )

        // Primary image IFD GPS Info tags (See JEITA CP-3451C Section 4.6.6 Tag Support Levels)
        private val IFD_GPS_TAGS = arrayOf(
            ExifTag(TAG_GPS_VERSION_ID, 0, IFD_FORMAT_BYTE),
            ExifTag(
                TAG_GPS_LATITUDE_REF,
                1,
                IFD_FORMAT_STRING
            ),  // Allow SRATIONAL to be compatible with apps using wrong format and
            // even if it is negative, it may be valid latitude / longitude.
            ExifTag(TAG_GPS_LATITUDE, 2, IFD_FORMAT_URATIONAL, IFD_FORMAT_SRATIONAL),
            ExifTag(TAG_GPS_LONGITUDE_REF, 3, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_LONGITUDE, 4, IFD_FORMAT_URATIONAL, IFD_FORMAT_SRATIONAL),
            ExifTag(TAG_GPS_ALTITUDE_REF, 5, IFD_FORMAT_BYTE),
            ExifTag(TAG_GPS_ALTITUDE, 6, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_TIMESTAMP, 7, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_SATELLITES, 8, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_STATUS, 9, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_MEASURE_MODE, 10, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DOP, 11, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_SPEED_REF, 12, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_SPEED, 13, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_TRACK_REF, 14, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_TRACK, 15, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_IMG_DIRECTION_REF, 16, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_IMG_DIRECTION, 17, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_MAP_DATUM, 18, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DEST_LATITUDE_REF, 19, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DEST_LATITUDE, 20, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_DEST_LONGITUDE_REF, 21, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DEST_LONGITUDE, 22, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_DEST_BEARING_REF, 23, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DEST_BEARING, 24, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_DEST_DISTANCE_REF, 25, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DEST_DISTANCE, 26, IFD_FORMAT_URATIONAL),
            ExifTag(TAG_GPS_PROCESSING_METHOD, 27, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_GPS_AREA_INFORMATION, 28, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_GPS_DATESTAMP, 29, IFD_FORMAT_STRING),
            ExifTag(TAG_GPS_DIFFERENTIAL, 30, IFD_FORMAT_USHORT),
            ExifTag(TAG_GPS_H_POSITIONING_ERROR, 31, IFD_FORMAT_URATIONAL)
        )

        // Primary image IFD Interoperability tag (See JEITA CP-3451C Section 4.6.8 Tag Support Levels)
        private val IFD_INTEROPERABILITY_TAGS = arrayOf(
            ExifTag(TAG_INTEROPERABILITY_INDEX, 1, IFD_FORMAT_STRING)
        )

        // IFD Thumbnail tags (See JEITA CP-3451C Section 4.6.8 Tag Support Levels)
        private val IFD_THUMBNAIL_TAGS =
            arrayOf( // For below two, see TIFF 6.0 Spec Section 3: Bilevel Images.
                ExifTag(TAG_NEW_SUBFILE_TYPE, 254, IFD_FORMAT_ULONG),
                ExifTag(TAG_SUBFILE_TYPE, 255, IFD_FORMAT_ULONG),
                ExifTag(TAG_THUMBNAIL_IMAGE_WIDTH, 256, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
                ExifTag(TAG_THUMBNAIL_IMAGE_LENGTH, 257, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
                ExifTag(TAG_BITS_PER_SAMPLE, 258, IFD_FORMAT_USHORT),
                ExifTag(TAG_COMPRESSION, 259, IFD_FORMAT_USHORT),
                ExifTag(TAG_PHOTOMETRIC_INTERPRETATION, 262, IFD_FORMAT_USHORT),
                ExifTag(TAG_IMAGE_DESCRIPTION, 270, IFD_FORMAT_STRING),
                ExifTag(TAG_MAKE, 271, IFD_FORMAT_STRING),
                ExifTag(TAG_MODEL, 272, IFD_FORMAT_STRING),
                ExifTag(TAG_STRIP_OFFSETS, 273, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
                ExifTag(TAG_THUMBNAIL_ORIENTATION, 274, IFD_FORMAT_USHORT),
                ExifTag(TAG_SAMPLES_PER_PIXEL, 277, IFD_FORMAT_USHORT),
                ExifTag(TAG_ROWS_PER_STRIP, 278, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
                ExifTag(TAG_STRIP_BYTE_COUNTS, 279, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG),
                ExifTag(TAG_X_RESOLUTION, 282, IFD_FORMAT_URATIONAL),
                ExifTag(TAG_Y_RESOLUTION, 283, IFD_FORMAT_URATIONAL),
                ExifTag(TAG_PLANAR_CONFIGURATION, 284, IFD_FORMAT_USHORT),
                ExifTag(TAG_RESOLUTION_UNIT, 296, IFD_FORMAT_USHORT),
                ExifTag(TAG_TRANSFER_FUNCTION, 301, IFD_FORMAT_USHORT),
                ExifTag(TAG_SOFTWARE, 305, IFD_FORMAT_STRING),
                ExifTag(TAG_DATETIME, 306, IFD_FORMAT_STRING),
                ExifTag(TAG_ARTIST, 315, IFD_FORMAT_STRING),
                ExifTag(TAG_WHITE_POINT, 318, IFD_FORMAT_URATIONAL),
                ExifTag(
                    TAG_PRIMARY_CHROMATICITIES,
                    319,
                    IFD_FORMAT_URATIONAL
                ),  // See Adobe PageMaker® 6.0 TIFF Technical Notes, Note 1.
                ExifTag(TAG_SUB_IFD_POINTER, 330, IFD_FORMAT_ULONG),
                ExifTag(TAG_JPEG_INTERCHANGE_FORMAT, 513, IFD_FORMAT_ULONG),
                ExifTag(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, 514, IFD_FORMAT_ULONG),
                ExifTag(TAG_Y_CB_CR_COEFFICIENTS, 529, IFD_FORMAT_URATIONAL),
                ExifTag(TAG_Y_CB_CR_SUB_SAMPLING, 530, IFD_FORMAT_USHORT),
                ExifTag(TAG_Y_CB_CR_POSITIONING, 531, IFD_FORMAT_USHORT),
                ExifTag(TAG_REFERENCE_BLACK_WHITE, 532, IFD_FORMAT_URATIONAL),
                ExifTag(TAG_COPYRIGHT, 33432, IFD_FORMAT_STRING),
                ExifTag(TAG_EXIF_IFD_POINTER, 34665, IFD_FORMAT_ULONG),
                ExifTag(TAG_GPS_INFO_IFD_POINTER, 34853, IFD_FORMAT_ULONG),
                ExifTag(TAG_DNG_VERSION, 50706, IFD_FORMAT_BYTE),
                ExifTag(TAG_DEFAULT_CROP_SIZE, 50720, IFD_FORMAT_USHORT, IFD_FORMAT_ULONG)
            )

        // RAF file tag (See piex.cc line 372)
        private val TAG_RAF_IMAGE_SIZE = ExifTag(TAG_STRIP_OFFSETS, 273, IFD_FORMAT_USHORT)

        // ORF file tags (See http://www.exiv2.org/tags-olympus.html)
        private val ORF_MAKER_NOTE_TAGS = arrayOf(
            ExifTag(TAG_ORF_THUMBNAIL_IMAGE, 256, IFD_FORMAT_UNDEFINED),
            ExifTag(TAG_ORF_CAMERA_SETTINGS_IFD_POINTER, 8224, IFD_FORMAT_ULONG),
            ExifTag(TAG_ORF_IMAGE_PROCESSING_IFD_POINTER, 8256, IFD_FORMAT_ULONG)
        )
        private val ORF_CAMERA_SETTINGS_TAGS = arrayOf(
            ExifTag(TAG_ORF_PREVIEW_IMAGE_START, 257, IFD_FORMAT_ULONG),
            ExifTag(TAG_ORF_PREVIEW_IMAGE_LENGTH, 258, IFD_FORMAT_ULONG)
        )
        private val ORF_IMAGE_PROCESSING_TAGS = arrayOf(
            ExifTag(TAG_ORF_ASPECT_FRAME, 4371, IFD_FORMAT_USHORT)
        )

        // PEF file tag (See http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/Pentax.html)
        private val PEF_TAGS = arrayOf(
            ExifTag(TAG_COLOR_SPACE, 55, IFD_FORMAT_USHORT)
        )

        const val IFD_TYPE_PRIMARY: Int = 0
        private const val IFD_TYPE_EXIF = 1
        private const val IFD_TYPE_GPS = 2
        private const val IFD_TYPE_INTEROPERABILITY = 3
        const val IFD_TYPE_THUMBNAIL: Int = 4
        const val IFD_TYPE_PREVIEW: Int = 5
        private const val IFD_TYPE_ORF_MAKER_NOTE = 6
        private const val IFD_TYPE_ORF_CAMERA_SETTINGS = 7
        private const val IFD_TYPE_ORF_IMAGE_PROCESSING = 8
        private const val IFD_TYPE_PEF = 9

        // List of Exif tag groups
        val EXIF_TAGS: Array<Array<ExifTag>> = arrayOf(
            IFD_TIFF_TAGS, IFD_EXIF_TAGS, IFD_GPS_TAGS, IFD_INTEROPERABILITY_TAGS,
            IFD_THUMBNAIL_TAGS, IFD_TIFF_TAGS, ORF_MAKER_NOTE_TAGS, ORF_CAMERA_SETTINGS_TAGS,
            ORF_IMAGE_PROCESSING_TAGS, PEF_TAGS
        )

        // List of tags for pointing to the other image file directory offset.
        private val EXIF_POINTER_TAGS = arrayOf(
            ExifTag(TAG_SUB_IFD_POINTER, 330, IFD_FORMAT_ULONG),
            ExifTag(TAG_EXIF_IFD_POINTER, 34665, IFD_FORMAT_ULONG),
            ExifTag(TAG_GPS_INFO_IFD_POINTER, 34853, IFD_FORMAT_ULONG),
            ExifTag(TAG_INTEROPERABILITY_IFD_POINTER, 40965, IFD_FORMAT_ULONG),
            ExifTag(TAG_ORF_CAMERA_SETTINGS_IFD_POINTER, 8224, IFD_FORMAT_BYTE),
            ExifTag(TAG_ORF_IMAGE_PROCESSING_IFD_POINTER, 8256, IFD_FORMAT_BYTE)
        )

        // Mappings from tag number to tag name and each item represents one IFD tag group.
        private val sExifTagMapsForReading: Array<HashMap<Int, ExifTag>> =
            Array(EXIF_TAGS.size) { HashMap<Int, ExifTag>() }

        // Mappings from tag name to tag number and each item represents one IFD tag group.
        private val sExifTagMapsForWriting: Array<HashMap<String, ExifTag>> =
            Array(EXIF_TAGS.size) { HashMap<String, ExifTag>() }
        private val sTagSetForCompatibility = HashSet(
            Arrays.asList(
                TAG_F_NUMBER, TAG_DIGITAL_ZOOM_RATIO, TAG_EXPOSURE_TIME, TAG_SUBJECT_DISTANCE,
                TAG_GPS_TIMESTAMP
            )
        )

        // Mappings from tag number to IFD type for pointer tags.
        private val sExifPointerTagMap: HashMap<Int?, Int?> = HashMap()

        // See JPEG File Interchange Format Version 1.02.
        // The following values are defined for handling JPEG streams. In this implementation, we are
        // not only getting information from EXIF but also from some JPEG special segments such as
        // MARKER_COM for user comment and MARKER_SOFx for image width and height.
        val ASCII: Charset = Charset.forName("US-ASCII")

        // Identifier for EXIF APP1 segment in JPEG
        val IDENTIFIER_EXIF_APP1: ByteArray = "Exif\u0000\u0000".toByteArray(ASCII)

        // Identifier for XMP APP1 segment in JPEG
        private val IDENTIFIER_XMP_APP1 = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(ASCII)

        // JPEG segment markers, that each marker consumes two bytes beginning with 0xff and ending with
        // the indicator. There is no SOF4, SOF8, SOF16 markers in JPEG and SOFx markers indicates start
        // of frame(baseline DCT) and the image size info exists in its beginning part.
        const val MARKER: Byte = 0xff.toByte()
        private const val MARKER_SOI = 0xd8.toByte()
        private const val MARKER_SOF0 = 0xc0.toByte()
        private const val MARKER_SOF1 = 0xc1.toByte()
        private const val MARKER_SOF2 = 0xc2.toByte()
        private const val MARKER_SOF3 = 0xc3.toByte()
        private const val MARKER_SOF5 = 0xc5.toByte()
        private const val MARKER_SOF6 = 0xc6.toByte()
        private const val MARKER_SOF7 = 0xc7.toByte()
        private const val MARKER_SOF9 = 0xc9.toByte()
        private const val MARKER_SOF10 = 0xca.toByte()
        private const val MARKER_SOF11 = 0xcb.toByte()
        private const val MARKER_SOF13 = 0xcd.toByte()
        private const val MARKER_SOF14 = 0xce.toByte()
        private const val MARKER_SOF15 = 0xcf.toByte()
        private const val MARKER_SOS = 0xda.toByte()
        const val MARKER_APP1: Byte = 0xe1.toByte()
        private const val MARKER_COM = 0xfe.toByte()
        const val MARKER_EOI: Byte = 0xd9.toByte()

        // Supported Image File Types
        const val IMAGE_TYPE_UNKNOWN: Int = 0
        const val IMAGE_TYPE_ARW: Int = 1
        const val IMAGE_TYPE_CR2: Int = 2
        const val IMAGE_TYPE_DNG: Int = 3
        const val IMAGE_TYPE_JPEG: Int = 4
        const val IMAGE_TYPE_NEF: Int = 5
        const val IMAGE_TYPE_NRW: Int = 6
        const val IMAGE_TYPE_ORF: Int = 7
        const val IMAGE_TYPE_PEF: Int = 8
        const val IMAGE_TYPE_RAF: Int = 9
        const val IMAGE_TYPE_RW2: Int = 10
        const val IMAGE_TYPE_SRW: Int = 11
        const val IMAGE_TYPE_HEIF: Int = 12
        const val IMAGE_TYPE_PNG: Int = 13
        const val IMAGE_TYPE_WEBP: Int = 14

        init {
            sFormatterPrimary.timeZone =
                TimeZone.getTimeZone("UTC")
            sFormatterSecondary = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            sFormatterSecondary.timeZone =
                TimeZone.getTimeZone("UTC")

            // Build up the hash tables to look up Exif tags for reading Exif tags.
            for (ifdType in EXIF_TAGS.indices) {
                sExifTagMapsForReading[ifdType] = HashMap()
                sExifTagMapsForWriting[ifdType] = HashMap()
                for (tag in EXIF_TAGS[ifdType]) {
                    sExifTagMapsForReading[ifdType][tag.number] =
                        tag
                    sExifTagMapsForWriting[ifdType][tag.name] =
                        tag
                }
            }

            // Build up the hash table to look up Exif pointer tags.
            sExifPointerTagMap[EXIF_POINTER_TAGS[0].number] = IFD_TYPE_PREVIEW // 330
            sExifPointerTagMap[EXIF_POINTER_TAGS[1].number] = IFD_TYPE_EXIF // 34665
            sExifPointerTagMap[EXIF_POINTER_TAGS[2].number] = IFD_TYPE_GPS // 34853
            sExifPointerTagMap[EXIF_POINTER_TAGS[3].number] =
                IFD_TYPE_INTEROPERABILITY // 40965
            sExifPointerTagMap[EXIF_POINTER_TAGS[4].number] =
                IFD_TYPE_ORF_CAMERA_SETTINGS // 8224
            sExifPointerTagMap[EXIF_POINTER_TAGS[5].number] =
                IFD_TYPE_ORF_IMAGE_PROCESSING // 8256
        }

        // Pattern to check non zero timestamp
        private val NON_ZERO_TIME_PATTERN: Pattern = Pattern.compile(".*[1-9].*")

        // Pattern to check gps timestamp
        private val GPS_TIMESTAMP_PATTERN: Pattern = Pattern.compile("^(\\d{2}):(\\d{2}):(\\d{2})$")

        // Pattern to check date time primary format (e.g. 2020:01:01 00:00:00)
        private val DATETIME_PRIMARY_FORMAT_PATTERN: Pattern =
            Pattern.compile("^(\\d{4}):(\\d{2}):(\\d{2})\\s(\\d{2}):(\\d{2}):(\\d{2})$")

        // Pattern to check date time secondary format (e.g. 2020-01-01 00:00:00)
        private val DATETIME_SECONDARY_FORMAT_PATTERN: Pattern =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})\\s(\\d{2}):(\\d{2}):(\\d{2})$")
        private const val DATETIME_VALUE_STRING_LENGTH = 19

        /**
         * Returns whether ExifInterface currently supports reading data from the specified mime type
         * or not.
         *
         * @param mimeType the string value of mime type
         */
        fun isSupportedMimeType(mimeType: String): Boolean {
            if (mimeType == null) {
                throw NullPointerException("mimeType shouldn't be null")
            }

            return when (mimeType.lowercase()) {
                "image/jpeg", "image/x-adobe-dng", "image/x-canon-cr2", "image/x-nikon-nef", "image/x-nikon-nrw", "image/x-sony-arw", "image/x-panasonic-rw2", "image/x-olympus-orf", "image/x-pentax-pef", "image/x-samsung-srw", "image/x-fuji-raf", "image/heic", "image/heif", "image/png", "image/webp" -> true
                else -> false
            }
        }

        private fun isSeekableFD(fd: FileDescriptor): Boolean {
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    ExifInterfaceUtils.Api21Impl.lseek(fd, 0, OsConstants.SEEK_CUR)
                    return true
                } catch (e: Exception) {
                    if (DEBUG) {
                        Log.d(TAG, "The file descriptor for the given input is not seekable")
                    }
                    return false
                }
            }
            return false
        }

        private fun parseDateTime(
            dateTimeString: String?, subSecs: String?,
            offsetString: String?
        ): Long? {
            if (dateTimeString == null || !NON_ZERO_TIME_PATTERN.matcher(dateTimeString)
                    .matches()
            ) {
                return null
            }

            val pos = ParsePosition(0)
            try {
                // The exif field is in local time. Parsing it as if it is UTC will yield time
                // since 1/1/1970 local time
                var dateTime = sFormatterPrimary.parse(dateTimeString, pos)
                if (dateTime == null) {
                    dateTime = sFormatterSecondary.parse(dateTimeString, pos)
                    if (dateTime == null) {
                        return null
                    }
                }
                var msecs = dateTime.time
                if (offsetString != null) {
                    val sign = offsetString.substring(0, 1)
                    val hour = offsetString.substring(1, 3).toInt()
                    val min = offsetString.substring(4, 6).toInt()
                    if (("+" == sign || "-" == sign)
                        && ":" == offsetString.substring(3, 4)
                        && hour <= 14 /* max UTC hour value */) {
                        msecs += ((hour * 60 + min) * 60 * 1000 * (if ("-" == sign) 1 else -1)).toLong()
                    }
                }

                if (subSecs != null) {
                    msecs += ExifInterfaceUtils.parseSubSeconds(subSecs)
                }
                return msecs
            } catch (e: IllegalArgumentException) {
                return null
            }
        }

        private fun convertRationalLatLonToDouble(rationalString: String, ref: String): Double {
            try {
                val parts = rationalString.split(",".toRegex()).toTypedArray()
                var pair = parts[0].split("/".toRegex()).toTypedArray()
                val degrees =
                    pair[0].trim { it <= ' ' }.toDouble() / pair[1].trim { it <= ' ' }.toDouble()

                pair = parts[1].split("/".toRegex()).toTypedArray()
                val minutes =
                    pair[0].trim { it <= ' ' }.toDouble() / pair[1].trim { it <= ' ' }.toDouble()

                pair = parts[2].split("/".toRegex()).toTypedArray()
                val seconds =
                    pair[0].trim { it <= ' ' }.toDouble() / pair[1].trim { it <= ' ' }.toDouble()

                val result = degrees + (minutes / 60.0) + (seconds / 3600.0)
                return if ((ref == "S" || ref == "W")) {
                    -result
                } else if (ref == "N" || ref == "E") {
                    result
                } else {
                    // Not valid
                    throw IllegalArgumentException()
                }
            } catch (e: NumberFormatException) {
                // Not valid
                throw IllegalArgumentException()
            } catch (e: ArrayIndexOutOfBoundsException) {
                throw IllegalArgumentException()
            }
        }

        /**
         * This method looks at the first 3 bytes to determine if this file is a JPEG file.
         * See http://www.media.mit.edu/pia/Research/deepview/exif.html, "JPEG format and Marker"
         */
        @Throws(IOException::class)
        private fun isJpegFormat(signatureCheckBytes: ByteArray): Boolean {
            for (i in JPEG_SIGNATURE.indices) {
                if (signatureCheckBytes[i] != JPEG_SIGNATURE[i]) {
                    return false
                }
            }
            return true
        }

        @Throws(IOException::class)
        private fun isExifDataOnly(`in`: BufferedInputStream): Boolean {
            `in`.mark(IDENTIFIER_EXIF_APP1.size)
            val signatureCheckBytes = ByteArray(IDENTIFIER_EXIF_APP1.size)
            `in`.read(signatureCheckBytes)
            `in`.reset()
            for (i in IDENTIFIER_EXIF_APP1.indices) {
                if (signatureCheckBytes[i] != IDENTIFIER_EXIF_APP1[i]) {
                    return false
                }
            }
            return true
        }

        /**
         * Determines the data format of EXIF entry value.
         *
         * @param entryValue The value to be determined.
         * @return Returns two data formats guessed as a pair in integer. If there is no two candidate
         * data formats for the given entry value, returns `-1` in the second of the pair.
         */
        private fun guessDataFormat(entryValue: String): Pair<Int, Int> {
            // See TIFF 6.0 Section 2, "Image File Directory".
            // Take the first component if there are more than one component.
            if (entryValue.contains(",")) {
                val entryValues = entryValue.split(",".toRegex()).toTypedArray()
                var dataFormat = guessDataFormat(
                    entryValues[0]
                )
                if (dataFormat.first == IFD_FORMAT_STRING) {
                    return dataFormat
                }
                for (i in 1..<entryValues.size) {
                    val guessDataFormat = guessDataFormat(
                        entryValues[i]
                    )
                    var first = -1
                    var second = -1
                    if (guessDataFormat.first == dataFormat.first
                        || guessDataFormat.second == dataFormat.first
                    ) {
                        first = dataFormat.first
                    }
                    if (dataFormat.second != -1 && (guessDataFormat.first == dataFormat.second
                                || guessDataFormat.second == dataFormat.second)
                    ) {
                        second = dataFormat.second
                    }
                    if (first == -1 && second == -1) {
                        return Pair(IFD_FORMAT_STRING, -1)
                    }
                    if (first == -1) {
                        dataFormat = Pair(second, -1)
                        continue
                    }
                    if (second == -1) {
                        dataFormat = Pair(first, -1)
                        continue
                    }
                }
                return dataFormat
            }

            if (entryValue.contains("/")) {
                val rationalNumber = entryValue.split("/".toRegex()).toTypedArray()
                if (rationalNumber.size == 2) {
                    try {
                        val numerator = rationalNumber[0].toDouble().toLong()
                        val denominator = rationalNumber[1].toDouble().toLong()
                        if (numerator < 0L || denominator < 0L) {
                            return Pair(IFD_FORMAT_SRATIONAL, -1)
                        }
                        if (numerator > Int.MAX_VALUE || denominator > Int.MAX_VALUE) {
                            return Pair(IFD_FORMAT_URATIONAL, -1)
                        }
                        return Pair(IFD_FORMAT_SRATIONAL, IFD_FORMAT_URATIONAL)
                    } catch (e: NumberFormatException) {
                        // Ignored
                    }
                }
                return Pair(IFD_FORMAT_STRING, -1)
            }
            try {
                val longValue = entryValue.toLong()
                if (longValue >= 0 && longValue <= 65535) {
                    return Pair(IFD_FORMAT_USHORT, IFD_FORMAT_ULONG)
                }
                if (longValue < 0) {
                    return Pair(IFD_FORMAT_SLONG, -1)
                }
                return Pair(IFD_FORMAT_ULONG, -1)
            } catch (e: NumberFormatException) {
                // Ignored
            }
            try {
                entryValue.toDouble()
                return Pair(IFD_FORMAT_DOUBLE, -1)
            } catch (e: NumberFormatException) {
                // Ignored
            }
            return Pair(IFD_FORMAT_STRING, -1)
        }

        /**
         * Parsing EXIF data requires seek (moving to any position in the stream), so all MIME
         * types should support seek via mark/reset, unless the MIME type specifies the position and
         * length of the EXIF data and the EXIF data can be read from the file and wrapped with a
         * ByteArrayInputStream.
         */
        private fun shouldSupportSeek(mimeType: Int): Boolean {
            if (mimeType == IMAGE_TYPE_JPEG || mimeType == IMAGE_TYPE_RAF || mimeType == IMAGE_TYPE_PNG || mimeType == IMAGE_TYPE_WEBP) {
                return false
            }
            return true
        }

        private fun isSupportedFormatForSavingAttributes(mimeType: Int): Boolean {
            if (mimeType == IMAGE_TYPE_JPEG || mimeType == IMAGE_TYPE_PNG || mimeType == IMAGE_TYPE_WEBP) {
                return true
            }
            return false
        }
    }
}
