package cn.qinxiandiqi.photochecker.feature.home

import android.app.Application
import android.content.Intent
import android.net.Uri
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
        val mimeType = when {
            file.name.endsWith(".png", ignoreCase = true) -> "image/png"
            file.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, null)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(chooser)
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
