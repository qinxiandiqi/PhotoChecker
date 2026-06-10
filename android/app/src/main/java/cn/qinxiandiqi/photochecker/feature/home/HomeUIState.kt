package cn.qinxiandiqi.photochecker.feature.home

import android.net.Uri
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult

sealed class HomeUIState {
    data object Empty : HomeUIState()
    data class Loading(val uri: Uri) : HomeUIState()
    data class Success(val result: ExifAnalysisResult) : HomeUIState()
    data class Error(val uri: Uri?, val message: String) : HomeUIState()
}
