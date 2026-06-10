/*
 * Copyright 2020 The Android Open Source Project
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

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.annotation.DoNotInline
import java.io.Closeable
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

internal object ExifInterfaceUtils {
    private const val TAG = "ExifInterfaceUtils"

    /**
     * Copies all of the bytes from input to out. Neither stream is closed.
     * Returns the total number of bytes transferred.
     */
    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Int {
        var total = 0
        val buffer = ByteArray(8192)
        var c: Int
        while ((input.read(buffer).also { c = it }) != -1) {
            total += c
            output.write(buffer, 0, c)
        }
        return total
    }

    /**
     * Copies the given number of the bytes from `in` to `out`. Neither stream is
     * closed.
     */
    @Throws(IOException::class)
    fun copy(inputStream: InputStream, outputStream: OutputStream, numBytes: Int) {
        var remainder = numBytes
        val buffer = ByteArray(8192)
        while (remainder > 0) {
            val bytesToRead = min(remainder, 8192)
            val bytesRead = inputStream.read(buffer, 0, bytesToRead)
            if (bytesRead != bytesToRead) {
                throw IOException(
                    "Failed to copy the given amount of bytes from the input"
                            + " stream to the output stream."
                )
            }
            remainder -= bytesRead
            outputStream.write(buffer, 0, bytesRead)
        }
    }

    /**
     * Convert given int[] to long[]. If long[] is given, just return it.
     * Return null for other types of input.
     */
    fun convertToLongArray(inputObj: Any?): LongArray? {
        if (inputObj is IntArray) {
            val result = LongArray(inputObj.size)
            for (i in inputObj.indices) {
                result[i] = inputObj[i].toLong()
            }
            return result
        } else if (inputObj is LongArray) {
            return inputObj
        }
        return null
    }

    fun startsWith(cur: ByteArray?, bytes: ByteArray?): Boolean {
        if (cur == null || bytes == null) {
            return false
        }
        if (cur.size < bytes.size) {
            return false
        }
        for (i in bytes.indices) {
            if (cur[i] != bytes[i]) {
                return false
            }
        }
        return true
    }

    fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (i in bytes.indices) {
            sb.append(String.format("%02x", bytes[i]))
        }
        return sb.toString()
    }

    fun parseSubSeconds(subSec: String): Long {
        try {
            val len = min(subSec.length, 3)
            var sub = subSec.substring(0, len).toLong()
            for (i in len..2) {
                sub *= 10
            }
            return sub
        } catch (e: NumberFormatException) {
            // Ignored
        }
        return 0L
    }


    /**
     * Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null.
     */
    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (rethrown: RuntimeException) {
                throw rethrown
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Closes a file descriptor that has been duplicated.
     */
    fun closeFileDescriptor(fd: FileDescriptor?) {
        // Os.dup and Os.close was introduced in API 21 so this method shouldn't be called
        // in API < 21.
        try {
            Api21Impl.close(fd)
            // Catching ErrnoException will raise error in API < 21
        } catch (ex: Exception) {
            Log.e(TAG, "Error closing fd.")
        }
    }

    internal object Api21Impl {
        @DoNotInline
        @Throws(ErrnoException::class)
        fun dup(fileDescriptor: FileDescriptor?): FileDescriptor {
            return Os.dup(fileDescriptor)
        }

        @DoNotInline
        @Throws(ErrnoException::class)
        fun lseek(fd: FileDescriptor?, offset: Long, whence: Int): Long {
            return Os.lseek(fd, offset, whence)
        }

        @DoNotInline
        @Throws(ErrnoException::class)
        fun close(fd: FileDescriptor?) {
            Os.close(fd)
        }
    }

    internal object Api23Impl {
        @DoNotInline
        fun setDataSource(retriever: MediaMetadataRetriever, dataSource: MediaDataSource?) {
            retriever.setDataSource(dataSource)
        }
    }
}
