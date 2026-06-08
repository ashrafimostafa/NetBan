package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class IpLookupResult(
    val ip: String,
    val type: String? = null,
    val continent: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isp: String? = null,
    val org: String? = null,
    val asn: String? = null,
    val timezone: String? = null,
    val flagEmoji: String? = null,
    val error: String? = null,
)

@Serializable
private data class IpWhoIsResponse(
    val success: Boolean = false,
    val message: String? = null,
    val ip: String? = null,
    val type: String? = null,
    val continent: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isp: String? = null,
    val org: String? = null,
    val asn: String? = null,
    val timezone: IpTimezone? = null,
    val flag: IpFlag? = null,
)

@Serializable
private data class IpTimezone(val id: String? = null)

@Serializable
private data class IpFlag(val emoji: String? = null)

class IpLookupManager {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(ip: String): IpLookupResult = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        if (!isValidIp(cleanIp)) {
            return@withContext IpLookupResult(ip = cleanIp, error = "invalid_ip")
        }

        try {
            val body = httpGet("https://ipwho.is/$cleanIp") ?: return@withContext IpLookupResult(
                ip = cleanIp,
                error = "lookup_failed",
            )
            val response = json.decodeFromString<IpWhoIsResponse>(body)
            if (!response.success) {
                return@withContext IpLookupResult(
                    ip = cleanIp,
                    error = "lookup_failed",
                )
            }
            IpLookupResult(
                ip = response.ip ?: cleanIp,
                type = response.type,
                continent = response.continent,
                country = response.country,
                countryCode = response.countryCode,
                region = response.region,
                city = response.city,
                latitude = response.latitude,
                longitude = response.longitude,
                isp = response.isp,
                org = response.org,
                asn = response.asn,
                timezone = response.timezone?.id,
                flagEmoji = response.flag?.emoji,
            )
        } catch (_: Exception) {
            IpLookupResult(ip = cleanIp, error = "lookup_failed")
        }
    }

    fun isValidIp(input: String): Boolean {
        val value = input.trim()
        return IPV4_PATTERN.matcher(value).matches() || IPV6_PATTERN.matcher(value).matches()
    }

    private fun httpGet(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NetBan/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000

        private val IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
        )
        private val IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$"
        )
    }
}
