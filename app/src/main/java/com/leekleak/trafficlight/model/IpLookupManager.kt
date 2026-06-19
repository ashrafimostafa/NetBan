package com.leekleak.trafficlight.model

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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
) {
    val displayFlagEmoji: String?
        get() = countryCode?.let(::countryCodeToFlagEmoji) ?: flagEmoji
}

private data class CloudflareTrace(
    val ip: String,
    val countryCode: String?,
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
    val connection: IpConnection? = null,
)

@Serializable
private data class IpConnection(
    val asn: Int? = null,
    val org: String? = null,
    val isp: String? = null,
)

@Serializable
private data class IpTimezone(val id: String? = null)

@Serializable
private data class IpFlag(val emoji: String? = null)

@Serializable
private data class IpApiCoResponse(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val org: String? = null,
    val asn: String? = null,
    val timezone: String? = null,
    val version: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
private data class IpifyResponse(val ip: String? = null)

@Serializable
private data class IpInfoIoResponse(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val loc: String? = null,
    val org: String? = null,
    val timezone: String? = null,
)

@Serializable
private data class GeoJsResponse(
    val ip: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val city: String? = null,
    val region: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val timezone: String? = null,
    @SerialName("organization_name") val organizationName: String? = null,
)

@Serializable
private data class IpSbResponse(
    val ip: String? = null,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isp: String? = null,
    val organization: String? = null,
    val timezone: String? = null,
    val asn: Int? = null,
    @SerialName("asn_organization") val asnOrganization: String? = null,
)

class IpLookupManager {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(ip: String): IpLookupResult = withContext(Dispatchers.IO) {
        val cleanIp = ip.trim()
        if (!isValidIp(cleanIp)) {
            return@withContext IpLookupResult(ip = cleanIp, error = "invalid_ip")
        }

        val candidates = collectGeoCandidates(cleanIp, network = null)
        mergeLookupResult(cleanIp, trustedCountryCode = null, candidates)
            ?: IpLookupResult(ip = cleanIp, error = "lookup_failed")
    }

    suspend fun lookupSelf(network: Network? = null): IpLookupResult = withContext(Dispatchers.IO) {
        val ip = resolvePublicIp(network)
        if (ip != null) {
            val trace = resolveCloudflareTrace(network)
            val trustedCountry = trace?.countryCode?.takeIf { trace.ip == ip }
            val sbSelf = lookupFromIpSbSelf(network)?.takeIf { it.ip == ip }
            val candidates = buildList {
                sbSelf?.let { add(it) }
                addAll(collectGeoCandidates(ip, network))
            }
            mergeLookupResult(ip, trustedCountry, candidates)?.let { return@withContext it }
            sbSelf?.let { return@withContext it.copy(ip = ip) }
            return@withContext IpLookupResult(ip = ip)
        }

        lookupSelfLegacy(network, ip)
    }

    private fun collectGeoCandidates(ip: String, network: Network?): List<IpLookupResult> {
        return listOfNotNull(
            lookupFromIpSb(ip, network),
            lookupFromIpInfoIo(ip, network),
            lookupFromGeoJs(ip, network),
            lookupFromIpWhoIs(ip, network),
            lookupFromIpApiCoIp(ip, network),
        )
    }

    private fun mergeLookupResult(
        ip: String,
        trustedCountryCode: String?,
        candidates: List<IpLookupResult>,
    ): IpLookupResult? {
        val usable = candidates.filter { it.error == null && it.ip.isNotBlank() }
        val countryCode = normalizeCountryCode(trustedCountryCode)
            ?: majorityCountryCode(usable)
            ?: usable.firstNotNullOfOrNull { normalizeCountryCode(it.countryCode) }

        if (countryCode == null && usable.isEmpty()) return null

        val detail = usable.firstOrNull { matchesCountry(it, countryCode) && hasLocationDetail(it) }
            ?: usable.firstOrNull { matchesCountry(it, countryCode) }
            ?: usable.maxByOrNull { detailScore(it) }
            ?: IpLookupResult(ip = ip)

        val countryName = countryCode?.let(::countryNameFromCode)
            ?: detail.country?.takeIf { it.isNotBlank() }

        return detail.copy(
            ip = ip,
            country = countryName,
            countryCode = countryCode,
            flagEmoji = null,
        )
    }

    private fun lookupSelfLegacy(network: Network?, resolvedIp: String?): IpLookupResult {
        return lookupFromIpWhoIsSelf(network).takeUnless { it.error != null }
            ?: lookupFromIpSbSelf(network)
            ?: lookupFromIpApiCo(network)
            ?: lookupFromIpInfoIoSelf(network)
            ?: IpLookupResult(ip = resolvedIp.orEmpty(), error = "lookup_failed")
    }

    private fun resolveCloudflareTrace(network: Network?): CloudflareTrace? {
        val body = httpGet("https://1.1.1.1/cdn-cgi/trace", network) ?: return null
        val fields = body.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator) to line.substring(separator + 1).trim()
            }
            .toMap()

        val ip = fields["ip"]?.takeIf { isValidIp(it) } ?: return null
        return CloudflareTrace(
            ip = ip,
            countryCode = normalizeCountryCode(fields["loc"]),
        )
    }

    private fun resolvePublicIp(network: Network?): String? {
        val votes = linkedMapOf<String, Int>()

        fun vote(ip: String?) {
            val normalized = ip?.trim() ?: return
            if (!isValidIp(normalized)) return
            votes[normalized] = (votes[normalized] ?: 0) + 1
        }

        vote(fetchIpifyIpv4(network))
        vote(httpGetPlain("https://api.ip.sb/ip", network))
        vote(httpGetPlain("https://ipv4.icanhazip.com", network))
        vote(httpGetPlain("https://checkip.amazonaws.com", network))
        vote(httpGetPlain("https://ifconfig.me/ip", network))
        lookupFromIpInfoIoSelf(network)?.ip?.let { vote(it) }
        lookupFromIpSbSelf(network)?.ip?.let { vote(it) }
        resolveCloudflareTrace(network)?.ip?.let { vote(it) }

        if (votes.isEmpty()) return null

        return votes.entries.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { if (IPV4_PATTERN.matcher(it.key).matches()) 1 else 0 },
        )?.key
    }

    private fun fetchIpifyIpv4(network: Network?): String? {
        val body = httpGet("https://api.ipify.org?format=json", network) ?: return null
        return try {
            json.decodeFromString<IpifyResponse>(body).ip?.trim()?.takeIf { isValidIp(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGetPlain(urlString: String, network: Network?): String? {
        return httpGet(urlString, network)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun lookupFromIpWhoIsSelf(network: Network?): IpLookupResult =
        lookupFromIpWhoIsResponse(httpGet("https://ipwho.is/", network), fallbackIp = "")

    private fun lookupFromIpWhoIs(ip: String, network: Network?): IpLookupResult? =
        lookupFromIpWhoIsResponse(httpGet("https://ipwho.is/$ip", network), fallbackIp = ip)
            .takeIf { it.error == null }

    private fun lookupFromIpWhoIsResponse(body: String?, fallbackIp: String): IpLookupResult {
        if (body == null) {
            return IpLookupResult(ip = fallbackIp, error = "lookup_failed")
        }
        return try {
            val response = json.decodeFromString<IpWhoIsResponse>(body)
            if (!response.success) {
                return IpLookupResult(ip = fallbackIp, error = "lookup_failed")
            }
            IpLookupResult(
                ip = response.ip ?: fallbackIp,
                type = response.type,
                continent = response.continent,
                country = response.country,
                countryCode = response.countryCode,
                region = response.region,
                city = response.city,
                latitude = response.latitude,
                longitude = response.longitude,
                isp = response.isp ?: response.connection?.isp,
                org = response.org ?: response.connection?.org,
                asn = response.asn ?: response.connection?.asn?.toString(),
                timezone = response.timezone?.id,
                flagEmoji = response.flag?.emoji,
            )
        } catch (_: Exception) {
            IpLookupResult(ip = fallbackIp, error = "lookup_failed")
        }
    }

    private fun lookupFromIpSbSelf(network: Network?): IpLookupResult? {
        val body = httpGet("https://api.ip.sb/geoip", network) ?: return null
        return parseIpSb(body)
    }

    private fun lookupFromIpSb(ip: String, network: Network?): IpLookupResult? {
        val body = httpGet("https://api.ip.sb/geoip/$ip", network) ?: return null
        return parseIpSb(body)
    }

    private fun parseIpSb(body: String): IpLookupResult? {
        return try {
            val response = json.decodeFromString<IpSbResponse>(body)
            val ip = response.ip ?: return null
            IpLookupResult(
                ip = ip,
                country = response.country,
                countryCode = response.countryCode,
                region = response.region,
                city = response.city,
                latitude = response.latitude,
                longitude = response.longitude,
                isp = response.isp,
                org = response.organization ?: response.asnOrganization,
                asn = response.asn?.toString(),
                timezone = response.timezone,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupFromIpApiCo(network: Network?): IpLookupResult? {
        val body = httpGet("https://ipapi.co/json/", network) ?: return null
        return parseIpApiCo(body)
    }

    private fun lookupFromIpApiCoIp(ip: String, network: Network?): IpLookupResult? {
        val body = httpGet("https://ipapi.co/$ip/json/", network) ?: return null
        return parseIpApiCo(body)
    }

    private fun parseIpApiCo(body: String): IpLookupResult? {
        return try {
            val response = json.decodeFromString<IpApiCoResponse>(body)
            val ip = response.ip ?: return null
            IpLookupResult(
                ip = ip,
                type = response.version,
                country = response.countryName,
                countryCode = response.countryCode,
                region = response.region,
                city = response.city,
                latitude = response.latitude,
                longitude = response.longitude,
                org = response.org,
                asn = response.asn,
                timezone = response.timezone,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupFromIpInfoIoSelf(network: Network?): IpLookupResult? {
        val body = httpGet("https://ipinfo.io/json", network) ?: return null
        return parseIpInfoIo(body)
    }

    private fun lookupFromIpInfoIo(ip: String, network: Network?): IpLookupResult? {
        val body = httpGet("https://ipinfo.io/$ip/json", network) ?: return null
        return parseIpInfoIo(body)
    }

    private fun parseIpInfoIo(body: String): IpLookupResult? {
        return try {
            val response = json.decodeFromString<IpInfoIoResponse>(body)
            val ip = response.ip ?: return null
            val (latitude, longitude) = parseLatLong(response.loc)
            val countryCode = normalizeCountryCode(response.country)
            IpLookupResult(
                ip = ip,
                country = countryCode?.let(::countryNameFromCode),
                countryCode = countryCode,
                region = response.region,
                city = response.city,
                latitude = latitude,
                longitude = longitude,
                org = response.org,
                timezone = response.timezone,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupFromGeoJs(ip: String, network: Network?): IpLookupResult? {
        val body = httpGet("https://get.geojs.io/v1/ip/geo/$ip.json", network) ?: return null
        return try {
            val response = json.decodeFromString<GeoJsResponse>(body)
            val resolvedIp = response.ip ?: ip
            IpLookupResult(
                ip = resolvedIp,
                country = response.country,
                countryCode = response.countryCode,
                region = response.region,
                city = response.city,
                latitude = response.latitude?.toDoubleOrNull(),
                longitude = response.longitude?.toDoubleOrNull(),
                org = response.organizationName,
                timezone = response.timezone,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun isValidIp(input: String): Boolean {
        val value = input.trim()
        return IPV4_PATTERN.matcher(value).matches() || IPV6_PATTERN.matcher(value).matches()
    }

    private fun httpGet(urlString: String, network: Network? = null): String? {
        val connection = (network?.openConnection(URL(urlString))
            ?: URL(urlString).openConnection()) as HttpURLConnection
        connection.apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "*/*")
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

private fun countryCodeToFlagEmoji(countryCode: String): String? {
    val code = countryCode.trim().uppercase(Locale.US)
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
    return code.map { char ->
        Character.toChars(0x1F1E6 + (char.code - 'A'.code))
    }.joinToString("") { String(it) }
}

private fun countryNameFromCode(countryCode: String): String? {
    val code = countryCode.trim().uppercase(Locale.US)
    if (code.length != 2) return null
    return Locale("", code).getDisplayCountry(Locale.getDefault()).takeIf { it.isNotBlank() }
}

private fun parseLatLong(loc: String?): Pair<Double?, Double?> {
    val parts = loc?.split(',')?.map { it.trim() } ?: return null to null
    if (parts.size != 2) return null to null
    return parts[0].toDoubleOrNull() to parts[1].toDoubleOrNull()
}

private fun normalizeCountryCode(code: String?): String? {
    val normalized = code?.trim()?.uppercase(Locale.US) ?: return null
    if (normalized.length != 2 || !normalized.all { it in 'A'..'Z' }) return null
    return normalized
}

private fun matchesCountry(result: IpLookupResult, countryCode: String?): Boolean {
    val code = countryCode ?: return true
    return normalizeCountryCode(result.countryCode) == code
}

private fun hasLocationDetail(result: IpLookupResult): Boolean =
    !result.city.isNullOrBlank() || !result.region.isNullOrBlank()

private fun detailScore(result: IpLookupResult): Int {
    var score = 0
    if (!result.city.isNullOrBlank()) score += 4
    if (!result.region.isNullOrBlank()) score += 2
    if (!result.isp.isNullOrBlank()) score += 2
    if (!result.org.isNullOrBlank()) score += 1
    if (result.latitude != null && result.longitude != null) score += 1
    if (!result.timezone.isNullOrBlank()) score += 1
    return score
}

private fun majorityCountryCode(results: List<IpLookupResult>): String? {
    return results
        .mapNotNull { normalizeCountryCode(it.countryCode) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}
