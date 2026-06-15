package com.leekleak.trafficlight.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SshOptions(
    val port: Int = 22,
    val compression: Boolean = false,
    val verbose: Int = 0,
    val strictHostKeyChecking: Boolean = true,
    val connectTimeoutSec: Int = 10,
    val connectionAttempts: Int = 1,
    val keepAlive: Boolean = true,
    val agentForwarding: Boolean = false,
    val ipv4Only: Boolean = false,
    val ipv6Only: Boolean = false,
    val batchMode: Boolean = false,
)

@Serializable
data class SshProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val host: String,
    val username: String,
    val password: String = "",
    val options: SshOptions = SshOptions(),
) {
    fun displayName(): String = label.ifBlank { "$username@$host" }
}

data class SshConnectionResult(
    val success: Boolean,
    val serverVersion: String? = null,
    val remoteHostname: String? = null,
    val commandOutput: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
)
