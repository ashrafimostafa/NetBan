package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.util.concurrent.TimeUnit

data class SshCommandResult(
    val output: String,
    val exitCode: Int?,
    val error: String? = null,
)

class SshSession internal constructor(
    private val client: SSHClient,
    val profile: SshProfile,
    val serverVersion: String?,
    val remoteHostname: String?,
) {
    val displayTarget: String
        get() = "${profile.username}@${profile.host}:${profile.options.port}"

    suspend fun execute(command: String): SshCommandResult = withContext(Dispatchers.IO) {
        if (!client.isConnected) {
            return@withContext SshCommandResult(
                output = "",
                exitCode = null,
                error = "disconnected",
            )
        }

        try {
            client.startSession().use { session ->
                session.exec(command).use { exec ->
                    val output = readCommandOutput(exec)
                    SshCommandResult(
                        output = output,
                        exitCode = exec.exitStatus,
                    )
                }
            }
        } catch (e: Exception) {
            SshCommandResult(
                output = "",
                exitCode = null,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    fun disconnect() {
        runCatching { client.disconnect() }
        runCatching { client.close() }
    }

    private fun readCommandOutput(exec: Session.Command): String {
        val stdout = exec.inputStream.bufferedReader().readText()
        val stderr = exec.errorStream.bufferedReader().readText()
        exec.join(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)

        return buildString {
            if (stdout.isNotBlank()) append(stdout.trimEnd())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderr.trimEnd())
            }
        }.ifBlank {
            exec.exitStatus?.takeIf { it != 0 }?.let { "exit $it" } ?: ""
        }
    }
}

class SshManager {

    fun buildCommandLine(profile: SshProfile, remoteCommand: String? = null): String {
        val options = profile.options
        val args = mutableListOf("ssh")

        if (options.ipv4Only) args += "-4"
        if (options.ipv6Only) args += "-6"
        if (options.port != 22) args += listOf("-p", options.port.toString())
        if (options.compression) args += "-C"
        if (options.agentForwarding) args += "-A"
        if (options.batchMode) args += "-o BatchMode=yes"
        repeat(options.verbose.coerceIn(0, 3)) { args += "-v" }

        args += listOf("-o", "ConnectTimeout=${options.connectTimeoutSec}")
        args += listOf("-o", "ConnectionAttempts=${options.connectionAttempts.coerceAtLeast(1)}")
        args += listOf(
            "-o",
            "StrictHostKeyChecking=${if (options.strictHostKeyChecking) "yes" else "no"}",
        )
        if (options.keepAlive) {
            args += listOf("-o", "ServerAliveInterval=30")
        }

        args += "${profile.username}@${profile.host.trim()}"
        remoteCommand?.trim()?.takeIf { it.isNotEmpty() }?.let { args += it }

        return args.joinToString(" ")
    }

    suspend fun openSession(profile: SshProfile): Pair<SshSession?, SshConnectionResult> =
        withContext(Dispatchers.IO) {
            val host = profile.host.trim()
            val username = profile.username.trim()
            if (host.isBlank() || username.isBlank()) {
                return@withContext null to SshConnectionResult(success = false, error = "invalid_profile")
            }

            val startedAt = System.currentTimeMillis()
            SshSecuritySetup.ensureInitialized()
            val ssh = SSHClient()
            try {
                applyOptions(ssh, profile.options)
                ssh.addHostKeyVerifier(PromiscuousVerifier())
                ssh.connect(host, profile.options.port)
                ssh.authPassword(username, profile.password)

                val serverVersion = ssh.transport.serverVersion
                var remoteHostname: String? = null

                ssh.startSession().use { session ->
                    session.exec("hostname").use { exec ->
                        remoteHostname = readCommandOutput(exec).trim().ifBlank { null }
                    }
                }

                val sshSession = SshSession(
                    client = ssh,
                    profile = profile,
                    serverVersion = serverVersion,
                    remoteHostname = remoteHostname,
                )

                sshSession to SshConnectionResult(
                    success = true,
                    serverVersion = serverVersion,
                    remoteHostname = remoteHostname,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            } catch (e: Exception) {
                runCatching { ssh.disconnect() }
                runCatching { ssh.close() }
                null to SshConnectionResult(
                    success = false,
                    error = e.message ?: e.javaClass.simpleName,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            }
        }

    private fun applyOptions(ssh: SSHClient, options: SshOptions) {
        val timeoutMs = options.connectTimeoutSec.coerceIn(1, 120) * 1000
        ssh.setConnectTimeout(timeoutMs)
        ssh.setTimeout(timeoutMs)
        if (options.compression) {
            ssh.useCompression()
        }
    }

    private fun readCommandOutput(exec: Session.Command): String {
        val stdout = exec.inputStream.bufferedReader().readText()
        val stderr = exec.errorStream.bufferedReader().readText()
        exec.join(TimeUnit.SECONDS.toMillis(30), TimeUnit.MILLISECONDS)

        return buildString {
            if (stdout.isNotBlank()) append(stdout.trimEnd())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(stderr.trimEnd())
            }
        }.ifBlank { exec.exitStatus?.let { "exit $it" } ?: "" }
    }
}
