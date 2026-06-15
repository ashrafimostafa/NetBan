package com.leekleak.trafficlight.ui.networkutils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leekleak.trafficlight.database.SshProfileRepo
import com.leekleak.trafficlight.model.SshConnectionResult
import com.leekleak.trafficlight.model.SshManager
import com.leekleak.trafficlight.model.SshOptions
import com.leekleak.trafficlight.model.SshProfile
import com.leekleak.trafficlight.model.SshSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TerminalLineType {
    SYSTEM,
    INPUT,
    OUTPUT,
    ERROR,
}

data class TerminalLine(
    val id: Long,
    val type: TerminalLineType,
    val text: String,
)

data class SshUiState(
    val profileId: String? = null,
    val label: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val options: SshOptions = SshOptions(),
    val remoteCommand: String = "",
    val passwordVisible: Boolean = false,
    val isConnecting: Boolean = false,
    val result: SshConnectionResult? = null,
    val commandPreview: String = "",
    val isShellActive: Boolean = false,
    val shellTarget: String = "",
    val terminalLines: List<TerminalLine> = emptyList(),
    val shellInput: String = "",
    val isRunningCommand: Boolean = false,
)

class SshVM(
    private val sshManager: SshManager,
    private val sshProfileRepo: SshProfileRepo,
) : ViewModel() {

    private val _state = MutableStateFlow(SshUiState())
    val state: StateFlow<SshUiState> = _state.asStateFlow()

    val savedProfiles: StateFlow<List<SshProfile>> = sshProfileRepo.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var connectJob: Job? = null
    private var commandJob: Job? = null
    private var activeSession: SshSession? = null
    private var nextLineId = 0L

    init {
        updateCommandPreview()
    }

    override fun onCleared() {
        disconnectShell()
        super.onCleared()
    }

    fun setLabel(value: String) {
        _state.update { it.copy(label = value) }
    }

    fun setHost(value: String) {
        _state.update { it.copy(host = value) }
        updateCommandPreview()
    }

    fun setUsername(value: String) {
        _state.update { it.copy(username = value) }
        updateCommandPreview()
    }

    fun setPassword(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun setRemoteCommand(value: String) {
        _state.update { it.copy(remoteCommand = value) }
        updateCommandPreview()
    }

    fun setShellInput(value: String) {
        _state.update { it.copy(shellInput = value) }
    }

    fun setPort(value: String) {
        val port = value.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        updateOptions { it.copy(port = port) }
    }

    fun setConnectTimeout(value: String) {
        val timeout = value.toIntOrNull()?.coerceIn(1, 120) ?: 10
        updateOptions { it.copy(connectTimeoutSec = timeout) }
    }

    fun setConnectionAttempts(value: String) {
        val attempts = value.toIntOrNull()?.coerceIn(1, 10) ?: 1
        updateOptions { it.copy(connectionAttempts = attempts) }
    }

    fun setVerbose(level: Int) {
        updateOptions { it.copy(verbose = level.coerceIn(0, 3)) }
    }

    fun setCompression(enabled: Boolean) {
        updateOptions { it.copy(compression = enabled) }
    }

    fun setStrictHostKeyChecking(enabled: Boolean) {
        updateOptions { it.copy(strictHostKeyChecking = enabled) }
    }

    fun setKeepAlive(enabled: Boolean) {
        updateOptions { it.copy(keepAlive = enabled) }
    }

    fun setAgentForwarding(enabled: Boolean) {
        updateOptions { it.copy(agentForwarding = enabled) }
    }

    fun setIpv4Only(enabled: Boolean) {
        updateOptions {
            it.copy(ipv4Only = enabled, ipv6Only = if (enabled) false else it.ipv6Only)
        }
    }

    fun setIpv6Only(enabled: Boolean) {
        updateOptions {
            it.copy(ipv6Only = enabled, ipv4Only = if (enabled) false else it.ipv4Only)
        }
    }

    fun setBatchMode(enabled: Boolean) {
        updateOptions { it.copy(batchMode = enabled) }
    }

    fun togglePasswordVisible() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun saveProfile() {
        val current = _state.value
        val host = current.host.trim()
        val username = current.username.trim()
        if (host.isBlank() || username.isBlank()) return

        val profile = SshProfile(
            id = current.profileId ?: java.util.UUID.randomUUID().toString(),
            label = current.label.trim(),
            host = host,
            username = username,
            password = current.password,
            options = current.options,
        )

        viewModelScope.launch {
            sshProfileRepo.save(profile)
            _state.update { it.copy(profileId = profile.id, label = profile.label) }
        }
    }

    fun loadProfile(profile: SshProfile) {
        if (_state.value.isShellActive) return
        _state.update {
            SshUiState(
                profileId = profile.id,
                label = profile.label,
                host = profile.host,
                username = profile.username,
                password = profile.password,
                options = profile.options,
                remoteCommand = it.remoteCommand,
            )
        }
        updateCommandPreview()
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            sshProfileRepo.remove(id)
            if (_state.value.profileId == id) {
                _state.update { it.copy(profileId = null) }
            }
        }
    }

    fun connect() {
        val profile = currentProfile() ?: return
        if (_state.value.isConnecting || _state.value.isShellActive) return

        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, result = null) }
            try {
                val (session, result) = sshManager.openSession(profile)
                if (result.success && session != null) {
                    activeSession = session
                    val welcome = buildList {
                        add(line(TerminalLineType.SYSTEM, "Connected to ${session.displayTarget}"))
                        session.serverVersion?.let {
                            add(line(TerminalLineType.SYSTEM, "Server: $it"))
                        }
                        session.remoteHostname?.let {
                            add(line(TerminalLineType.SYSTEM, "Hostname: $it"))
                        }
                        add(line(TerminalLineType.SYSTEM, "Type commands below. Use 'exit' to disconnect."))
                    }
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isShellActive = true,
                            shellTarget = session.displayTarget,
                            terminalLines = welcome,
                            result = null,
                        )
                    }
                    val initialCommand = _state.value.remoteCommand.trim()
                    if (initialCommand.isNotEmpty()) {
                        runShellCommand(initialCommand, showInput = true)
                    }
                } else {
                    _state.update { it.copy(isConnecting = false, result = result) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(isConnecting = false) }
            }
        }
    }

    fun sendShellCommand() {
        val command = _state.value.shellInput.trim()
        if (command.isEmpty() || _state.value.isRunningCommand) return

        if (command.equals("exit", ignoreCase = true) ||
            command.equals("logout", ignoreCase = true)
        ) {
            _state.update { it.copy(shellInput = "") }
            disconnectShell()
            return
        }

        _state.update { it.copy(shellInput = "") }
        runShellCommand(command, showInput = true)
    }

    fun disconnectShell() {
        commandJob?.cancel()
        commandJob = null
        connectJob?.cancel()
        connectJob = null
        activeSession?.disconnect()
        activeSession = null
        _state.update {
            it.copy(
                isShellActive = false,
                shellTarget = "",
                terminalLines = emptyList(),
                shellInput = "",
                isRunningCommand = false,
            )
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminalLines = emptyList()) }
    }

    fun stopConnect() {
        connectJob?.cancel()
        connectJob = null
        _state.update { it.copy(isConnecting = false) }
    }

    fun clearResult() {
        _state.update { it.copy(result = null) }
    }

    fun newProfile() {
        if (_state.value.isShellActive) return
        _state.value = SshUiState()
        updateCommandPreview()
    }

    private fun runShellCommand(command: String, showInput: Boolean) {
        val session = activeSession ?: return
        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            if (showInput) {
                appendLine(TerminalLineType.INPUT, "$ $command")
            }
            _state.update { it.copy(isRunningCommand = true) }
            try {
                val result = session.execute(command)
                when {
                    result.error != null -> appendLine(TerminalLineType.ERROR, result.error)
                    result.output.isNotBlank() -> appendLine(TerminalLineType.OUTPUT, result.output)
                    result.exitCode != null && result.exitCode != 0 -> {
                        appendLine(TerminalLineType.ERROR, "exit ${result.exitCode}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendLine(TerminalLineType.ERROR, e.message ?: "command_failed")
            } finally {
                _state.update { it.copy(isRunningCommand = false) }
            }
        }
    }

    private fun appendLine(type: TerminalLineType, text: String) {
        _state.update { state ->
            state.copy(terminalLines = state.terminalLines + line(type, text))
        }
    }

    private fun line(type: TerminalLineType, text: String): TerminalLine {
        return TerminalLine(id = nextLineId++, type = type, text = text)
    }

    private fun updateOptions(transform: (SshOptions) -> SshOptions) {
        _state.update { it.copy(options = transform(it.options)) }
        updateCommandPreview()
    }

    private fun updateCommandPreview() {
        val profile = currentProfile()
        val preview = profile?.let {
            sshManager.buildCommandLine(it, _state.value.remoteCommand)
        } ?: ""
        _state.update { it.copy(commandPreview = preview) }
    }

    private fun currentProfile(): SshProfile? {
        val current = _state.value
        val host = current.host.trim()
        val username = current.username.trim()
        if (host.isBlank() || username.isBlank()) return null

        return SshProfile(
            id = current.profileId ?: "",
            label = current.label.trim(),
            host = host,
            username = username,
            password = current.password,
            options = current.options,
        )
    }
}
