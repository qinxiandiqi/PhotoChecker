package cn.qinxiandiqi.photochecker.feature.about

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.App
import cn.qinxiandiqi.photochecker.BuildConfig
import cn.qinxiandiqi.photochecker.R
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults

/**
 *
 * created by Jianan on 2024/3/20.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = ""
                    )
                }
            },
            title = {
                Text(text = stringResource(id = R.string.about))
            },
            colors = TopAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier.size(100.dp),
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = ""
            )
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    text = stringResource(id = R.string.app_name)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    style = MaterialTheme.typography.titleMedium,
                    text = "${stringResource(id = R.string.version)} ${BuildConfig.VERSION_NAME}"
                )
            }
        }

        val uriLauncher = rememberLauncherForActivityResult(contract = LinkContract()) {}
        Text(
            modifier = Modifier
                .padding(horizontal = 26.dp, vertical = 8.dp)
                .clickable {
                    uriLauncher.launch(Uri.parse("https://www.qinxiandiqi.cn"))
                },
            text = stringResource(id = R.string.policy),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.tertiary,
            textDecoration = TextDecoration.Underline
        )

        LibrariesContainer(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            padding = LibraryDefaults.libraryPadding(
                namePadding = PaddingValues(vertical = 4.dp),
                badgeContentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AboutPreview() {
    App {
        AboutScreen()
    }
}