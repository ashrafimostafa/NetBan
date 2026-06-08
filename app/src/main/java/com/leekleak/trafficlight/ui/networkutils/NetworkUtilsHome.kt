package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.ui.navigation.Navigator
import com.leekleak.trafficlight.ui.navigation.IpLookupTool
import com.leekleak.trafficlight.ui.navigation.MyNetworkTool
import com.leekleak.trafficlight.ui.navigation.PingTool
import com.leekleak.trafficlight.ui.navigation.TracerouteTool
import com.leekleak.trafficlight.ui.navigation.WhoisTool
import com.leekleak.trafficlight.util.PageTitle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject

@Composable
fun NetworkUtilsHome(paddingValues: PaddingValues) {
    val navigator: Navigator = koinInject()
    val hazeState = rememberHazeState()

    LazyColumn(
        Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .hazeSource(hazeState),
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = stringResource(R.string.network_utils_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NetworkUtilTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = stringResource(R.string.ping),
                    description = stringResource(R.string.ping_tile_description),
                    icon = painterResource(R.drawable.mobiledata_arrows),
                    onClick = { navigator.goTo(PingTool) },
                )
                NetworkUtilTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = stringResource(R.string.whois),
                    description = stringResource(R.string.whois_tile_description),
                    icon = painterResource(R.drawable.query_stats),
                    onClick = { navigator.goTo(WhoisTool) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NetworkUtilTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = stringResource(R.string.ip_lookup),
                    description = stringResource(R.string.ip_lookup_tile_description),
                    icon = painterResource(R.drawable.cellular),
                    onClick = { navigator.goTo(IpLookupTool) },
                )
                NetworkUtilTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = stringResource(R.string.my_network),
                    description = stringResource(R.string.my_network_tile_description),
                    icon = painterResource(R.drawable.vpn),
                    onClick = { navigator.goTo(MyNetworkTool) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NetworkUtilTile(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    title = stringResource(R.string.traceroute),
                    description = stringResource(R.string.traceroute_tile_description),
                    icon = painterResource(R.drawable.hotspot),
                    onClick = { navigator.goTo(TracerouteTool) },
                )
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.network_utils))
}
