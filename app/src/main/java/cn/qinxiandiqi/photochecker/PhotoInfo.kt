package cn.qinxiandiqi.photochecker

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.net.URI

/**
 *
 * created by Jianan on 2024/3/14
 */
class PhotoInfo(
    val uri: Uri
) {

    fun getExif() {
        ExifInterface(File(URI(uri.toString())))
    }
}