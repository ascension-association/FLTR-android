package org.ascensionassociation.fltr.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import org.ascensionassociation.fltr.data.FltrStatus
import org.ascensionassociation.fltr.data.FltrUser
import org.ascensionassociation.fltr.data.parseTime
import org.ascensionassociation.fltr.ui.HomeState
import org.ascensionassociation.fltr.ui.components.Illustrated
import org.ascensionassociation.fltr.ui.components.PersonAvatar
import org.ascensionassociation.fltr.ui.components.Pill
import org.ascensionassociation.fltr.ui.components.ProblemBanner
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.components.TimeRing
import org.ascensionassociation.fltr.ui.theme.FltrTheme
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatClock
import org.ascensionassociation.fltr.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    state: HomeState,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onPairAgain: () -> Unit,
    onOpenUser: (String) -> Unit,
    onAddPerson: () -> Unit,
    onPause: (FltrUser, Boolean) -> Unit,
    onExtra: (FltrUser, Long) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.problem?.let { problem ->
                item { ProblemBanner(problem, onRetry = onRefresh, onPairAgain = onPairAgain) }
            }
            state.status?.let { status ->
                item { ProtectionCard(status) }
            }
            if (state.loaded && state.users.isEmpty()) {
                item {
                    Illustrated(
                        title = "Who's in your family?",
                        body = "Add each person and their phones, tablets and laptops. Their devices then share one daily allowance.",
                        primary = "Add a person" to onAddPerson,
                    )
                }
            }
            items(state.users, key = { it.name }) { user ->
                UserCard(
                    user = user,
                    onClick = { onOpenUser(user.name) },
                    onPause = { onPause(user, !user.paused) },
                    onExtra = { onExtra(user, 15 * 60L) },
                )
            }
        }
    }
}

@Composable
private fun ProtectionCard(status: FltrStatus) {
    val extra = FltrTheme.colors
    val reset = parseTime(status.nextReset)
    StickerCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (status.failClosed) Icons.Rounded.Shield else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (status.failClosed) extra.good else extra.warn,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Your home is protected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Managing " + status.protocols.joinToString(", ").ifEmpty { "nothing yet" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (reset != null) {
            Spacer(Modifier.height(12.dp))
            val left = Duration.between(Instant.now(), reset).seconds.coerceAtLeast(0)
            Pill(
                "Daily time resets in ${formatDuration(left)} · ${formatClock(reset)}",
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                content = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun UserCard(user: FltrUser, onClick: () -> Unit, onPause: () -> Unit, onExtra: () -> Unit) {
    val extra = FltrTheme.colors
    StickerCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeRing(
                fraction = user.remainingFraction,
                size = 64.dp,
                color = when {
                    user.paused || user.remainingSeconds == 0L -> MaterialTheme.colorScheme.tertiary
                    user.remainingFraction < 0.2f -> extra.warn
                    else -> MaterialTheme.colorScheme.secondary
                },
            ) {
                PersonAvatar(user.name, size = 42.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName(user.name), style = MaterialTheme.typography.titleLarge)
                Text(
                    statusLine(user),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (user.paused) {
                        Pill(
                            "Paused",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Icons.Rounded.Pause,
                        )
                    }
                    Pill(devicesLabel(user.devices.size))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(
                    onClick = onPause,
                    colors = if (user.paused) IconButtonDefaults.filledTonalIconButtonColors()
                    else IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Icon(
                        if (user.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (user.paused) "Resume ${user.name}" else "Pause ${user.name}",
                    )
                }
                OutlinedButton(
                    onClick = onExtra,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("15m", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun statusLine(user: FltrUser): String = when {
    user.paused -> "Paused until the daily reset"
    user.alwaysBlocked -> "Always blocked"
    user.remainingSeconds == 0L -> "Time's up for today"
    else -> "${formatDuration(user.remainingSeconds)} left of ${formatDuration(user.totalSeconds)}"
}

internal fun devicesLabel(n: Int) = when (n) {
    0 -> "No devices"
    1 -> "1 device"
    else -> "$n devices"
}
