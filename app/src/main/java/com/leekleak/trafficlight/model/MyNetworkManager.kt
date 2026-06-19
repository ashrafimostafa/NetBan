package com.leekleak.trafficlight.model

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NetworkLinkInfo(
    val networkType: String,
    val interfaceName: String?,
    val localAddresses: List<String>,
    val dnsServers: List<String>,
    val mtu: Int?,
    val isVpn: Boolean,
    val isActive: Boolean,
)

data class MyNetworkInfo(
    val isVpnActive: Boolean,
    val vpnAppName: String?,
    val publicIp: IpLookupResult?,
    val underlyingPublicIp: IpLookupResult?,
    val activeConnection: String?,
    val underlyingConnection: String?,
    val links: List<NetworkLinkInfo>,
    val cellularOperator: String?,
    val simOperator: String?,
    val cellularNetworkType: String?,
    val isRoaming: Boolean?,
)

class MyNetworkManager(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val ipLookupManager: IpLookupManager,
) {

    suspend fun gather(): MyNetworkInfo = withContext(Dispatchers.IO) {
        val local = collectLocalInfo()
        val lookupNetwork = resolveLookupNetwork(
            vpnActive = local.isVpnActive,
            activeConnection = local.activeConnection,
        )
        val publicIp = withBoundNetwork(lookupNetwork) {
            ipLookupManager.lookupSelf(lookupNetwork)
        }
        val underlyingNetwork = if (local.isVpnActive) {
            findUnderlyingNetwork(local.underlyingConnection ?: local.activeConnection)
        } else {
            null
        }
        val underlyingPublicIp = underlyingNetwork?.let { network ->
            withBoundNetwork(network) {
                ipLookupManager.lookupSelf(network)
            }
        }
        local.copy(publicIp = publicIp, underlyingPublicIp = underlyingPublicIp)
    }

    @SuppressLint("MissingPermission")
    private suspend fun <T> withBoundNetwork(network: Network?, block: suspend () -> T): T {
        if (network == null) return block()
        val previousNetwork = connectivityManager.boundNetworkForProcess
        val bound = connectivityManager.bindProcessToNetwork(network)
        try {
            return block()
        } finally {
            if (bound) {
                connectivityManager.bindProcessToNetwork(previousNetwork)
            }
        }
    }

    private fun resolveLookupNetwork(vpnActive: Boolean, activeConnection: String?): Network? {
        if (vpnActive) {
            return connectivityManager.activeNetwork
        }

        findNetworkForConnection(activeConnection, requireNotVpn = true)?.let { return it }

        val active = connectivityManager.activeNetwork
        val activeCaps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        if (active != null && activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != true) {
            return active
        }

        return findUnderlyingNetwork(activeConnection)
    }

    private fun findNetworkForConnection(activeConnection: String?, requireNotVpn: Boolean): Network? {
        for (transport in transportsFromLabel(activeConnection)) {
            findNetworkWithTransport(transport, requireNotVpn)?.let { return it }
        }
        return null
    }

    private fun transportsFromLabel(label: String?): List<Int> {
        if (label.isNullOrBlank()) return emptyList()
        return buildList {
            if (label.contains("WiFi")) add(NetworkCapabilities.TRANSPORT_WIFI)
            if (label.contains("Cellular")) add(NetworkCapabilities.TRANSPORT_CELLULAR)
            if (label.contains("Ethernet")) add(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }

    private fun findNetworkWithTransport(transport: Int, requireNotVpn: Boolean): Network? {
        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(transport) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (!requireNotVpn || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        }
    }

    private fun findUnderlyingNetwork(preferredConnection: String?): Network? {
        findNetworkForConnection(preferredConnection, requireNotVpn = true)?.let { return it }

        return connectivityManager.allNetworks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        }
    }

    @SuppressLint("MissingPermission")
    private fun collectLocalInfo(): MyNetworkInfo {
        val activeNetwork = connectivityManager.activeNetwork
        val activeCaps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val links = connectivityManager.allNetworks.mapNotNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val linkProps = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val transports = buildTransportLabel(caps)
            if (transports.isBlank()) return@mapNotNull null

            NetworkLinkInfo(
                networkType = transports,
                interfaceName = linkProps.interfaceName,
                localAddresses = linkProps.linkAddresses.mapNotNull { it.address.hostAddress },
                dnsServers = linkProps.dnsServers.mapNotNull { it.hostAddress },
                mtu = linkProps.mtu.takeIf { it > 0 },
                isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                isActive = network == activeNetwork,
            )
        }

        val activeLink = links.find { it.isActive }
        val isVpnActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
            activeLink?.isVpn == true
        val vpnAppName = if (isVpnActive) resolveVpnAppName() else null

        val underlyingConnection = links
            .filter { !it.isVpn }
            .map { it.networkType }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

        val telephony = context.getSystemService(TelephonyManager::class.java)

        return MyNetworkInfo(
            isVpnActive = isVpnActive,
            vpnAppName = vpnAppName,
            publicIp = null,
            underlyingPublicIp = null,
            activeConnection = activeLink?.networkType,
            underlyingConnection = underlyingConnection,
            links = links,
            cellularOperator = telephony?.safeString { networkOperatorName },
            simOperator = telephony?.safeString { simOperatorName },
            cellularNetworkType = telephony?.safeValue { cellularNetworkTypeName(it) },
            isRoaming = telephony?.safeValue { it.isNetworkRoaming },
        )
    }

    private inline fun TelephonyManager.safeString(block: TelephonyManager.() -> String?): String? {
        return runCatching { block().takeIf { !it.isNullOrBlank() } }.getOrNull()
    }

    private inline fun <T> TelephonyManager?.safeValue(block: (TelephonyManager) -> T): T? {
        return this?.let { runCatching { block(it) }.getOrNull() }
    }

    private fun resolveVpnAppName(): String? {
        val packageName = Settings.Secure.getString(context.contentResolver, "always_on_vpn_app")
            ?.takeIf { it.isNotBlank() && it != "[nothing]" }
            ?: return null

        return runCatching {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun buildTransportLabel(caps: NetworkCapabilities): String {
        return buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WiFi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("Bluetooth")
        }.joinToString(" + ")
    }

    @SuppressLint("MissingPermission")
    private fun cellularNetworkTypeName(telephony: TelephonyManager): String? {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephony.dataNetworkType
        } else {
            @Suppress("DEPRECATION")
            telephony.networkType
        }
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> null
        }
    }
}
