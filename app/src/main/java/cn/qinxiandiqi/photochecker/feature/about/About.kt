package cn.qinxiandiqi.photochecker.feature.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.qinxiandiqi.photochecker.App
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 *
 * created by Jianan on 2024/3/20.
 */

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier
) {
    LibrariesContainer(
        modifier = modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun AboutPreview() {
    App {
        AboutScreen()
    }
}