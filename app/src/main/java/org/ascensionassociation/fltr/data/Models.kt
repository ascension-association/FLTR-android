package org.ascensionassociation.fltr.data

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire types for the FLTR app API v1. Field names follow docs/API.md.
// Times are kept as strings and parsed with [parseTime]: Go writes them in
// RFC 3339 with either "Z" or a local offset (next_reset uses the box's zone).

@Serializable
data class FltrStatus(
    val mode: String = "",
    @SerialName("fail_closed") val failClosed: Boolean = true,
    val protocols: List<String> = emptyList(),
    val started: String? = null,
    val time: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long = 0,
    @SerialName("next_reset") val nextReset: String? = null,
    val rules: Map<String, Int> = emptyMap(),
    val users: Int = 0,
)

@Serializable
data class FltrUser(
    val name: String,
    @SerialName("allowance_seconds") val allowanceSeconds: Long = 0,
    @SerialName("used_seconds") val usedSeconds: Long = 0,
    @SerialName("extra_seconds") val extraSeconds: Long = 0,
    @SerialName("remaining_seconds") val remainingSeconds: Long = 0,
    val paused: Boolean = false,
    val managed: Boolean = false,
    val devices: List<UserDevice> = emptyList(),
) {
    /** Today's total: the daily allowance plus any extra time granted. */
    val totalSeconds: Long get() = allowanceSeconds + extraSeconds

    /** Share of today's time still left, 0..1. */
    val remainingFraction: Float
        get() = if (totalSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)

    /** True when the protocols are blocked outright: no allowance and no extra. */
    val alwaysBlocked: Boolean get() = allowanceSeconds == 0L && extraSeconds == 0L

    val settingsDevices: List<UserDevice> get() = devices.filter { it.editable }
}

@Serializable
data class UserDevice(
    val type: String,
    val value: String,
    val source: String = SOURCE_SETTINGS,
    /** The name given to this device in the app, if any. */
    val name: String? = null,
) {
    /** Devices from the settings file can be changed by the app; rules.txt ones can't. */
    val editable: Boolean get() = source == SOURCE_SETTINGS

    fun toBody() = DeviceBody(type, value)

    companion object {
        const val SOURCE_SETTINGS = "settings"
        const val SOURCE_RULES = "rules"
    }
}

@Serializable
data class SeenDevice(
    val ip: String,
    val mac: String? = null,
    @SerialName("client_label") val clientLabel: String? = null,
    val ja4: String? = null,
    val host: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val user: String? = null,
    val rule: String? = null,
    /** The name given to this device in the app (by MAC, or else IP), if any. */
    val name: String? = null,
) {
    /** What to call it: its given name, else the software FLTR recognized. */
    val title: String? get() = name?.takeIf { it.isNotBlank() } ?: clientLabel
}

@Serializable
data class BlockRule(val type: String = "", val value: String = "")

@Serializable
data class BlockEntry(
    val time: String,
    val proto: String = "",
    val client: String = "",
    @SerialName("client_port") val clientPort: Int? = null,
    val server: String = "",
    @SerialName("server_port") val serverPort: Int? = null,
    val mac: String? = null,
    @SerialName("class") val classification: String = "",
    val host: String? = null,
    val ja4: String? = null,
    @SerialName("client_label") val clientLabel: String? = null,
    val rule: BlockRule? = null,
    val user: String? = null,
    val reason: String = REASON_RULE,
    val allowance: String? = null,
    val mode: String? = null,
) {
    /** The protocol nDPI saw, e.g. "YouTube" from "TLS.YouTube". */
    val app: String get() = classification.substringAfterLast('.').ifEmpty { classification }

    companion object {
        const val REASON_RULE = "rule"
        const val REASON_ALLOWANCE = "allowance_exhausted"
        const val REASON_PAUSED = "paused"
    }
}

// Request bodies. FLTR rejects unknown fields, so these carry only what the
// API documents; nulls are omitted (Json.explicitNulls = false).

@Serializable
data class PutUserBody(
    @SerialName("allowance_seconds") val allowanceSeconds: Long? = null,
    val devices: List<DeviceBody>? = null,
)

@Serializable
data class DeviceBody(val type: String, val value: String)

@Serializable
data class ExtraBody(val seconds: Long)

/** PUT /names: a device's name; an empty [name] removes it. */
@Serializable
data class NameBody(val type: String, val value: String, val name: String)

@Serializable
data class ErrorBody(val error: String = "")

fun parseTime(value: String?): Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
