package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.util.regex.Pattern

data class TracerouteHop(
    val hop: Int,
    val address: String?,
    val hostname: String?,
    val timeMs: Double?,
    val timedOut: Boolean,
    val isDestination: Boolean,
    val detail: String?,
)

data class TracerouteResult(
    val host: String,
    val resolvedIp: String?,
    val hops: List<TracerouteHop>,
    val reachedDestination: Boolean,
    val error: String?,
)

class TracerouteManager(
    private val pingManager: PingManager,
) {

    suspend fun trace(
        host: String,
        maxHops: Int = 30,
        onHop: suspend (TracerouteHop) -> Unit = {},
    ): TracerouteResult = withContext(Dispatchers.IO) {
        val cleanHost = pingManager.normalizeHost(host)
        if (cleanHost.isBlank()) {
            return@withContext TracerouteResult(
                host = host,
                resolvedIp = null,
                hops = emptyList(),
                reachedDestination = false,
                error = "invalid_host",
            )
        }

        val resolvedIp = resolveIpv4(cleanHost)

        val pingResult = traceWithPing(cleanHost, resolvedIp, maxHops, onHop)
        if (pingResult.hops.any { !it.timedOut } || pingResult.reachedDestination) {
            return@withContext pingResult
        }

        val nativeResult = traceWithNativeTraceroute(cleanHost, resolvedIp, maxHops, onHop)
        if (nativeResult != null && nativeResult.hops.isNotEmpty()) {
            return@withContext nativeResult
        }

        pingResult
    }

    private suspend fun traceWithPing(
        cleanHost: String,
        resolvedIp: String?,
        maxHops: Int,
        onHop: suspend (TracerouteHop) -> Unit,
    ): TracerouteResult {
        val pingCommandBuilder = pingManager.findWorkingTtlCommand(cleanHost)
        if (pingCommandBuilder == null) {
            return TracerouteResult(
                host = cleanHost,
                resolvedIp = resolvedIp,
                hops = emptyList(),
                reachedDestination = false,
                error = "traceroute_failed: ping -t not supported",
            )
        }

        val hops = mutableListOf<TracerouteHop>()
        var error: String? = null
        var reachedDestination = false
        var consecutiveTimeouts = 0
        var lastOutput = ""

        for (ttl in 1..maxHops) {
            currentCoroutineContext().ensureActive()

            val output = pingManager.pingHop(ttl, pingCommandBuilder)
            lastOutput = output

            if (output.startsWith("ping:")) {
                error = output.removePrefix("ping: ").trim()
                break
            }

            val hop = parsePingHopOutput(ttl, output, resolvedIp, cleanHost)
            hops.add(hop)
            onHop(hop)

            if (hop.timedOut) {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= 8 && hops.size >= 8) {
                    break
                }
                continue
            }

            consecutiveTimeouts = 0

            if (hop.isDestination) {
                reachedDestination = true
                break
            }
        }

        if (error == null && hops.isEmpty()) {
            error = diagnosticError(lastOutput)
        }

        val (finalHops, finalReached) = finalizeDestination(hops, resolvedIp, reachedDestination)
        return TracerouteResult(
            host = cleanHost,
            resolvedIp = resolvedIp,
            hops = finalHops,
            reachedDestination = finalReached,
            error = error,
        )
    }

    private fun diagnosticError(lastOutput: String): String {
        val snippet = lastOutput.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
            ?.trim()
        return if (snippet.isNullOrBlank()) {
            "traceroute_failed"
        } else {
            "traceroute_failed: $snippet"
        }
    }

    private suspend fun traceWithNativeTraceroute(
        cleanHost: String,
        resolvedIp: String?,
        maxHops: Int,
        onHop: suspend (TracerouteHop) -> Unit,
    ): TracerouteResult? {
        val commandSets = listOf(
            arrayOf("traceroute", "-4", "-I", "-m", maxHops.toString(), "-w", "3", "-q", "1", cleanHost),
            arrayOf("traceroute", "-4", "-m", maxHops.toString(), "-w", "3", "-q", "1", cleanHost),
            arrayOf("/system/bin/traceroute", "-4", "-I", "-m", maxHops.toString(), "-w", "3", "-q", "1", cleanHost),
            arrayOf("/system/bin/traceroute", "-4", "-m", maxHops.toString(), "-w", "3", "-q", "1", cleanHost),
        )

        for (command in commandSets) {
            val result = runTracerouteProcess(command, cleanHost, resolvedIp, onHop)
            if (result != null && result.hops.isNotEmpty()) {
                return result
            }
        }
        return null
    }

    private suspend fun runTracerouteProcess(
        command: Array<String>,
        cleanHost: String,
        fallbackResolvedIp: String?,
        onHop: suspend (TracerouteHop) -> Unit,
    ): TracerouteResult? {
        val process = try {
            ProcessBuilder(*command).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return null
        }

        currentCoroutineContext().job.invokeOnCompletion {
            process.destroy()
        }

        var resolvedIp = fallbackResolvedIp
        var error: String? = null
        val hops = mutableListOf<TracerouteHop>()
        var reachedDestination = false

        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    currentCoroutineContext().ensureActive()
                    when {
                        isTracerouteCommandError(line) -> {
                            error = line.trim()
                            return@useLines
                        }
                        line.contains("traceroute to", ignoreCase = true) -> {
                            TRACEROUTE_HEADER_PATTERN.matcher(line).let { matcher ->
                                if (matcher.find()) {
                                    resolvedIp = matcher.group(1)?.trim()
                                }
                            }
                        }
                        else -> {
                            val hop = parseTracerouteLine(line, resolvedIp, cleanHost) ?: continue
                            hops.add(hop)
                            onHop(hop)
                            if (hop.isDestination) {
                                reachedDestination = true
                            }
                        }
                    }
                }
            }
            process.waitFor()
        } catch (_: Exception) {
            process.destroy()
            return null
        }

        currentCoroutineContext().ensureActive()

        if (hops.isEmpty()) return null

        val (finalHops, finalReached) = finalizeDestination(hops, resolvedIp, reachedDestination)
        return TracerouteResult(
            host = cleanHost,
            resolvedIp = resolvedIp,
            hops = finalHops,
            reachedDestination = finalReached,
            error = error,
        )
    }

    private fun parseTracerouteLine(
        line: String,
        resolvedIp: String?,
        host: String,
    ): TracerouteHop? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.first().isDigit()) return null

        val hop = trimmed.substringBefore(' ').toIntOrNull() ?: return null
        val rest = trimmed.substring(trimmed.indexOf(' ') + 1).trim()
        if (rest.isEmpty()) return null

        if (rest.startsWith("*")) {
            return timeoutHop(hop, trimmed)
        }

        val timeMs = parseTime(rest)
        val hostIpMatcher = TRACEROUTE_HOST_IP_PATTERN.matcher(rest)
        if (hostIpMatcher.find()) {
            val hostname = hostIpMatcher.group(1)?.trim()
            val address = hostIpMatcher.group(2)?.trim()
            return TracerouteHop(
                hop = hop,
                address = address,
                hostname = hostname?.takeUnless { it == address },
                timeMs = timeMs,
                timedOut = false,
                isDestination = isDestinationAddress(address, resolvedIp, host),
                detail = trimmed,
            )
        }

        val ip = rest.split(Regex("\\s+")).firstOrNull()?.takeIf { IP_PATTERN.matcher(it).matches() }
        if (ip != null) {
            return TracerouteHop(
                hop = hop,
                address = ip,
                hostname = null,
                timeMs = timeMs,
                timedOut = false,
                isDestination = isDestinationAddress(ip, resolvedIp, host),
                detail = trimmed,
            )
        }

        return null
    }

    private fun finalizeDestination(
        hops: List<TracerouteHop>,
        resolvedIp: String?,
        reachedDestination: Boolean,
    ): Pair<List<TracerouteHop>, Boolean> {
        if (reachedDestination || hops.isEmpty()) return hops to reachedDestination

        val lastResponsiveIndex = hops.indexOfLast { !it.timedOut }
        if (lastResponsiveIndex < 0) return hops to false

        val lastHop = hops[lastResponsiveIndex]
        val matchesResolved = resolvedIp != null &&
            lastHop.address.equals(resolvedIp, ignoreCase = true)
        val isFinalHop = lastResponsiveIndex == hops.lastIndex

        if (!matchesResolved && !isFinalHop) return hops to false

        val updated = hops.toMutableList()
        updated[lastResponsiveIndex] = lastHop.copy(isDestination = true)
        return updated to true
    }

    private fun isTracerouteCommandError(line: String): Boolean {
        return line.startsWith("traceroute:") ||
            line.contains("not found", ignoreCase = true) ||
            line.contains("invalid option", ignoreCase = true) ||
            line.contains("unknown option", ignoreCase = true) ||
            line.contains("Permission denied", ignoreCase = true)
    }

    private fun resolveIpv4(host: String): String? {
        return runCatching {
            val addresses = InetAddress.getAllByName(host)
            addresses.firstOrNull { it is Inet4Address }?.hostAddress
                ?: addresses.firstOrNull()?.hostAddress
        }.getOrNull()
    }

    private fun parsePingHopOutput(
        ttl: Int,
        output: String,
        resolvedIp: String?,
        host: String,
    ): TracerouteHop {
        val detail = output.lineSequence().firstOrNull { line ->
            line.contains("From ", ignoreCase = true) ||
                line.contains("bytes from", ignoreCase = true)
        }?.trim() ?: output.lineSequence().firstOrNull { it.isNotBlank() }?.trim()

        if (output.isBlank()) {
            return timeoutHop(ttl, detail)
        }

        if (isTtlExceeded(output)) {
            val fromMatcher = FROM_PATTERN.matcher(output)
            if (fromMatcher.find()) {
                val address = fromMatcher.group(1)?.trim()
                return TracerouteHop(
                    hop = ttl,
                    address = address,
                    hostname = null,
                    timeMs = parseTime(output),
                    timedOut = false,
                    isDestination = false,
                    detail = detail,
                )
            }
        }

        val hostIpMatcher = HOSTNAME_IP_PATTERN.matcher(output)
        if (hostIpMatcher.find()) {
            val hostname = hostIpMatcher.group(1)?.trim()
            val address = hostIpMatcher.group(2)?.trim()
            return TracerouteHop(
                hop = ttl,
                address = address,
                hostname = hostname,
                timeMs = parseTime(output),
                timedOut = false,
                isDestination = isDestinationAddress(address, resolvedIp, host),
                detail = detail,
            )
        }

        val bytesMatcher = BYTES_FROM_PATTERN.matcher(output)
        if (bytesMatcher.find()) {
            val address = bytesMatcher.group(1)?.trim()
            return TracerouteHop(
                hop = ttl,
                address = address,
                hostname = null,
                timeMs = parseTime(output),
                timedOut = false,
                isDestination = isDestinationAddress(address, resolvedIp, host) && !isTtlExceeded(output),
                detail = detail,
            )
        }

        if (output.contains("100% packet loss") || output.contains("0 received")) {
            return timeoutHop(ttl, detail)
        }

        return timeoutHop(ttl, detail)
    }

    private fun isTtlExceeded(output: String): Boolean {
        return output.contains("Time to live exceeded", ignoreCase = true) ||
            output.contains("ttl exceeded", ignoreCase = true) ||
            output.contains("Hop limit exceeded", ignoreCase = true)
    }

    private fun timeoutHop(ttl: Int, detail: String?): TracerouteHop {
        return TracerouteHop(
            hop = ttl,
            address = null,
            hostname = null,
            timeMs = null,
            timedOut = true,
            isDestination = false,
            detail = detail,
        )
    }

    private fun parseTime(output: String): Double? {
        val matcher = TIME_PATTERN.matcher(output)
        return if (matcher.find()) matcher.group(1)?.toDoubleOrNull() else null
    }

    private fun isDestinationAddress(
        address: String?,
        resolvedIp: String?,
        host: String,
    ): Boolean {
        if (address.isNullOrBlank()) return false
        if (resolvedIp != null && address.equals(resolvedIp, ignoreCase = true)) return true
        return address.equals(host, ignoreCase = true)
    }

    companion object {
        private val TRACEROUTE_HEADER_PATTERN = Pattern.compile(
            "traceroute to .+? \\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE,
        )
        private val TRACEROUTE_HOST_IP_PATTERN = Pattern.compile(
            "^\\s*([^\\s(]+)\\s+\\(([^)]+)\\)",
        )
        private val IP_PATTERN = Pattern.compile("^[\\d.]+$")
        private val FROM_PATTERN = Pattern.compile("(?i)From\\s+([^\\s:]+)")
        private val HOSTNAME_IP_PATTERN = Pattern.compile(
            "(?i)bytes from\\s+([^\\s(]+)\\s*\\(([^)]+)\\)",
        )
        private val BYTES_FROM_PATTERN = Pattern.compile(
            "(?i)bytes from\\s+([^:\\s(]+)",
        )
        private val TIME_PATTERN = Pattern.compile(
            "time[=<]([\\d.]+)\\s*ms",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
