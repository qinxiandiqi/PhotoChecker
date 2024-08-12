package cn.qinxiandiqi.photochecker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.qinxiandiqi.photochecker.ui.theme.PhotoCheckerTheme

/**
 *
 * created by Jianan on 2024/3/19
 */

@Composable
fun App(
    modifier: Modifier = Modifier,
    screen: @Composable () -> Unit,
) {
    PhotoCheckerTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            screen()
        }
    }
}