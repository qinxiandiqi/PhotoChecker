package cn.qinxiandiqi.photochecker.feature.home

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import cn.qinxiandiqi.lib.exif.ExifInterface
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
        // Copy source image to a temp file (needed for file-based ExifInterface that supports writing)
        val tempFile = File(context.cacheDir, "cleaned_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot open source URI")

        try {
            // Open with file-based constructor (supports saveAttributes)
            val exifInterface = ExifInterface(tempFile)

            val tagsToRemove = when (mode) {
                ExifRemovalMode.GPS_ONLY -> ExifTagRegistry.gpsTags
                ExifRemovalMode.PERSONAL_ONLY -> ExifTagRegistry.personalTags
                ExifRemovalMode.ALL -> {
                    // All known tags from all IFD groups
                    ExifInterface.EXIF_TAGS.flatMap { group ->
                        group.map { it.name }
                    }.toSet()
                }
            }

            for (tag in tagsToRemove) {
                exifInterface.setAttribute(tag, null)
            }
            exifInterface.saveAttributes()

            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private class IOException(message: String) : java.io.IOException(message)
}
