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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leekleak.trafficlight.R
import com.leekleak.trafficlight.model.SshConnectionResult
import com.leekleak.trafficlight.model.SshProfile
import com.leekleak.trafficlight.ui.theme.jetbrainsMono
import com.leekleak.trafficlight.util.PageTitle
import com.leekleak.trafficlight.util.categoryTitleSmall
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SshScreen(paddingValues: PaddingValues) {
    val viewModel: SshVM = koinViewModel()
    val state by viewModel.state.collectAsState()
    val profiles by viewModel.savedProfiles.collectAsState()
    val hazeState = rememberHazeState()

    if (state.isShellActive) {
        SshTerminalScreen(
            state = state,
            paddingValues = paddingValues,
            hazeState = hazeState,
            onInputChange = viewModel::setShellInput,
            onSend = viewModel::sendShellCommand,
            onDisconnect = viewModel::disconnectShell,
            onClear = viewModel::clearTerminal,
        )
        return
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
            SshConnectionCard(
                state = state,
                onLabelChange = viewModel::setLabel,
                onHostChange = viewModel::setHost,
                onUsernameChange = viewModel::setUsername,
                onPasswordChange = viewModel::setPassword,
                onPortChange = viewModel::setPort,
                onRemoteCommandChange = viewModel::setRemoteCommand,
                onTogglePassword = viewModel::togglePasswordVisible,
                onSave = viewModel::saveProfile,
                onConnect = viewModel::connect,
                onStop = viewModel::stopConnect,
                onClear = viewModel::clearResult,
                onNew = viewModel::newProfile,
            )
        }

        item {
            SshOptionsCard(
                state = state,
                onCompressionChange = viewModel::setCompression,
                onStrictHostKeyChange = viewModel::setStrictHostKeyChecking,
                onKeepAliveChange = viewModel::setKeepAlive,
                onAgentForwardingChange = viewModel::setAgentForwarding,
                onIpv4Change = viewModel::setIpv4Only,
                onIpv6Change = viewModel::setIpv6Only,
                onBatchModeChange = viewModel::setBatchMode,
                onVerboseChange = viewModel::setVerbose,
                onTimeoutChange = viewModel::setConnectTimeout,
                onAttemptsChange = viewModel::setConnectionAttempts,
            )
        }

        if (state.commandPreview.isNotBlank()) {
            item { SshCommandPreviewCard(state.commandPreview) }
        }

        if (profiles.isNotEmpty()) {
            categoryTitleSmall { stringResource(R.string.ssh_saved_profiles) }
            items(profiles, key = { it.id }) { profile ->
                SshProfileListItem(
                    profile = profile,
                    enabled = !state.isConnecting,
                    onLoad = { viewModel.loadProfile(profile) },
                    onConnect = {
                        viewModel.loadProfile(profile)
                        viewModel.connect()
                    },
                    onDelete = { viewModel.deleteProfile(profile.id) },
                )
            }
        }

        state.result?.let { result ->
            item { SshResultCard(result) }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.ssh))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SshConnectionCard(
    state: SshUiState,
    onLabelChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onRemoteCommandChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSave: () -> Unit,
    onConnect: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onNew: () -> Unit,
) {
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
                value = state.label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.ssh_label_hint)) },
                singleLine = true,
                enabled = !state.isConnecting,
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.ssh_host_hint)) },
                singleLine = true,
                enabled = !state.isConnecting,
                shape = MaterialTheme.shapes.large,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.ssh_username_hint)) },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(0.6f),
                    value = state.options.port.toString(),
                    onValueChange = onPortChange,
                    label = { Text(stringResource(R.string.ssh_port_hint)) },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.large,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.ssh_password_hint)) },
                singleLine = true,
                enabled = !state.isConnecting,
                visualTransformation = if (state.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                shape = MaterialTheme.shapes.large,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_show_password),
                checked = state.passwordVisible,
                onCheckedChange = { onTogglePassword() },
                enabled = !state.isConnecting,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.remoteCommand,
                onValueChange = onRemoteCommandChange,
                label = { Text(stringResource(R.string.ssh_command_hint)) },
                supportingText = { Text(stringResource(R.string.ssh_command_supporting)) },
                singleLine = true,
                enabled = !state.isConnecting,
                shape = MaterialTheme.shapes.large,
            )
            if (state.isConnecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            NetworkUtilActionRow {
                if (state.isConnecting) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = onStop,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.stop))
                    }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConnect,
                        enabled = state.host.isNotBlank() && state.username.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.ssh_connect))
                    }
                }
                FilledTonalButton(
                    onClick = onSave,
                    enabled = !state.isConnecting &&
                        state.host.isNotBlank() &&
                        state.username.isNotBlank(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.ssh_save))
                }
            }
            NetworkUtilActionRow {
                FilledTonalButton(
                    modifier = Modifier.weight(1f),
                    onClick = onNew,
                    enabled = !state.isConnecting,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(stringResource(R.string.ssh_new))
                }
                if (state.result != null) {
                    FilledTonalButton(
                        onClick = onClear,
                        enabled = !state.isConnecting,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(stringResource(R.string.clear))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SshOptionsCard(
    state: SshUiState,
    onCompressionChange: (Boolean) -> Unit,
    onStrictHostKeyChange: (Boolean) -> Unit,
    onKeepAliveChange: (Boolean) -> Unit,
    onAgentForwardingChange: (Boolean) -> Unit,
    onIpv4Change: (Boolean) -> Unit,
    onIpv6Change: (Boolean) -> Unit,
    onBatchModeChange: (Boolean) -> Unit,
    onVerboseChange: (Int) -> Unit,
    onTimeoutChange: (String) -> Unit,
    onAttemptsChange: (String) -> Unit,
) {
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
            Text(
                text = stringResource(R.string.ssh_options),
                style = MaterialTheme.typography.titleMedium,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_compression),
                checked = state.options.compression,
                onCheckedChange = onCompressionChange,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_strict_host_key),
                checked = state.options.strictHostKeyChecking,
                onCheckedChange = onStrictHostKeyChange,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_keep_alive),
                checked = state.options.keepAlive,
                onCheckedChange = onKeepAliveChange,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_agent_forwarding),
                checked = state.options.agentForwarding,
                onCheckedChange = onAgentForwardingChange,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_ipv4),
                checked = state.options.ipv4Only,
                onCheckedChange = onIpv4Change,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_ipv6),
                checked = state.options.ipv6Only,
                onCheckedChange = onIpv6Change,
                enabled = !state.isConnecting,
            )
            SshSwitchRow(
                label = stringResource(R.string.ssh_option_batch_mode),
                checked = state.options.batchMode,
                onCheckedChange = onBatchModeChange,
                enabled = !state.isConnecting,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.ssh_option_verbose),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { level ->
                    FilterChip(
                        selected = state.options.verbose == level,
                        onClick = { onVerboseChange(level) },
                        enabled = !state.isConnecting,
                        label = { Text(level.toString()) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.options.connectTimeoutSec.toString(),
                    onValueChange = onTimeoutChange,
                    label = { Text(stringResource(R.string.ssh_option_timeout)) },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.options.connectionAttempts.toString(),
                    onValueChange = onAttemptsChange,
                    label = { Text(stringResource(R.string.ssh_option_attempts)) },
                    singleLine = true,
                    enabled = !state.isConnecting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}

@Composable
private fun SshSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SshCommandPreviewCard(command: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ssh_command_preview),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = jetbrainsMono(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SshProfileListItem(
    profile: SshProfile,
    enabled: Boolean,
    onLoad: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled, onClick = onLoad),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = profile.displayName(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(
                        R.string.ssh_profile_subtitle,
                        profile.host,
                        profile.options.port,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledIconButton(
                        onClick = onConnect,
                        enabled = enabled,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.terminal),
                            contentDescription = stringResource(R.string.ssh_connect),
                        )
                    }
                    FilledIconButton(
                        onClick = onDelete,
                        enabled = enabled,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.ssh_delete),
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
private fun SshResultCard(result: SshConnectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (result.success) {
                    stringResource(R.string.ssh_connected)
                } else {
                    stringResource(R.string.ssh_failed)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (result.success) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            result.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            result.serverVersion?.let {
                SshResultRow(stringResource(R.string.ssh_server_version), it)
            }
            result.remoteHostname?.let {
                SshResultRow(stringResource(R.string.ssh_remote_hostname), it)
            }
            result.durationMs?.let {
                SshResultRow(
                    stringResource(R.string.ssh_duration),
                    stringResource(R.string.ping_time_ms, String.format(Locale.US, "%.0f", it.toDouble())),
                )
            }
            result.commandOutput?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.ssh_output),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = jetbrainsMono(),
                )
            }
        }
    }
}

