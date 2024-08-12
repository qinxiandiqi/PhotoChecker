package cn.qinxiandiqi.photochecker.feature.home

/**
 *
 * created by Jianan on 2024/3/19
 */
sealed class HomeUIState {
    data object Empty : HomeUIState()
    class Loading(val photoInfo: PhotoInfo) : HomeUIState()
    class Success(val photoInfo: PhotoInfo) : HomeUIState()
    class Error(val photoInfo: PhotoInfo?) : HomeUIState()
}