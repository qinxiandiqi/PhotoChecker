package cn.qinxiandiqi.photochecker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 *
 * created by Jianan on 2024/3/17
 */
class PhotoInfoViewModel: ViewModel() {

    var photoInfoState = mutableStateOf<PhotoInfo?>(null)

    var photoInfo by photoInfoState

    fun process() {

    }

}