package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

data class PingReply(
    val sequence: Int,
    val timeMs: Double?,
    val message: String,
)

data class PingResult(
    val host: String,
    val replies: List<PingReply>,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val packetLossPercent: Int?,
    val error: String?,
)

class PingManager {

    suspend fun ping(
        host: String,
        count: Int = 4,
        onReply: suspend (PingReply) -> Unit = {},
    ): PingResult = withContext(Dispatchers.IO) {
        val cleanHost = sanitizeHost(host)
        if (cleanHost.isBlank()) {
            return@withContext PingResult(
                host = host,
                replies = emptyList(),
                minMs = null,
                avgMs = null,
                maxMs = null,
                packetLossPercent = null,
                error = "invalid_host",
            )
        }

        val process = ProcessBuilder("ping", "-c", count.toString(), "-w", "10", cleanHost)
            .redirectErrorStream(true)
            .start()

        currentCoroutineContext().job.invokeOnCompletion {
            process.destroy()
        }

        val replies = mutableListOf<PingReply>()
        var error: String? = null
        var minMs: Double? = null
        var avgMs: Double? = null
        var maxMs: Double? = null
        var packetLossPercent: Int? = null

        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                currentCoroutineContext().ensureActive()
                when {
                    line.startsWith("ping:") -> {
                        error = line.removePrefix("ping: ").trim()
                    }
                    line.contains("bytes from") -> {
                        val reply = parseReplyLine(line)
                        if (reply != null) {
                            replies.add(reply)
                            onReply(reply)
                        }
                    }
                    line.contains("packet loss") -> {
                        packetLossPercent = parsePacketLoss(line)
                    }
                    line.contains("rtt min/avg/max") -> {
                        val stats = parseRttStats(line)
                        minMs = stats.first
                        avgMs = stats.second
                        maxMs = stats.third
                    }
                }
            }
        }

        val exitCode = process.waitFor()
        currentCoroutineContext().ensureActive()

        if (error == null && replies.isEmpty() && exitCode != 0) {
            error = "ping_failed"
        }
        if (error == null && packetLossPercent == 100) {
            error = "host_unreachable"
        }

        PingResult(
            host = cleanHost,
            replies = replies,
            minMs = minMs,
            avgMs = avgMs,
            maxMs = maxMs,
            packetLossPercent = packetLossPercent,
            error = error,
        )
    }

    fun normalizeHost(input: String): String = sanitizeHost(input)

    suspend fun pingHop(ttl: Int, builder: (Int) -> Array<String>): String = withContext(Dispatchers.IO) {
        runPingProcess(builder(ttl))
    }

    suspend fun findWorkingTtlCommand(host: String): ((Int) -> Array<String>)? =
        withContext(Dispatchers.IO) {
            val cleanHost = sanitizeHost(host)
            if (cleanHost.isBlank()) return@withContext null

            val candidates = pingTtlCommandBuilders(cleanHost)
            for (builder in candidates) {
                val probe = runPingProcess(builder(1))
                if (probe.startsWith("ping:") || isInvalidPingOption(probe)) continue
                if (ttlCommandWorks(builder, cleanHost)) {
                    return@withContext builder
                }
            }
            candidates.firstOrNull { builder ->
                val probe = runPingProcess(builder(1))
                !probe.startsWith("ping:") && !isInvalidPingOption(probe)
            }
        }

    private fun pingTtlCommandBuilders(host: String): List<(Int) -> Array<String>> = listOf(
        { ttl -> arrayOf("ping", "-c", "1", "-t", ttl.toString(), "-w", "3", host) },
        { ttl -> arrayOf("ping", "-c", "1", "-t", ttl.toString(), "-W", "3", host) },
        { ttl -> arrayOf("/system/bin/ping", "-c", "1", "-t", ttl.toString(), "-w", "3", host) },
        { ttl -> arrayOf("/system/bin/ping", "-c", "1", "-t", ttl.toString(), "-W", "3", host) },
        { ttl -> arrayOf("ping", "-c", "1", "-T", ttl.toString(), "-w", "3", host) },
        { ttl -> arrayOf("ping", "-c", "1", "-h", ttl.toString(), "-w", "3", host) },
        { ttl -> arrayOf("ping", "-c", "1", "-w", "3", "--ttl=$ttl", host) },
    )

    private fun ttlCommandWorks(builder: (Int) -> Array<String>, host: String): Boolean {
        val lowTtlOutput = runPingProcess(builder(1))
        if (lowTtlOutput.isBlank() || isInvalidPingOption(lowTtlOutput)) return false
        if (isTtlExceeded(lowTtlOutput)) return true

        val highTtlOutput = runPingProcess(builder(20))
        val lowReached = hasEchoReply(lowTtlOutput, host)
        val highReached = hasEchoReply(highTtlOutput, host)
        return highReached && !lowReached
    }

    private fun isTtlExceeded(output: String): Boolean {
        return output.contains("Time to live exceeded", ignoreCase = true) ||
            output.contains("ttl exceeded", ignoreCase = true) ||
            output.contains("Hop limit exceeded", ignoreCase = true)
    }

    private fun hasEchoReply(output: String, host: String): Boolean {
        return output.contains("bytes from", ignoreCase = true) &&
            !isTtlExceeded(output)
    }

    private fun isInvalidPingOption(output: String): Boolean {
        return output.contains("invalid option", ignoreCase = true) ||
            output.contains("unknown option", ignoreCase = true) ||
            output.contains("unrecognized option", ignoreCase = true)
    }

    private fun runPingProcess(command: Array<String>): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        return try {
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            process.destroy()
            ""
        }
    }

    private fun sanitizeHost(input: String): String {
        var host = input.trim()
        if (host.startsWith("http://", ignoreCase = true)) host = host.substring(7)
        if (host.startsWith("https://", ignoreCase = true)) host = host.substring(8)
        host = host.substringBefore('/')
        host = host.substringBefore(':')
        return host.trim()
    }

    private fun parseReplyLine(line: String): PingReply? {
        val seqMatcher = SEQ_PATTERN.matcher(line)
        val timeMatcher = TIME_PATTERN.matcher(line)
        val sequence = if (seqMatcher.find()) seqMatcher.group(1)?.toIntOrNull() ?: 0 else 0
        val timeMs = if (timeMatcher.find()) timeMatcher.group(1)?.toDoubleOrNull() else null
        return PingReply(sequence = sequence, timeMs = timeMs, message = line.trim())
    }

    private fun parsePacketLoss(line: String): Int? {
        val match = PACKET_LOSS_PATTERN.matcher(line)
        return if (match.find()) match.group(1)?.toIntOrNull() else null
    }

    private fun parseRttStats(line: String): Triple<Double?, Double?, Double?> {
        val match = RTT_PATTERN.matcher(line)
        return if (match.find()) {
            Triple(
                match.group(1)?.toDoubleOrNull(),
                match.group(2)?.toDoubleOrNull(),
                match.group(3)?.toDoubleOrNull(),
            )
        } else {
            Triple(null, null, null)
        }
    }

    companion object {
        private val SEQ_PATTERN = Pattern.compile("icmp_seq=(\\d+)")
        private val TIME_PATTERN = Pattern.compile("time[=<]([\\d.]+)\\s*ms", Pattern.CASE_INSENSITIVE)
        private val PACKET_LOSS_PATTERN = Pattern.compile("(\\d+)%\\s*packet loss")
        private val RTT_PATTERN = Pattern.compile(
            "rtt min/avg/max(?:/mdev)?\\s*=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
