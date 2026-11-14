package org.ascensionassociation.fltr.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "0m", "<1m", "45m", "2h", "1h 30m". */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0m"
    if (seconds < 60) return "<1m"
    val minutes = seconds / 60
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** "just now", "5 min ago", "3 h ago", "yesterday", or a short date. */
fun formatRelative(then: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val ago = Duration.between(then, now)
    return when {
        ago.seconds < 60 -> "just now"
        ago.toMinutes() < 60 -> "${ago.toMinutes()} min ago"
        ago.toHours() < 12 -> "${ago.toHours()} h ago"
        else -> {
            val day = then.atZone(zone).toLocalDate()
            val today = now.atZone(zone).toLocalDate()
            when (day) {
                today -> formatClock(then, zone)
                today.minusDays(1) -> "yesterday"
                else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
        }
    }
}

fun formatClock(t: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    t.atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "Today", "Yesterday", or a full date: section headers in the activity list. */
fun formatDay(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
}

/** "alice" -> "Alice". FLTR user names are ASCII identifiers. */
fun displayName(name: String): String = name.replaceFirstChar { it.titlecase() }

/** FLTR accepts user names of letters, digits, '_', '.' and '-'. */
private val USER_NAME = Regex("[A-Za-z0-9_.-]{1,32}")

fun isValidUserName(name: String): Boolean = USER_NAME.matches(name)

/** "192.168.1.23/32" -> "192.168.1.23"; ranges keep their prefix. */
fun displayDeviceValue(type: String, value: String): String =
    if (type == "ip") value.removeSuffix("/32").removeSuffix("/128") else value

/** True for an IP rule that covers a range rather than one device. */
fun isIpRange(type: String, value: String): Boolean =
    type == "ip" && value.contains('/') && !value.endsWith("/32") && !value.endsWith("/128")
