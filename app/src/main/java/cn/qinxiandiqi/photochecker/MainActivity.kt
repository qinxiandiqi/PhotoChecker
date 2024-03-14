package cn.qinxiandiqi.photochecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.ui.theme.PhotoCheckerTheme

class MainActivity : ComponentActivity() {

    private var photoInfo: PhotoInfo? = null

    private val pickMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            photoInfo = PhotoInfo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoCheckerTheme {
                // A surface container using the 'background' color from the theme
                ImageSelectorScreen {
                    pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                }
            }
        }
    }
}

@Composable
fun App(onAddImageClick: () -> Unit) {
    PhotoCheckerTheme {
        ImageSelectorScreen(onAddImageClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSelectorScreen(onAddImageClick: () -> Unit) {
    Column {
        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
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
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    App {}
}