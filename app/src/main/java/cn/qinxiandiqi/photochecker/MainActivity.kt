package cn.qinxiandiqi.photochecker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.ui.theme.PhotoCheckerTheme
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<PhotoInfoViewModel>()

    private val pickMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.photoInfo = PhotoInfo(uri).also { it.parseExif(contentResolver) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App {
                HomeScreen(
                    onAddImageClick = {
                        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    onAboutClick = {

                    }
                ) {
                    val photoInfo by rememberSaveable { viewModel.photoInfoState }
                    photoInfo?.let { PhotoExifDetail(photoInfo = it) } ?: EmptyExifDetail()
                }

            }
        }

        onBackPressedDispatcher.addCallback {
            if (viewModel.photoInfo != null) {
                viewModel.photoInfo = null
            } else {
                finish()
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onAddImageClick: () -> Unit,
    onAboutClick: () -> Unit,
    exifDetail: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            actions = {
                IconButton(onClick = onAboutClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(id = R.string.about),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )
        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            exifDetail()

            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onAddImageClick
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
    modifier: Modifier = Modifier,
    photoInfo: PhotoInfo
) {
    Column(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = photoInfo.uri,
            modifier = modifier
                .fillMaxWidth(1f)
                .aspectRatio(ratio = 1.6f),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
        val exifListState = rememberLazyListState()
        val exifList = remember { photoInfo.readExifInfoList }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            state = exifListState
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
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = exifInfo.first, style = MaterialTheme.typography.titleMedium)
            Text(text = exifInfo.second, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Preview(showBackground = true)
@Composable
fun AppPreview() {
    App {
        HomeScreen(
            onAddImageClick = {},
            onAboutClick = {}
        ) {
            EmptyExifDetail()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoExifDetailPreview() {
    App {
        PhotoExifDetail(photoInfo = PhotoInfo(Uri.EMPTY))
    }
}