package com.leekleak.trafficlight.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Main screens
 */
@Serializable
data object Blank : NavKey
@Serializable
data object Overview : NavKey
@Serializable
data object History : NavKey
@Serializable
data object Settings : NavKey
@Serializable
data object UsagePermissionRequest : NavKey

val mainScreens = listOf(Blank, Overview, History)

/**
 * Settings
 */
@Serializable
data class PlanConfig(val subscriberId: String) : NavKey

/**
 * Settings
 */
@Serializable
data object NotificationSettings : NavKey

@Serializable
data object NetworkUtils : NavKey

@Serializable
data object PingTool : NavKey

@Serializable
data object WhoisTool : NavKey

@Serializable
data object IpLookupTool : NavKey

@Serializable
data object MyNetworkTool : NavKey

@Serializable
data object TracerouteTool : NavKey

@Serializable
data object SshTool : NavKey
