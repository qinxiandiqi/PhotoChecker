package cn.qinxiandiqi.photochecker

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 *
 * created by Jianan on 2024/3/17
 */
class PhotoInfoViewModel: ViewModel() {

    var photoInfoState = mutableStateOf<PhotoInfo?>(null)

    var photoInfo = photoInfoState.value

}