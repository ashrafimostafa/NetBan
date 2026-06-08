package com.leekleak.trafficlight.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.regex.Pattern
import kotlinx.serialization.json.Json as KotlinxJson

data class WhoisResult(
    val domain: String,
    val text: String,
    val error: String?,
)

class WhoisManager {

    suspend fun lookup(domain: String): WhoisResult = withContext(Dispatchers.IO) {
        val cleanDomain = normalizeDomain(domain)
        if (!isValidDomain(cleanDomain)) {
            return@withContext WhoisResult(
                domain = domain,
                text = "",
                error = "invalid_domain",
            )
        }

        lookupRdap(cleanDomain)?.let { formatted ->
            return@withContext WhoisResult(
                domain = cleanDomain,
                text = formatted,
                error = null,
            )
        }

        currentCoroutineContext().ensureActive()

        try {
            lookupWhoisSocket(cleanDomain)
        } catch (_: Exception) {
            WhoisResult(
                domain = cleanDomain,
                text = "",
                error = "whois_failed",
            )
        }
    }

    fun normalizeDomain(input: String): String {
        var domain = input.trim().lowercase()
        if (domain.startsWith("http://")) domain = domain.removePrefix("http://")
        if (domain.startsWith("https://")) domain = domain.removePrefix("https://")
        domain = domain.substringBefore('/')
        domain = domain.substringBefore(':')
        if (domain.startsWith("www.")) domain = domain.removePrefix("www.")
        return domain.trim()
    }

    private fun lookupRdap(domain: String): String? {
        val urls = rdapUrlsFor(domain)
        for (url in urls) {
            val json = httpGet(url) ?: continue
            val formatted = formatRdap(json)
            if (formatted.isNotBlank()) return formatted
        }
        return null
    }

    private fun rdapUrlsFor(domain: String): List<String> {
        val tld = domain.substringAfterLast('.').lowercase()
        val direct = when (tld) {
            "com" -> "https://rdap.verisign.com/com/v1/domain/$domain"
            "net" -> "https://rdap.verisign.com/net/v1/domain/$domain"
            "org" -> "https://rdap.publicinterestregistry.org/rdap/org/domain/$domain"
            "ir" -> "https://rdap.nic.ir/domain/$domain"
            else -> null
        }
        return listOfNotNull(direct, "https://rdap.org/domain/$domain")
    }

