package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

data class SiteIpResult(
    val host: String,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val primaryIp: String?,
    val ipInfo: IpLookupResult?,
    val error: String?,
)

class SiteIpManager(
    private val pingManager: PingManager,
    private val ipLookupManager: IpLookupManager,
) {

    suspend fun resolve(urlOrHost: String): SiteIpResult = withContext(Dispatchers.IO) {
        val host = pingManager.normalizeHost(urlOrHost)
        if (host.isBlank()) {
            return@withContext SiteIpResult(
                host = urlOrHost.trim(),
                ipv4Addresses = emptyList(),
                ipv6Addresses = emptyList(),
                primaryIp = null,
                ipInfo = null,
                error = "invalid_host",
            )
        }

        if (ipLookupManager.isValidIp(host)) {
            val lookup = ipLookupManager.lookup(host)
            val isV6 = host.contains(':')
            return@withContext SiteIpResult(
                host = host,
                ipv4Addresses = if (isV6) emptyList() else listOf(host),
                ipv6Addresses = if (isV6) listOf(host) else emptyList(),
                primaryIp = host,
                ipInfo = lookup.takeIf { it.error == null },
                error = lookup.error,
            )
        }

        try {
            val addresses = InetAddress.getAllByName(host)
            val ipv4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
            val ipv6 = addresses.filterIsInstance<Inet6Address>().mapNotNull { it.hostAddress }
            val allAddresses = (ipv4 + ipv6).distinct()

            if (allAddresses.isEmpty()) {
                return@withContext SiteIpResult(
                    host = host,
                    ipv4Addresses = emptyList(),
                    ipv6Addresses = emptyList(),
                    primaryIp = null,
                    ipInfo = null,
                    error = "resolve_failed",
                )
            }

            val primaryIp = ipv4.firstOrNull() ?: allAddresses.first()
            val lookup = ipLookupManager.lookup(primaryIp)

            SiteIpResult(
                host = host,
                ipv4Addresses = ipv4,
                ipv6Addresses = ipv6,
                primaryIp = primaryIp,
                ipInfo = lookup.takeIf { it.error == null },
                error = if (lookup.error != null && allAddresses.isNotEmpty()) null else lookup.error,
            )
        } catch (_: UnknownHostException) {
            SiteIpResult(
                host = host,
                ipv4Addresses = emptyList(),
                ipv6Addresses = emptyList(),
                primaryIp = null,
                ipInfo = null,
                error = "resolve_failed",
            )
        } catch (_: Exception) {
            SiteIpResult(
                host = host,
                ipv4Addresses = emptyList(),
                ipv6Addresses = emptyList(),
                primaryIp = null,
                ipInfo = null,
                error = "resolve_failed",
            )
        }
    }
}
