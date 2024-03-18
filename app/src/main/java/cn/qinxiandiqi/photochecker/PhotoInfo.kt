package cn.qinxiandiqi.photochecker

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import cn.qinxiandiqi.lib.exif.ExifInterface

/**
 *
 * created by Jianan on 2024/3/14
 */
class PhotoInfo(
    val uri: Uri
) {

    fun parseExif(contentResolver: ContentResolver) {
        try {
            contentResolver.openInputStream(uri)?.use {
                val exifInterface = ExifInterface(it)
                for (tagGroup in ExifInterface.EXIF_TAGS) {
                    for (tag in tagGroup) {
                        val value = exifInterface.getAttribute(tag.name)
                        if (value != null) {
                            Log.d("PhotoInfo", "parseExif: ${tag.name} = $value")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("PhotoInfo", "parseExif: $e")
        }
    }
}