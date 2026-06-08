package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.TracerouteHop
import com.leekleak.trafficlight.model.TracerouteResult
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TracerouteScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val state by viewModel.tracerouteState.collectAsState()
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
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.host,
                        onValueChange = { viewModel.setTracerouteHost(it) },
                        label = { Text(stringResource(R.string.traceroute_host_hint)) },
                        supportingText = { Text(stringResource(R.string.traceroute_host_supporting)) },
                        singleLine = true,
                        enabled = !state.isRunning,
                        shape = MaterialTheme.shapes.large,
                    )
                    if (state.isRunning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    NetworkUtilActionRow {
                        if (state.isRunning) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.stopTraceroute() },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.startTraceroute() },
                                enabled = state.host.isNotBlank(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.traceroute_start))
                            }
                        }
                        if (state.hops.isNotEmpty() || state.result != null) {
                            FilledTonalButton(
                                onClick = { viewModel.clearTraceroute() },
                                enabled = !state.isRunning,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }

        if (state.isRunning && state.hops.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(R.string.traceroute_running, state.host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.result?.let { result ->
            if (result.resolvedIp != null || result.error == null) {
                item { TracerouteTargetCard(state.host, result) }
            }
        }

        if (state.result?.error != null && state.hops.isEmpty()) {
            val errorKey = state.result!!.error!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        modifier = Modifier.padding(16.dp),
                        text = when {
                            errorKey == "invalid_host" -> stringResource(R.string.ping_error_invalid_host)
                            errorKey == "traceroute_failed" -> stringResource(R.string.traceroute_error_failed)
                            errorKey.startsWith("traceroute_failed:") -> errorKey.removePrefix("traceroute_failed: ").trim()
                            else -> errorKey
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (state.hops.isNotEmpty()) {
            categoryTitleSmall { stringResource(R.string.traceroute_hops) }
            items(state.hops, key = { it.hop }) { hop ->
                TracerouteHopCard(hop)
            }
        }

        state.result?.let { result ->
            if (result.hops.isNotEmpty()) {
                item { TracerouteSummaryCard(result) }
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.traceroute))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TracerouteTargetCard(host: String, result: TracerouteResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.traceroute_target),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = host,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = jetbrainsMono(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            result.resolvedIp?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.traceroute_resolved_ip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = jetbrainsMono(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TracerouteHopCard(hop: TracerouteHop) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when {
                hop.isDestination -> MaterialTheme.colorScheme.tertiaryContainer
                hop.timedOut -> MaterialTheme.colorScheme.surfaceContainerLowest
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.traceroute_hop_number, hop.hop),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TracerouteHopStatusChip(hop)
            }

            if (!hop.timedOut) {
                hop.address?.let {
                    IpInfoRow(stringResource(R.string.traceroute_hop_ip), it)
                }
                hop.hostname?.let {
                    IpInfoRow(stringResource(R.string.traceroute_hop_hostname), it)
                }
                hop.timeMs?.let {
                    IpInfoRow(
                        stringResource(R.string.traceroute_hop_rtt),
                        stringResource(
                            R.string.ping_time_ms,
                            String.format(Locale.US, "%.1f", it),
                        ),
                    )
                }
            }

            hop.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = jetbrainsMono(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TracerouteHopStatusChip(hop: TracerouteHop) {
    val (label, containerColor, labelColor) = when {
        hop.isDestination -> Triple(
            stringResource(R.string.traceroute_status_destination),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        hop.timedOut -> Triple(
            stringResource(R.string.traceroute_status_timeout),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            stringResource(R.string.traceroute_status_router),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            disabledContainerColor = containerColor,
            disabledLabelColor = labelColor,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TracerouteSummaryCard(result: TracerouteResult) {
    val respondedHops = result.hops.count { !it.timedOut }
    val avgRtt = result.hops.mapNotNull { it.timeMs }.average().takeIf { !it.isNaN() }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(R.string.traceroute_summary),
                style = MaterialTheme.typography.titleMedium,
            )
            IpInfoRow(stringResource(R.string.traceroute_total_hops), result.hops.size.toString())
            IpInfoRow(stringResource(R.string.traceroute_responding_hops), respondedHops.toString())
            avgRtt?.let {
                IpInfoRow(
                    stringResource(R.string.traceroute_avg_rtt),
                    stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.1f", it)),
                )
            }
            IpInfoRow(
                stringResource(R.string.traceroute_status),
                if (result.reachedDestination) {
                    stringResource(R.string.traceroute_reached)
                } else {
                    stringResource(R.string.traceroute_not_reached)
                },
            )
        }
    }
}
