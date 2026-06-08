package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhoisScreen(paddingValues: PaddingValues) {
    val viewModel: NetworkUtilsVM = koinViewModel()
    val whoisState by viewModel.whoisState.collectAsState()
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
                        value = whoisState.domain,
                        onValueChange = { viewModel.setWhoisDomain(it) },
                        label = { Text(stringResource(R.string.whois_domain_hint)) },
                        singleLine = true,
                        enabled = !whoisState.isRunning,
                        shape = MaterialTheme.shapes.large,
                    )
                    NetworkUtilActionRow {
                        if (whoisState.isRunning) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.stopWhois() },
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.stop))
                            }
                        } else {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.lookupWhois() },
                                enabled = whoisState.domain.isNotBlank(),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.whois_lookup))
                            }
                        }
                        if (whoisState.result != null || whoisState.error != null) {
                            FilledTonalButton(
                                onClick = { viewModel.clearWhois() },
                                enabled = !whoisState.isRunning,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(stringResource(R.string.clear))
                            }
                        }
                    }
                }
            }
        }

        if (whoisState.isRunning && whoisState.result == null && whoisState.error == null) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringResource(R.string.whois_running, whoisState.domain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (whoisState.error != null) {
            item { WhoisErrorCard(whoisState.error!!) }
        }

        whoisState.result?.let { text ->
            categoryTitleSmall { stringResource(R.string.whois_result) }
            item { WhoisResultCard(text) }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.whois))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhoisErrorCard(errorKey: String) {
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
                "invalid_domain" -> stringResource(R.string.whois_error_invalid_domain)
                "whois_server_not_found" -> stringResource(R.string.whois_error_server_not_found)
                "whois_empty" -> stringResource(R.string.whois_error_empty)
                "whois_failed" -> stringResource(R.string.whois_error_failed)
                else -> errorKey
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhoisResultCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = jetbrainsMono(),
        )
    }
}