@Composable
private fun SshResultRow(label: String, value: String) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SshTerminalScreen(
    state: SshUiState,
    paddingValues: PaddingValues,
    hazeState: HazeState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.terminalLines.size, state.isRunningCommand) {
        if (state.terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(state.terminalLines.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .hazeSource(hazeState)
            .padding(paddingValues)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ssh_shell_connected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = state.shellTarget,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = jetbrainsMono(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onClear, shape = MaterialTheme.shapes.large) {
                        Text(stringResource(R.string.clear))
                    }
                    FilledTonalButton(onClick = onDisconnect, shape = MaterialTheme.shapes.large) {
                        Text(stringResource(R.string.ssh_disconnect))
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = MaterialTheme.shapes.largeIncreased,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.terminalLines, key = { it.id }) { line ->
                    TerminalLineText(line)
                }
                if (state.isRunningCommand) {
                    item {
                        Text(
                            text = stringResource(R.string.ssh_running_command),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = jetbrainsMono(),
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = state.shellInput,
                    onValueChange = onInputChange,
                    label = { Text(stringResource(R.string.ssh_shell_input_hint)) },
                    singleLine = true,
                    enabled = !state.isRunningCommand,
                    shape = MaterialTheme.shapes.large,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = jetbrainsMono()),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = state.shellInput.isNotBlank() && !state.isRunningCommand,
                    colors = IconButtonDefaults.filledIconButtonColors(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = stringResource(R.string.ssh_shell_send),
                    )
                }
            }
        }
    }

    PageTitle(true, hazeState, stringResource(R.string.ssh_shell))
}

@Composable
private fun TerminalLineText(line: TerminalLine) {
    val color = when (line.type) {
        TerminalLineType.INPUT -> MaterialTheme.colorScheme.primary
        TerminalLineType.OUTPUT -> MaterialTheme.colorScheme.onSurface
        TerminalLineType.ERROR -> MaterialTheme.colorScheme.error
        TerminalLineType.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fontWeight = if (line.type == TerminalLineType.INPUT) FontWeight.SemiBold else FontWeight.Normal

    Text(
        text = line.text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            fontWeight = fontWeight,
        ),
        fontFamily = jetbrainsMono(),
        color = color,
    )
}
