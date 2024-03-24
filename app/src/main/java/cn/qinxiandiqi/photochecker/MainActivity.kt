package cn.qinxiandiqi.photochecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.qinxiandiqi.photochecker.feature.about.AboutScreen
import cn.qinxiandiqi.photochecker.feature.home.HomeScreen
import cn.qinxiandiqi.photochecker.feature.home.HomeViewModel

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(activity = this)
            var isShowAbout by rememberSaveable { mutableStateOf(false) }
            App {
                if (isShowAbout) {
                    AboutScreen(windowSizeClass = windowSizeClass) {
                        isShowAbout = false
                    }
                } else {
                    HomeScreen(
                        windowSizeClass = windowSizeClass,
                        viewModel = viewModel<HomeViewModel>(),
                        onAboutClick = {
                            isShowAbout = true
                        }
                    )
                }
            }

            BackHandler(enabled = isShowAbout) {
                isShowAbout = false
            }
        }
    }
}





