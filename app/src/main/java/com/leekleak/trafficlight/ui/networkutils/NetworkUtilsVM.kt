package com.leekleak.trafficlight.ui.networkutils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leekleak.trafficlight.database.PingBookmarkRepo
import com.leekleak.trafficlight.model.PingManager
import com.leekleak.trafficlight.model.PingReply
import com.leekleak.trafficlight.model.PingResult
import com.leekleak.trafficlight.model.WhoisManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PingUiState(
    val host: String = "",
    val isRunning: Boolean = false,
    val replies: List<PingReply> = emptyList(),
    val result: PingResult? = null,
)

data class WhoisUiState(
    val domain: String = "",
    val isRunning: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)

class NetworkUtilsVM(
    private val pingManager: PingManager,
    private val whoisManager: WhoisManager,
    private val pingBookmarkRepo: PingBookmarkRepo,
) : ViewModel() {

    private val _pingState = MutableStateFlow(PingUiState())
    val pingState: StateFlow<PingUiState> = _pingState.asStateFlow()

    val bookmarks: StateFlow<List<String>> = pingBookmarkRepo.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isCurrentHostBookmarked: StateFlow<Boolean> = combine(
        _pingState,
        bookmarks,
    ) { ping, saved ->
        val normalized = pingManager.normalizeHost(ping.host)
        normalized.isNotBlank() && saved.any { it.equals(normalized, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _whoisState = MutableStateFlow(WhoisUiState())
    val whoisState: StateFlow<WhoisUiState> = _whoisState.asStateFlow()

    private var pingJob: Job? = null
    private var whoisJob: Job? = null

    fun setHost(host: String) {
        _pingState.update { it.copy(host = host) }
    }

    fun toggleCurrentBookmark() {
        val host = pingManager.normalizeHost(_pingState.value.host)
        if (host.isBlank()) return
        viewModelScope.launch {
            if (bookmarks.value.any { it.equals(host, ignoreCase = true) }) {
                pingBookmarkRepo.remove(host)
            } else {
                pingBookmarkRepo.add(host)
                setHost(host)
            }
        }
    }

    fun removeBookmark(host: String) {
        viewModelScope.launch {
            pingBookmarkRepo.remove(host)
        }
    }

    fun selectBookmark(host: String) {
        setHost(host)
    }

    fun pingBookmark(host: String) {
        setHost(host)
        startPing()
    }

    fun startPing() {
        val host = _pingState.value.host.trim()
        if (host.isBlank() || _pingState.value.isRunning) return

        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            _pingState.update {
                PingUiState(host = host, isRunning = true)
            }
            try {
                val result = pingManager.ping(host) { reply ->
                    _pingState.update { state ->
                        state.copy(replies = state.replies + reply)
                    }
                }
                _pingState.update { state ->
                    state.copy(isRunning = false, result = result)
                }
            } catch (_: Exception) {
                _pingState.update { state ->
                    state.copy(isRunning = false)
                }
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        _pingState.update { it.copy(isRunning = false) }
    }

    fun clearPing() {
        stopPing()
        _pingState.value = PingUiState(host = _pingState.value.host)
    }

    fun setWhoisDomain(domain: String) {
        _whoisState.update { it.copy(domain = domain) }
    }

    fun lookupWhois() {
        val domain = _whoisState.value.domain.trim()
        if (domain.isBlank() || _whoisState.value.isRunning) return

        whoisJob?.cancel()
        whoisJob = viewModelScope.launch {
            _whoisState.update {
                WhoisUiState(domain = domain, isRunning = true)
            }
            try {
                val result = whoisManager.lookup(domain)
                _whoisState.update {
                    WhoisUiState(
                        domain = result.domain,
                        isRunning = false,
                        result = result.text.takeIf { it.isNotBlank() },
                        error = result.error,
                    )
                }
            } catch (_: Exception) {
                _whoisState.update { state ->
                    state.copy(isRunning = false, error = "whois_failed")
                }
            }
        }
    }

    fun stopWhois() {
        whoisJob?.cancel()
        whoisJob = null
        _whoisState.update { it.copy(isRunning = false) }
    }

    fun clearWhois() {
        stopWhois()
        _whoisState.value = WhoisUiState(domain = _whoisState.value.domain)
    }
}
