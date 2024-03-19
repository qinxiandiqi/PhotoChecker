package cn.qinxiandiqi.photochecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.qinxiandiqi.photochecker.feature.home.HomeScreen
import cn.qinxiandiqi.photochecker.feature.home.HomeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App {
                HomeScreen(
                    viewModel = viewModel<HomeViewModel>(),
                    onAboutClick = {

                    }
                )
            }
        }
    }
}





