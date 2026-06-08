package com.leekleak.trafficlight.ui.networkutils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.IpLookupResult
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IpLookupResultCard(
    result: IpLookupResult,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        IpLookupResultContent(result, Modifier.padding(16.dp))
    }
}

@Composable
fun IpLookupResultContent(result: IpLookupResult, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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

        IpInfoSection(stringResource(R.string.ip_lookup_location)) {
            result.country?.let { country ->
                val code = result.countryCode?.let { " ($it)" }.orEmpty()
                IpInfoRow(stringResource(R.string.ip_lookup_country), "$country$code")
            }
            result.continent?.let { IpInfoRow(stringResource(R.string.ip_lookup_continent), it) }
            result.region?.let { IpInfoRow(stringResource(R.string.ip_lookup_region), it) }
            result.city?.let { IpInfoRow(stringResource(R.string.ip_lookup_city), it) }
            if (result.latitude != null && result.longitude != null) {
                IpInfoRow(
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

        IpInfoSection(stringResource(R.string.ip_lookup_network)) {
            result.isp?.let { IpInfoRow(stringResource(R.string.ip_lookup_isp), it) }
            result.org?.let { IpInfoRow(stringResource(R.string.ip_lookup_org), it) }
            result.asn?.let { IpInfoRow(stringResource(R.string.ip_lookup_asn), it) }
        }

        result.timezone?.let {
            IpInfoSection(stringResource(R.string.ip_lookup_other)) {
                IpInfoRow(stringResource(R.string.ip_lookup_timezone), it)
            }
        }
    }
}

@Composable
fun IpInfoSection(title: String, content: @Composable () -> Unit) {
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
fun IpInfoRow(label: String, value: String) {
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
