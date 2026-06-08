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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PingScreen(paddingValues: PaddingValues) {
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.largeIncreased,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                            shape = MaterialTheme.shapes.large,
                        )
                        FilledIconButton(
                            onClick = { viewModel.toggleCurrentBookmark() },
                            enabled = pingState.host.isNotBlank() && !pingState.isRunning,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isBookmarked) R.drawable.archive else R.drawable.save
                                ),
                                contentDescription = stringResource(
                                    if (isBookmarked) R.string.ping_remove_bookmark
                                    else R.string.ping_add_bookmark
                                ),
                            )
                        }
                    }
                    NetworkUtilActionRow {
                        if (pingState.isRunning) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.stopPing() },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startPing() },
                                enabled = pingState.host.isNotBlank(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.ping_start))
                            }
                        }
                        if (pingState.replies.isNotEmpty() || pingState.result != null) {
                            FilledTonalButton(
                                onClick = { viewModel.clearPing() },
                                enabled = !pingState.isRunning,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }

        if (bookmarks.isNotEmpty()) {
            categoryTitleSmall { stringResource(R.string.ping_bookmarks) }
            items(bookmarks, key = { it }) { host ->
                PingBookmarkListItem(
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
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(R.string.ping_running, pingState.host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            item { PingSummaryCard(result) }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.ping))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PingBookmarkListItem(
    host: String,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled, onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = host,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledIconButton(
                        onClick = onPing,
                        enabled = enabled,
                        modifier = Modifier.padding(0.dp),
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
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PingReplyItem(reply: PingReply) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.ping_sequence, reply.sequence))
            },
            trailingContent = {
                Text(
                    text = reply.timeMs?.let {
                        stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it))
                    } ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = jetbrainsMono(),
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PingSummaryCard(result: PingResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
