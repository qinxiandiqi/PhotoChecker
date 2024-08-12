package cn.qinxiandiqi.photochecker.feature.home

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 *
 * created by Jianan on 2024/3/17
 */
class HomeViewModel(private val application: Application) : AndroidViewModel(application) {

    private val _uiStateFlow = MutableStateFlow<HomeUIState>(HomeUIState.Empty)

    val photoInfoFlow: StateFlow<HomeUIState> = _uiStateFlow.asStateFlow()

    fun updatePhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val photoInfo = PhotoInfo(uri)
            try {
                _uiStateFlow.value = HomeUIState.Loading(photoInfo)
                photoInfo.parseExif(application.contentResolver)
                _uiStateFlow.value = HomeUIState.Success(photoInfo)
            } catch (e: Throwable) {
                Log.e("HOME", "Error:${e.stackTraceToString()}")
                _uiStateFlow.value = HomeUIState.Error(photoInfo)
            }
        }
    }
}