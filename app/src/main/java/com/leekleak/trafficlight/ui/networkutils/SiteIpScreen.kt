package com.leekleak.trafficlight.ui.networkutils

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.SiteIpResult
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SiteIpScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val state by viewModel.siteIpState.collectAsState()
    val hazeState = rememberHazeState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun copyIp(ip: String) {
        clipboard.setText(AnnotatedString(ip))
        Toast.makeText(context, context.getString(R.string.site_ip_copied), Toast.LENGTH_SHORT).show()
    }

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
                        value = state.url,
                        onValueChange = { viewModel.setSiteIpUrl(it) },
                        label = { Text(stringResource(R.string.site_ip_hint)) },
                        supportingText = { Text(stringResource(R.string.site_ip_supporting)) },
                        singleLine = true,
                        enabled = !state.isRunning,
                        shape = MaterialTheme.shapes.large,
                    )
                    NetworkUtilActionRow {
                        if (state.isRunning) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.stopSiteIp() },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.resolveSiteIp() },
                                enabled = state.url.isNotBlank(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.site_ip_resolve))
                            }
                        }
                        if (state.result != null || state.error != null) {
                            FilledTonalButton(
                                onClick = { viewModel.clearSiteIp() },
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

        if (state.isRunning && state.result == null && state.error == null) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(R.string.site_ip_running, state.url),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.error != null) {
            item { SiteIpErrorCard(state.error!!) }
        }

        state.result?.let { result ->
            item { SiteIpResolvedCard(result, ::copyIp) }

            result.ipInfo?.let { ipInfo ->
                categoryTitleSmall { stringResource(R.string.site_ip_info) }
                item { IpLookupResultCard(ipInfo) }
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.site_ip))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SiteIpResolvedCard(
    result: SiteIpResult,
    onCopyIp: (String) -> Unit,
) {
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
                text = stringResource(R.string.site_ip_host),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = result.host,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = jetbrainsMono(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            result.primaryIp?.let { primary ->
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(R.string.site_ip_primary),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                CopiableIpRow(
                    ip = primary,
                    highlighted = true,
                    onCopy = { onCopyIp(primary) },
                )
            }

            if (result.ipv4Addresses.isNotEmpty()) {
                SiteIpAddressGroup(
                    title = stringResource(R.string.site_ip_ipv4),
                    addresses = result.ipv4Addresses,
                    onCopyIp = onCopyIp,
                )
            }

            if (result.ipv6Addresses.isNotEmpty()) {
                SiteIpAddressGroup(
                    title = stringResource(R.string.site_ip_ipv6),
                    addresses = result.ipv6Addresses,
                    onCopyIp = onCopyIp,
                )
            }
        }
    }
}

@Composable
private fun SiteIpAddressGroup(
    title: String,
    addresses: List<String>,
    onCopyIp: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        addresses.forEach { ip ->
            CopiableIpRow(ip = ip, highlighted = false, onCopy = { onCopyIp(ip) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CopiableIpRow(
    ip: String,
    highlighted: Boolean,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = ip,
                    style = if (highlighted) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontFamily = jetbrainsMono(),
                )
            },
            trailingContent = {
                FilledIconButton(
                    onClick = onCopy,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.content_copy),
                        contentDescription = stringResource(R.string.site_ip_copy),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SiteIpErrorCard(errorKey: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = when (errorKey) {
                "invalid_host" -> stringResource(R.string.site_ip_error_invalid)
                "resolve_failed" -> stringResource(R.string.site_ip_error_resolve)
                else -> errorKey
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
