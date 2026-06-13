package cn.qinxiandiqi.photochecker.feature.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.model.ConsistencyWarning
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import cn.qinxiandiqi.photochecker.ui.theme.ShapeSm
import cn.qinxiandiqi.photochecker.ui.theme.SpacingLg
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm
import coil.compose.AsyncImage
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlin.math.atan2

@Composable
internal fun ConsistencyWarningSection(
    warnings: List<ConsistencyWarning>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = SpacingLg, vertical = SpacingSm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        warnings.forEach { warning ->
            ConsistencyWarningItem(warning = warning)
        }
    }
}

@Composable
internal fun ConsistencyWarningItem(warning: ConsistencyWarning) {
    val appColors = LocalAppColors.current
    var showFullThumbnail by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ShapeSm),
        colors = CardDefaults.cardColors(
            containerColor = appColors.warningContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingMd, vertical = SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = appColors.warningIcon
            )
            Spacer(modifier = Modifier.width(SpacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = warning.messageResId),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = appColors.warningTitle
                )
                Text(
                    text = warning.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.warningDetail
                )
            }
            // Embedded thumbnail preview (tap to enlarge). Only shown when the warning
            // carries the decoded thumbnail bitmap.
            warning.thumbnail?.let { bitmap ->
                Spacer(modifier = Modifier.width(SpacingSm))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(id = R.string.warning_thumbnail_exists),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(ShapeSm))
                        .clickable { showFullThumbnail = true }
                )
            }
        }
    }

    if (showFullThumbnail && warning.thumbnail != null) {
        ThumbnailPreviewDialog(
            bitmap = warning.thumbnail,
            onDismiss = { showFullThumbnail = false }
        )
    }
}

@Composable
internal fun ThumbnailPreviewDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appColors.scrimOverlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Tapping the image itself should NOT close the dialog (only the scrim does).
            ZoomableImage(bitmap = bitmap)
        }
    }
}

/**
 * Image viewer supporting pinch-to-zoom, drag-to-pan, double-tap-to-reset (via the
 * zoomable library) AND two-finger rotation (handled here, since zoomable 1.6 has no
 * rotation API and Compose UI 1.11+ removed detectTransformGestures).
 *
 * Rotation is applied as an outer graphicsLayer so it composes cleanly with zoomable's
 * own scale/pan transform on the inner image.
 */
@Composable
internal fun ZoomableImage(bitmap: android.graphics.Bitmap) {
    val zoomState = rememberZoomState()
    var rotation by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Two-finger rotation, built on the low-level awaitPointerEventScope (the only
            // gesture API still present in Compose UI 1.11+). Tracks the angle of the line
            // between two pointers and accumulates the delta into `rotation`.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var prevAngle = 0.0
                    var hasPrev = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.size >= 2) {
                            val a = active[0].position
                            val b = active[1].position
                            val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
                            if (hasPrev) {
                                var delta = angle - prevAngle
                                // Wrap into [-π, π] so crossing the boundary doesn't cause a
                                // 360° jump.
                                if (delta > Math.PI) delta -= 2 * Math.PI
                                if (delta < -Math.PI) delta += 2 * Math.PI
                                rotation += Math.toDegrees(delta).toFloat()
                            }
                            prevAngle = angle
                            hasPrev = true
                        } else {
                            // Drop the reference when fewer than two fingers remain, so the
                            // next two-finger gesture starts from a fresh baseline.
                            hasPrev = false
                        }
                    }
                }
            }
            .graphicsLayer { rotationZ = rotation }
    ) {
        AsyncImage(
            model = bitmap,
            contentDescription = stringResource(id = R.string.warning_thumbnail_exists),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .zoomable(zoomState)
        )
    }
}
