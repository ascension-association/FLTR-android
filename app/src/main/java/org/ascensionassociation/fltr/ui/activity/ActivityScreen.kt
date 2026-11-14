package org.ascensionassociation.fltr.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import org.ascensionassociation.fltr.data.BlockEntry
import org.ascensionassociation.fltr.data.parseTime
import org.ascensionassociation.fltr.ui.HomeState
import org.ascensionassociation.fltr.ui.components.Illustrated
import org.ascensionassociation.fltr.ui.components.ProblemBanner
import org.ascensionassociation.fltr.ui.components.SectionTitle
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.theme.FltrTheme
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatClock
import org.ascensionassociation.fltr.util.formatDay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    state: HomeState,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPairAgain: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val shown = state.blocks.filter { filter == null || it.user == filter }
    val byDay = shown.groupBy { parseTime(it.time)?.atZone(zone)?.toLocalDate() ?: today }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.problem?.let { problem ->
                item { ProblemBanner(problem, onRetry = onRefresh, onPairAgain = onPairAgain) }
            }
            if (state.users.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Everyone") }) }
                        items(state.users, key = { it.name }) { u ->
                            FilterChip(
                                selected = filter == u.name,
                                onClick = { filter = if (filter == u.name) null else u.name },
                                label = { Text(displayName(u.name)) },
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Illustrated(
                        title = "All quiet",
                        body = "When FLTR blocks something, you'll see it here: what, when, and why.",
                    )
                }
            }
            byDay.forEach { (day, entries) ->
                item(key = "day-$day") { SectionTitle(formatDay(day, today)) }
                item(key = "list-$day") {
                    StickerCard(Modifier.fillMaxWidth()) {
                        entries.forEach { BlockRow(it, showUser = filter == null) }
                    }
                }
            }
        }
    }
}

/** One blocked connection: who, what, why and when. */
@Composable
fun BlockRow(entry: BlockEntry, showUser: Boolean) {
    val extra = FltrTheme.colors
    val (icon, tint, why) = when (entry.reason) {
        BlockEntry.REASON_ALLOWANCE -> Triple(Icons.Rounded.HourglassBottom, extra.warn, "Out of time")
        BlockEntry.REASON_PAUSED -> Triple(Icons.Rounded.PauseCircle, MaterialTheme.colorScheme.tertiary, "Paused")
        else -> Triple(Icons.Rounded.Block, MaterialTheme.colorScheme.tertiary, "Always blocked")
    }
    val who = entry.user?.let(::displayName) ?: entry.clientLabel ?: entry.client
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.small, color = tint.copy(alpha = 0.14f), modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = why, tint = tint, modifier = Modifier.padding(7.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (showUser) "$who · ${entry.app}" else entry.app,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(why, entry.host, entry.clientLabel.takeIf { showUser && entry.user != null })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        parseTime(entry.time)?.let {
            Text(formatClock(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
