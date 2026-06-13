package cn.qinxiandiqi.photochecker.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.qinxiandiqi.photochecker.R
import cn.qinxiandiqi.photochecker.feature.home.RemovalState
import cn.qinxiandiqi.photochecker.feature.home.model.ExifAnalysisResult
import cn.qinxiandiqi.photochecker.feature.home.model.ExifRemovalMode
import cn.qinxiandiqi.photochecker.feature.home.model.PrivacyRisk
import cn.qinxiandiqi.photochecker.ui.theme.LocalAppColors
import cn.qinxiandiqi.photochecker.ui.theme.ShapeMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingLg
import cn.qinxiandiqi.photochecker.ui.theme.SpacingMd
import cn.qinxiandiqi.photochecker.ui.theme.SpacingSm
import cn.qinxiandiqi.photochecker.ui.theme.SpacingXl
import cn.qinxiandiqi.photochecker.ui.theme.SpacingXs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExifRemovalSheet(
    result: ExifAnalysisResult,
    removalState: RemovalState,
    onRemove: (ExifRemovalMode) -> Unit,
    onShare: (java.io.File) -> Unit,
    onSave: (java.io.File, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = LocalAppColors.current
    // Save outcome shown inline (null = idle/none yet), because the SnackbarHost sits
    // behind this modal sheet and would be hidden from the user.
    var saveResult by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingXl)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.action_privacy_clean),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(SpacingLg))

            when (removalState) {
                is RemovalState.Idle -> {
                    if (result.hasGpsCoordinates) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_gps),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_gps),
                            onClick = { onRemove(ExifRemovalMode.GPS_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(SpacingSm))
                    }
                    if (result.overallRisk == PrivacyRisk.MEDIUM || result.overallRisk == PrivacyRisk.HIGH) {
                        RemovalOptionButton(
                            text = stringResource(id = R.string.action_remove_personal),
                            subtitle = stringResource(id = R.string.msg_privacy_summary_personal),
                            onClick = { onRemove(ExifRemovalMode.PERSONAL_ONLY) }
                        )
                        Spacer(modifier = Modifier.height(SpacingSm))
                    }
                    RemovalOptionButton(
                        text = stringResource(id = R.string.action_remove_all),
                        onClick = { onRemove(ExifRemovalMode.ALL) }
                    )
                }

                is RemovalState.Removing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(SpacingMd))
                        Text(text = stringResource(id = R.string.msg_removing))
                    }
                }

                is RemovalState.Done -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_success),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.success,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(SpacingLg))
                    Button(
                        onClick = { onShare(removalState.file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(SpacingSm))
                        Text(text = stringResource(id = R.string.action_share))
                    }
                    Spacer(modifier = Modifier.height(SpacingSm))
                    OutlinedButton(
                        onClick = { onSave(removalState.file) { ok -> saveResult = ok } },
                        enabled = saveResult == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(SpacingSm))
                        Text(text = stringResource(id = R.string.action_save_to_gallery))
                    }
                    saveResult?.let { ok ->
                        Spacer(modifier = Modifier.height(SpacingXs))
                        Text(
                            text = stringResource(
                                id = if (ok) R.string.msg_saved_to_gallery
                                else R.string.msg_save_to_gallery_error
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) appColors.success else MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is RemovalState.Error -> {
                    Text(
                        text = stringResource(id = R.string.msg_remove_error),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(SpacingSm))
                    Text(
                        text = removalState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
internal fun RemovalOptionButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(ShapeMd),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(SpacingLg)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(SpacingXs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
