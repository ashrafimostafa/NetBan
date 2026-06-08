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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import com.leekleak.trafficlight.model.IpLookupResult
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IpLookupScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val ipState by viewModel.ipLookupState.collectAsState()
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
                        value = ipState.ip,
                        onValueChange = { viewModel.setIpLookupAddress(it) },
                        label = { Text(stringResource(R.string.ip_lookup_hint)) },
                        singleLine = true,
                        enabled = !ipState.isRunning,
                        shape = MaterialTheme.shapes.large,
                    )
                    NetworkUtilActionRow {
                        if (ipState.isRunning) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.stopIpLookup() },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.lookupIp() },
                                enabled = ipState.ip.isNotBlank(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.ip_lookup_start))
                            }
                        }
                        if (ipState.result != null || ipState.error != null) {
                            FilledTonalButton(
                                onClick = { viewModel.clearIpLookup() },
                                enabled = !ipState.isRunning,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }

        if (ipState.isRunning && ipState.result == null && ipState.error == null) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(R.string.ip_lookup_running, ipState.ip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (ipState.error != null) {
            item { IpLookupErrorCard(ipState.error!!) }
        }

        ipState.result?.let { result ->
            categoryTitleSmall { stringResource(R.string.ip_lookup_result) }
            item { IpLookupResultCard(result) }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.ip_lookup))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IpLookupErrorCard(errorKey: String) {
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
                "invalid_ip" -> stringResource(R.string.ip_lookup_error_invalid)
                "lookup_failed" -> stringResource(R.string.ip_lookup_error_failed)
                else -> errorKey
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IpLookupResultCard(result: IpLookupResult) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.flagEmoji?.let {
                    Text(text = it, style = MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text(
                        text = result.ip,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = jetbrainsMono(),
                    )
                    result.type?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IpLookupSection(stringResource(R.string.ip_lookup_location)) {
                result.country?.let { country ->
                    val code = result.countryCode?.let { " ($it)" }.orEmpty()
                    IpLookupRow(stringResource(R.string.ip_lookup_country), "$country$code")
                }
                result.continent?.let {
                    IpLookupRow(stringResource(R.string.ip_lookup_continent), it)
                }
                result.region?.let {
                    IpLookupRow(stringResource(R.string.ip_lookup_region), it)
                }
                result.city?.let {
                    IpLookupRow(stringResource(R.string.ip_lookup_city), it)
                }
                if (result.latitude != null && result.longitude != null) {
                    IpLookupRow(
                        label = stringResource(R.string.ip_lookup_coordinates),
                        value = String.format(
                            Locale.US,
                            "%.4f, %.4f",
                            result.latitude,
                            result.longitude,
                        ),
                    )
                }
            }

            IpLookupSection(stringResource(R.string.ip_lookup_network)) {
                result.isp?.let { IpLookupRow(stringResource(R.string.ip_lookup_isp), it) }
                result.org?.let { IpLookupRow(stringResource(R.string.ip_lookup_org), it) }
                result.asn?.let { IpLookupRow(stringResource(R.string.ip_lookup_asn), it) }
            }

            result.timezone?.let {
                IpLookupSection(stringResource(R.string.ip_lookup_other)) {
                    IpLookupRow(stringResource(R.string.ip_lookup_timezone), it)
                }
            }
        }
    }
}

@Composable
private fun IpLookupSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun IpLookupRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            modifier = Modifier.weight(0.4f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.weight(0.6f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