    private fun httpGet(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/rdap+json, application/json")
            setRequestProperty("User-Agent", "NetBan/1.0")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            connection.inputStream.bufferedReader().readText()
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun formatRdap(jsonText: String): String {
        val root = runCatching { KotlinxJson.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return ""
        val sb = StringBuilder()

        root["ldhName"]?.jsonPrimitive?.contentOrNull?.let { sb.appendLine("Domain Name: $it") }
        root["handle"]?.jsonPrimitive?.contentOrNull?.let { sb.appendLine("Handle: $it") }

        root["status"]?.jsonArray?.forEach { status ->
            status.jsonPrimitive.contentOrNull?.let { sb.appendLine("Status: $it") }
        }

        root["events"]?.jsonArray?.forEach { event ->
            val obj = event.jsonObject
            val action = obj["eventAction"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val date = obj["eventDate"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            sb.appendLine("${action.replaceFirstChar { c -> c.uppercase() }} Date: $date")
        }

        root["nameservers"]?.jsonArray?.forEach { ns ->
            ns.jsonObject["ldhName"]?.jsonPrimitive?.contentOrNull?.let {
                sb.appendLine("Name Server: $it")
            }
        }

        root["secureDNS"]?.jsonObject?.let { dns ->
            dns["delegationSigned"]?.jsonPrimitive?.contentOrNull?.let {
                sb.appendLine("DNSSEC: $it")
            }
        }

        root["entities"]?.jsonArray?.forEach { entity ->
            formatRdapEntity(entity.jsonObject, sb)
        }

        return sb.toString().trim()
    }

    private fun formatRdapEntity(entity: JsonObject, sb: StringBuilder) {
        val roles = entity["roles"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.joinToString(", ")
            ?.takeIf { it.isNotBlank() }
            ?: return

        sb.appendLine()
        sb.appendLine("[$roles]")

        vcardField(entity["vcardArray"]?.jsonArray, "fn")?.let { sb.appendLine("Name: $it") }
        vcardField(entity["vcardArray"]?.jsonArray, "org")?.let { sb.appendLine("Organization: $it") }
        vcardField(entity["vcardArray"]?.jsonArray, "email")?.let { sb.appendLine("Email: $it") }
        vcardField(entity["vcardArray"]?.jsonArray, "tel")?.let { sb.appendLine("Phone: $it") }
        vcardField(entity["vcardArray"]?.jsonArray, "adr")?.let { sb.appendLine("Address: $it") }

        entity["handle"]?.jsonPrimitive?.contentOrNull?.let { sb.appendLine("Handle: $it") }

        entity["entities"]?.jsonArray?.forEach { nested ->
            formatRdapEntity(nested.jsonObject, sb)
        }
    }

    private fun vcardField(vcard: JsonArray?, field: String): String? {
        if (vcard == null || vcard.size < 2) return null
        for (entry in vcard[1].jsonArray) {
            val parts = entry.jsonArray
            if (parts.size >= 4 && parts[0].jsonPrimitive.contentOrNull == field) {
                return parts[3].jsonPrimitive.contentOrNull
            }
        }
        return null
    }

    private fun lookupWhoisSocket(cleanDomain: String): WhoisResult {
        val tld = cleanDomain.substringAfterLast('.').lowercase()
        var server = WHOIS_SERVERS[tld] ?: resolveServerFromIana(tld)
            ?: return WhoisResult(
                domain = cleanDomain,
                text = "",
                error = "whois_server_not_found",
            )

        var response = queryServer(server, queryFor(server, cleanDomain))

        var hops = 0
        while (hops < MAX_REFERRALS) {
            val referral = findReferralServer(response, server) ?: break
            server = normalizeWhoisServer(referral)
            val referralResponse = runCatching {
                queryServer(server, queryFor(server, cleanDomain))
            }.getOrNull()
            if (!referralResponse.isNullOrBlank()) {
                response = referralResponse
            }
            hops++
        }

        return WhoisResult(
            domain = cleanDomain,
            text = response.trim(),
            error = if (response.isBlank()) "whois_empty" else null,
        )
    }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.isBlank() || !domain.contains('.')) return false
        return DOMAIN_PATTERN.matcher(domain).matches()
    }

    private fun resolveServerFromIana(tld: String): String? {
        val response = queryServer("whois.iana.org", tld)
        return findReferralServer(response, "whois.iana.org")?.let { normalizeWhoisServer(it) }
    }

    private fun normalizeWhoisServer(server: String): String {
        var host = server.trim().removeSuffix(".")
        if (host.startsWith("http://", ignoreCase = true)) host = host.removePrefix("http://")
        if (host.startsWith("https://", ignoreCase = true)) host = host.removePrefix("https://")
        host = host.substringBefore('/')
        if (!host.contains('.')) host = "whois.$host"
        return host
    }

    private fun queryFor(server: String, domain: String): String {
        return if (server.contains("denic.de", ignoreCase = true)) "-$domain" else domain
    }

    private fun queryServer(server: String, query: String): String {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(server, WHOIS_PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val request = (query + "\r\n").toByteArray(Charsets.US_ASCII)
            socket.getOutputStream().write(request)
            socket.getOutputStream().flush()
            socket.shutdownOutput()

            val response = StringBuilder()
            socket.getInputStream().bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.appendLine(line)
                }
            }
            return response.toString()
        } finally {
            socket.close()
        }
    }

    private fun findReferralServer(response: String, currentServer: String): String? {
        for (pattern in REFERRAL_PATTERNS) {
            val matcher = pattern.matcher(response)
            while (matcher.find()) {
                val server = matcher.group(1)?.trim()?.removeSuffix(".") ?: continue
                if (server.isNotBlank() && !server.equals(currentServer, ignoreCase = true)) {
                    return server
                }
            }
        }
        return null
    }

    companion object {
        private const val WHOIS_PORT = 43
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_REFERRALS = 3

        private val DOMAIN_PATTERN = Pattern.compile(
            "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$"
        )

        private val REFERRAL_PATTERNS = listOf(
            Pattern.compile("(?i)Registrar WHOIS Server:\\s*(\\S+)"),
            Pattern.compile("(?i)Whois Server:\\s*(\\S+)"),
            Pattern.compile("(?i)refer:\\s*(\\S+)"),
        )

        private val WHOIS_SERVERS = mapOf(
            "com" to "whois.verisign-grs.com",
            "net" to "whois.verisign-grs.com",
            "org" to "whois.pir.org",
            "info" to "whois.afilias.net",
            "io" to "whois.nic.io",
            "ir" to "whois.nic.ir",
            "co" to "whois.nic.co",
            "uk" to "whois.nic.uk",
            "de" to "whois.denic.de",
            "fr" to "whois.nic.fr",
            "us" to "whois.nic.us",
            "biz" to "whois.nic.biz",
            "me" to "whois.nic.me",
            "app" to "whois.nic.google",
            "dev" to "whois.nic.google",
            "xyz" to "whois.nic.xyz",
            "online" to "whois.nic.online",
            "site" to "whois.nic.site",
        )
    }
}
