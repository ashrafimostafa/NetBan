package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.MyNetworkInfo
import com.leekleak.trafficlight.model.NetworkLinkInfo
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyNetworkScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val state by viewModel.myNetworkState.collectAsState()
    val hazeState = rememberHazeState()

    LaunchedEffect(Unit) {
        if (state.info == null && !state.isLoading) {
            viewModel.refreshMyNetwork()
        }
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
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.refreshMyNetwork() },
                enabled = !state.isLoading,
                shape = MaterialTheme.shapes.large,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.my_network_refresh))
            }
        }

        if (state.error != null) {
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
                        text = stringResource(R.string.my_network_error),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        state.info?.let { info ->
            item { VpnStatusCard(info) }

            info.publicIp?.let { publicIp ->
                categoryTitleSmall {
                    if (info.isVpnActive) {
                        stringResource(R.string.my_network_vpn_ip)
                    } else {
                        stringResource(R.string.my_network_public_ip)
                    }
                }
                item {
                    if (publicIp.error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Text(
                                modifier = Modifier.padding(16.dp),
                                text = stringResource(R.string.ip_lookup_error_failed),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    } else {
                        IpLookupResultCard(publicIp)
                    }
                }
            }

            categoryTitleSmall { stringResource(R.string.my_network_signaling) }
            item { SignalingCard(info) }

            if (info.links.isNotEmpty()) {
                categoryTitleSmall { stringResource(R.string.my_network_interfaces) }
                items(info.links, key = { "${it.interfaceName}-${it.networkType}" }) { link ->
                    NetworkLinkCard(link)
                }
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.my_network))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VpnStatusCard(info: MyNetworkInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = if (info.isVpnActive) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (info.isVpnActive) {
                    stringResource(R.string.my_network_vpn_active)
                } else {
                    stringResource(R.string.my_network_vpn_inactive)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (info.isVpnActive) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (info.isVpnActive) {
                Text(
                    text = stringResource(R.string.my_network_vpn_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                info.vpnAppName?.let {
                    IpInfoRow(stringResource(R.string.my_network_vpn_app), it)
                }
                info.underlyingConnection?.let {
                    IpInfoRow(stringResource(R.string.my_network_underlying), it)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignalingCard(info: MyNetworkInfo) {
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            info.activeConnection?.let {
                IpInfoRow(stringResource(R.string.my_network_active_connection), it)
            }
            info.cellularNetworkType?.let {
                IpInfoRow(stringResource(R.string.my_network_mobile_type), it)
            }
            info.cellularOperator?.let {
                IpInfoRow(stringResource(R.string.my_network_operator), it)
            }
            info.simOperator?.let {
                IpInfoRow(stringResource(R.string.my_network_sim_operator), it)
            }
            info.isRoaming?.let { roaming ->
                IpInfoRow(
                    stringResource(R.string.my_network_roaming),
                    if (roaming) stringResource(R.string.yes) else stringResource(R.string.no),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkLinkCard(link: NetworkLinkInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = link.networkType,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (link.isActive) {
                    Text(
                        text = stringResource(R.string.my_network_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            link.interfaceName?.let {
                IpInfoRow(stringResource(R.string.my_network_interface), it)
            }
            link.mtu?.let {
                IpInfoRow(stringResource(R.string.my_network_mtu), it.toString())
            }
            if (link.localAddresses.isNotEmpty()) {
                IpInfoRow(
                    stringResource(R.string.my_network_local_ip),
                    link.localAddresses.joinToString("\n"),
                )
            }
            if (link.dnsServers.isNotEmpty()) {
                IpInfoRow(
                    stringResource(R.string.my_network_dns),
                    link.dnsServers.joinToString("\n"),
                )
            }
        }
    }
}
