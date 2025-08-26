package cn.qinxiandiqi.photochecker.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.qinxiandiqi.photochecker.App
import cn.qinxiandiqi.photochecker.R
import coil.compose.AsyncImage

/**
 *
 * created by Jianan on 2024/3/19
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onAboutClick: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updatePhoto(uri)
        }
    }
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            actions = {
                IconButton(onClick = onAboutClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(id = R.string.about),
                    )
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            val uiState by viewModel.photoInfoFlow.collectAsStateWithLifecycle()

            when (uiState) {
                HomeUIState.Empty -> {
                    EmptyExifDetail()
                }

                is HomeUIState.Loading -> {
                    PhotoExifDetailLoading(
                        windowSizeClass = windowSizeClass,
                        photoInfo = (uiState as HomeUIState.Loading).photoInfo
                    )
                }

                is HomeUIState.Success -> {
                    PhotoExifDetailSuccess(
                        windowSizeClass = windowSizeClass,
                        photoInfo = (uiState as HomeUIState.Success).photoInfo
                    )
                }

                is HomeUIState.Error -> {
                    PhotoExifDetailError(
                        windowSizeClass = windowSizeClass,
                        photoInfo = (uiState as HomeUIState.Error).photoInfo
                    )
                }
            }

            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.select_photo),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun EmptyExifDetail(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.padding(5.dp),
            style = MaterialTheme.typography.titleLarge,
            text = stringResource(id = R.string.empty),
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier
                .padding(5.dp),
            style = MaterialTheme.typography.bodyMedium,
            text = stringResource(id = R.string.empty_tips),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PhotoExifDetail(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    uri: Uri,
    content: @Composable () -> Unit
) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Column(modifier = modifier.fillMaxSize()) {
                AsyncImage(
                    model = uri,
                    modifier = modifier
                        .fillMaxWidth(1f)
                        .aspectRatio(ratio = 1.6f),
                    contentScale = ContentScale.Crop,
                    contentDescription = ""
                )
                content()
            }
        }

        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            Row(modifier = modifier.fillMaxSize()) {
                AsyncImage(
                    model = uri,
                    modifier = modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f),
                    contentScale = ContentScale.Crop,
                    contentDescription = ""
                )
                content()
            }
        }
    }
}

@Composable
fun PhotoExifDetailLoading(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    photoInfo: PhotoInfo,
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = photoInfo.uri
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun PhotoExifDetailSuccess(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    photoInfo: PhotoInfo
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = photoInfo.uri
    ) {
        val exifList = remember { photoInfo.readExifInfoList }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            items(exifList.size) { index ->
                ExifInfoItem(exifInfo = exifList[index])
            }
        }
    }
}

@Composable
fun ExifInfoItem(modifier: Modifier = Modifier, exifInfo: Pair<String, String>) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = exifInfo.first, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    modifier = Modifier.size(10.dp),
                    imageVector = Icons.Default.PlayArrow,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    contentDescription = ""
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = exifInfo.second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun PhotoExifDetailError(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    photoInfo: PhotoInfo? = null
) {
    PhotoExifDetail(
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        uri = photoInfo?.uri ?: Uri.EMPTY
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.padding(5.dp),
                style = MaterialTheme.typography.titleLarge.copy(MaterialTheme.colorScheme.error),
                text = stringResource(id = R.string.error),
                textAlign = TextAlign.Center
            )
            Text(
                modifier = Modifier
                    .padding(5.dp),
                style = MaterialTheme.typography.bodyMedium.copy(MaterialTheme.colorScheme.onError),
                text = stringResource(id = R.string.error_tips),
                textAlign = TextAlign.Center
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun PhotoExifDetailPreview() {
//    App {
//        PhotoExifDetailSuccess(photoInfo = PhotoInfo(uri = Uri.EMPTY))
//    }
//}

@Preview(showBackground = true)
@Composable
fun PhotoExifDetailItemPreview() {
    App {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            items(5) { index ->
                ExifInfoItem(exifInfo = "Title${index}" to "content")
            }
        }
    }
}