package cn.qinxiandiqi.photochecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                val photoInfo by rememberSaveable { viewModel.photoInfoState }
                if (photoInfo != null) {
                    ImageExifDetailScreen(viewModel = viewModel)
                } else {
                    ImageSelectorScreen {
                        pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    }
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
fun ImageSelectorScreen(
    modifier: Modifier = Modifier,
    onAddImageClick: () -> Unit
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .clickable(onClick = onAddImageClick)
                    .drawBehind {
                        val borderStrokeWidth = 1.dp.toPx()
                        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        val cornerRadius = 8.dp.toPx()
                        // Draw the dashed border
                        drawRoundRect(
                            color = Color.Black,
                            style = Stroke(width = borderStrokeWidth, pathEffect = pathEffect),
                            cornerRadius = CornerRadius(cornerRadius)
                        )
                    },
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .size(100.dp, 161.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Image",
                        tint = Color.Black,
                    )
                    Text(
                        modifier = Modifier.padding(5.dp),
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
                        text = stringResource(id = R.string.pick_photo),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExifDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PhotoInfoViewModel = viewModel()
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.photo_exif_info)) },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.photoInfo = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        AsyncImage(
            model = viewModel.photoInfo?.uri,
            modifier = modifier
                .fillMaxWidth(1f)
                .aspectRatio(ratio = 1.6f),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
    }
}


@Preview(showBackground = true)
@Composable
fun ImageSelectorScreenPreview() {
    App {
        ImageSelectorScreen {

        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImageExifDetailScreenPreview() {
    App {
        ImageExifDetailScreen()
    }
}