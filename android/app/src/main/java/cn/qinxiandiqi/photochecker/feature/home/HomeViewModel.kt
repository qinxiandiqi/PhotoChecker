package cn.qinxiandiqi.photochecker.feature.home

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.qinxiandiqi.photochecker.feature.home.model.ExifRemovalMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(private val application: Application) : AndroidViewModel(application) {

    private val _uiStateFlow = MutableStateFlow<HomeUIState>(HomeUIState.Empty)
    val uiStateFlow: StateFlow<HomeUIState> = _uiStateFlow.asStateFlow()

    private val _removalState = MutableStateFlow<RemovalState>(RemovalState.Idle)
    val removalState: StateFlow<RemovalState> = _removalState.asStateFlow()

    fun updatePhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiStateFlow.value = HomeUIState.Loading(uri)
            try {
                val result = ExifAnalysisService.analyze(
                    uri, application.contentResolver, application
                )
                _uiStateFlow.value = HomeUIState.Success(result)
            } catch (e: Throwable) {
                Log.e("HOME", "Error: ${e.stackTraceToString()}")
                _uiStateFlow.value = HomeUIState.Error(uri, e.message ?: "Unknown error")
            }
        }
    }

    fun removeExif(sourceUri: Uri, mode: ExifRemovalMode) {
        viewModelScope.launch(Dispatchers.IO) {
            _removalState.value = RemovalState.Removing
            try {
                val file = ExifRemovalService.removeExif(
                    sourceUri, application.contentResolver, application, mode
                )
                _removalState.value = RemovalState.Done(file, mode)
            } catch (e: Throwable) {
                Log.e("HOME", "Removal error: ${e.stackTraceToString()}")
                _removalState.value = RemovalState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun shareCleanedFile(file: File) {
        val authority = "${application.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(application, authority, file)
        val (mimeType, _) = detectImageFormat(file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, null)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(chooser)
    }

    /**
     * Saves the cleaned image into the device's MediaStore (Pictures/PhotoChecker). Returns
     * the resulting content [Uri] on success, or null on failure. Runs the IO on a background
     * dispatcher and reports the outcome via [onResult].
     */
    fun saveToGallery(file: File, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = runCatching {
                val resolver = application.contentResolver
                // Detect the REAL format from magic bytes: the temp file is always named
                // *.jpg regardless of the source, so trusting the extension would write a
                // PNG/WEBP as image/jpeg and the gallery would show it as corrupted.
                val (mimeType, extension) = detectImageFormat(file)
                val displayName = "PhotoChecker_${System.currentTimeMillis()}.$extension"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoChecker")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    onResult(false)
                    return@runCatching
                }

                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: run {
                    onResult(false)
                    return@runCatching
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                onResult(true)
            }.getOrElse {
                Log.e("HOME", "Save to gallery error: ${it.stackTraceToString()}")
                onResult(false)
            }
            success
        }
    }

    /**
     * Sniffs the image format from the file's leading bytes. The temp file produced by
     * ExifRemovalService is always named *.jpg, but the bytes may actually be PNG/WEBP
     * (the source is copied verbatim). Returns the MIME type and canonical extension.
     */
    private fun detectImageFormat(file: File): Pair<String, String> {
        val header = ByteArray(12)
        file.inputStream().use { it.read(header) }
        return when {
            // JPEG: FF D8
            header.size >= 2 &&
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() ->
                "image/jpeg" to "jpg"
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            header.size >= 8 &&
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() ->
                "image/png" to "png"
            // WEBP: "RIFF" .... "WEBP"
            header.size >= 12 &&
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() ->
                "image/webp" to "webp"
            else -> "image/jpeg" to "jpg"
        }
    }

    fun resetRemovalState() {
        // Clean up temp file from previous operation
        val current = _removalState.value
        if (current is RemovalState.Done) {
            current.file.delete()
        }
        _removalState.value = RemovalState.Idle
    }
}

sealed class RemovalState {
    data object Idle : RemovalState()
    data object Removing : RemovalState()
    data class Done(val file: File, val mode: ExifRemovalMode) : RemovalState()
    data class Error(val message: String) : RemovalState()
}
