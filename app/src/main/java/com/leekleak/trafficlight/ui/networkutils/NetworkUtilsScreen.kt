package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.PingReply
import com.leekleak.trafficlight.model.PingResult
import com.leekleak.trafficlight.ui.theme.card
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun NetworkUtilsScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val pingState by viewModel.pingState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val isBookmarked by viewModel.isCurrentHostBookmarked.collectAsState()
    val hazeState = rememberHazeState()

    LazyColumn(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .hazeSource(hazeState),
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        categoryTitleSmall { stringResource(R.string.ping) }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .card()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = pingState.host,
                        onValueChange = { viewModel.setHost(it) },
                        label = { Text(stringResource(R.string.ping_host_hint)) },
                        singleLine = true,
                        enabled = !pingState.isRunning,
                    )
                    FilledIconButton(
                        onClick = { viewModel.toggleCurrentBookmark() },
                        enabled = pingState.host.isNotBlank() && !pingState.isRunning,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isBookmarked) R.drawable.archive else R.drawable.save
                            ),
                            contentDescription = stringResource(
                                if (isBookmarked) R.string.ping_remove_bookmark else R.string.ping_add_bookmark
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pingState.isRunning) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.stopPing() },
                        ) {
                            Text(stringResource(R.string.stop))
                        }
                    } else {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.startPing() },
                            enabled = pingState.host.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.ping_start))
                        }
                    }
                    if (pingState.replies.isNotEmpty() || pingState.result != null) {
                        FilledTonalButton(
                            onClick = { viewModel.clearPing() },
                            enabled = !pingState.isRunning,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
            }
        }

        categoryTitleSmall { stringResource(R.string.ping_bookmarks) }
        if (bookmarks.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.ping_no_bookmarks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(bookmarks, key = { it }) { host ->
                PingBookmarkItem(
                    host = host,
                    enabled = !pingState.isRunning,
                    onSelect = { viewModel.selectBookmark(host) },
                    onPing = { viewModel.pingBookmark(host) },
                    onRemove = { viewModel.removeBookmark(host) },
                )
            }
        }

        if (pingState.isRunning && pingState.replies.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.ping_running, pingState.host),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (pingState.replies.isNotEmpty()) {
            categoryTitleSmall { stringResource(R.string.ping_results) }
            items(pingState.replies, key = { it.sequence }) { reply ->
                PingReplyItem(reply)
            }
        }

        pingState.result?.let { result ->
            item {
                PingSummaryCard(result)
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.network_utils))
}

@Composable
private fun PingBookmarkItem(
    host: String,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .card()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = host,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FilledIconButton(
            onClick = onPing,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                painter = painterResource(R.drawable.mobiledata_arrows),
                contentDescription = stringResource(R.string.ping_bookmark),
            )
        }
        FilledIconButton(
            onClick = onRemove,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.ping_remove_bookmark),
            )
        }
    }
}

@Composable
private fun PingReplyItem(reply: PingReply) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .card()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ping_sequence, reply.sequence),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = reply.timeMs?.let {
                stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it))
            } ?: stringResource(R.string.unknown),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = jetbrainsMono(),
        )
    }
}

@Composable
private fun PingSummaryCard(result: PingResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .card()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.ping_summary),
            style = MaterialTheme.typography.titleMedium,
        )

        result.error?.let { errorKey ->
            Text(
                text = when (errorKey) {
                    "invalid_host" -> stringResource(R.string.ping_error_invalid_host)
                    "host_unreachable" -> stringResource(R.string.ping_error_unreachable)
                    "ping_failed" -> stringResource(R.string.ping_error_failed)
                    else -> errorKey
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        result.packetLossPercent?.let {
            PingStatRow(
                label = stringResource(R.string.ping_packet_loss),
                value = stringResource(R.string.ping_packet_loss_value, it),
            )
        }
        result.minMs?.let {
            PingStatRow(
                label = stringResource(R.string.ping_min),
                value = stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it)),
            )
        }
        result.avgMs?.let {
            PingStatRow(
                label = stringResource(R.string.ping_avg),
                value = stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it)),
            )
        }
        result.maxMs?.let {
            PingStatRow(
                label = stringResource(R.string.ping_max),
                value = stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it)),
            )
        }
    }
}

@Composable
private fun PingStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = jetbrainsMono(),
        )
    }
}
