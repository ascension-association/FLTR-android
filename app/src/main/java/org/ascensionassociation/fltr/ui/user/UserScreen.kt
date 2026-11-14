package org.ascensionassociation.fltr.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.ascensionassociation.fltr.data.BlockEntry
import org.ascensionassociation.fltr.data.DeviceBody
import org.ascensionassociation.fltr.data.FltrUser
import org.ascensionassociation.fltr.data.SeenDevice
import org.ascensionassociation.fltr.data.UserDevice
import org.ascensionassociation.fltr.ui.HomeState
import org.ascensionassociation.fltr.ui.activity.BlockRow
import org.ascensionassociation.fltr.ui.components.Illustrated
import org.ascensionassociation.fltr.ui.components.PersonAvatar
import org.ascensionassociation.fltr.ui.components.Pill
import org.ascensionassociation.fltr.ui.components.SectionTitle
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.components.TimeRing
import org.ascensionassociation.fltr.ui.family.statusLine
import org.ascensionassociation.fltr.ui.matches
import org.ascensionassociation.fltr.ui.sheets.AddDeviceSheet
import org.ascensionassociation.fltr.ui.sheets.AllowanceSheet
import org.ascensionassociation.fltr.ui.theme.FltrTheme
import org.ascensionassociation.fltr.util.displayDeviceValue
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatDuration
import org.ascensionassociation.fltr.util.isIpRange

private val EXTRA_CHOICES = listOf(15L, 30L, 60L, 120L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    name: String,
    state: HomeState,
    onBack: () -> Unit,
    onPause: (FltrUser, Boolean) -> Unit,
    onExtra: (FltrUser, Long) -> Unit,
    onSetAllowance: (FltrUser, Long?) -> Unit,
    onAddDevices: (FltrUser, List<DeviceBody>) -> Unit,
    onRemoveDevice: (FltrUser, DeviceBody) -> Unit,
    onDelete: (FltrUser) -> Unit,
    onLoadDevices: () -> Unit,
) {
    val user = state.user(name)
    var menu by rememberSaveable { mutableStateOf(false) }
    var editAllowance by rememberSaveable { mutableStateOf(false) }
    var addDevice by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName(name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (user?.managed == true) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove ${displayName(name)}") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (user == null) {
            Illustrated(
                title = "Not found",
                body = "${displayName(name)} isn't on your FLTR box any more.",
                primary = "Back" to onBack,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TodayCard(user) }
            item { PauseCard(user, onToggle = { onPause(user, !user.paused) }) }
            item { ExtraTimeCard(user, onExtra = { onExtra(user, it) }) }
            item { AllowanceCard(user, onEdit = { editAllowance = true }) }
            item {
                DevicesCard(
                    user = user,
                    seen = state.devices,
                    onAdd = { onLoadDevices(); addDevice = true },
                    onRemove = { onRemoveDevice(user, it) },
                )
            }
            item { RecentBlocks(state.blocks.filter { it.user == user.name }.take(8)) }
        }

        if (editAllowance) {
            AllowanceSheet(
                name = user.name,
                current = user.allowanceSeconds,
                canUseRulesDefault = user.managed,
                onSave = { editAllowance = false; onSetAllowance(user, it) },
                onDismiss = { editAllowance = false },
            )
        }
        if (addDevice) {
            val mine = user.devices.map { it.toBody() }
            AddDeviceSheet(
                personName = user.name,
                seen = state.devices.filter { d -> mine.none { it.matches(DeviceBody("mac", d.mac ?: "")) || it.matches(DeviceBody("ip", d.ip)) } },
                onAdd = { addDevice = false; onAddDevices(user, it) },
                onDismiss = { addDevice = false },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Remove ${displayName(name)}?") },
                text = {
                    Text(
                        if (user.devices.any { !it.editable })
                            "This removes what you set in the app. ${displayName(name)} stays because rules.txt lists them."
                        else "Their devices will follow FLTR's default rules again."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { confirmDelete = false; onDelete(user) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun TodayCard(user: FltrUser) {
    val extra = FltrTheme.colors
    StickerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            TimeRing(
                fraction = user.remainingFraction,
                size = 180.dp,
                stroke = 16.dp,
                color = when {
                    user.paused || user.remainingSeconds == 0L -> MaterialTheme.colorScheme.tertiary
                    user.remainingFraction < 0.2f -> extra.warn
                    else -> MaterialTheme.colorScheme.secondary
                },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PersonAvatar(user.name, size = 44.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            user.paused -> "Paused"
                            else -> formatDuration(user.remainingSeconds)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    if (!user.paused) Text("left today", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(statusLine(user), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Used ${formatDuration(user.usedSeconds)}")
                if (user.extraSeconds > 0) {
                    Pill(
                        "+${formatDuration(user.extraSeconds)} extra",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseCard(user: FltrUser, onToggle: () -> Unit) {
    StickerCard(
        Modifier.fillMaxWidth(),
        color = if (user.paused) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (user.paused) "Paused" else "Pause", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (user.paused) "No time until you resume or the daily reset."
                    else "Stop their time right now, until the daily reset.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onToggle,
                colors = if (user.paused) ButtonDefaults.buttonColors()
                else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Icon(if (user.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (user.paused) "Resume" else "Pause")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraTimeCard(user: FltrUser, onExtra: (Long) -> Unit) {
    StickerCard(Modifier.fillMaxWidth()) {
        Text("Extra time today", style = MaterialTheme.typography.titleMedium)
        Text(
            "A reward or a little more time to finish something. It ends at the daily reset.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EXTRA_CHOICES.forEach { minutes ->
                AssistChip(
                    onClick = { onExtra(minutes * 60) },
                    label = { Text("+${formatDuration(minutes * 60)}") },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (user.extraSeconds > 0) {
                val back = minOf(user.extraSeconds, 15 * 60L)
                AssistChip(
                    onClick = { onExtra(-back) },
                    label = { Text("Take back ${formatDuration(back)}") },
                    leadingIcon = { Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun AllowanceCard(user: FltrUser, onEdit: () -> Unit) {
    StickerCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily time", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (user.allowanceSeconds == 0L) "Always blocked" else "${formatDuration(user.allowanceSeconds)} every day",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(Icons.Rounded.Edit, contentDescription = "Change daily time")
        }
    }
}

@Composable
private fun DevicesCard(user: FltrUser, seen: List<SeenDevice>, onAdd: () -> Unit, onRemove: (DeviceBody) -> Unit) {
    StickerCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Add")
            }
        }
        if (user.devices.isEmpty()) {
            Text(
                "No devices yet. Add the phones, tablets and computers ${displayName(user.name)} uses.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        user.devices.forEach { device -> DeviceRow(device, seen, onRemove) }
    }
}

@Composable
private fun DeviceRow(device: UserDevice, seen: List<SeenDevice>, onRemove: (DeviceBody) -> Unit) {
    val body = device.toBody()
    val match = seen.firstOrNull { s ->
        (s.mac != null && body.matches(DeviceBody("mac", s.mac))) || body.matches(DeviceBody("ip", s.ip))
    }
    val range = isIpRange(device.type, device.value)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (range) Icons.Rounded.Hub else Icons.Rounded.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                match?.clientLabel ?: if (range) "A range of addresses" else displayDeviceValue(device.type, device.value),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${device.type.uppercase()} ${displayDeviceValue(device.type, device.value)}" +
                    if (device.editable) "" else " · from rules.txt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (device.editable) {
            IconButton(onClick = { onRemove(body) }) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${device.value}")
            }
        } else {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "Set in rules.txt; change it there",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun RecentBlocks(blocks: List<BlockEntry>) {
    if (blocks.isEmpty()) return
    Column {
        SectionTitle("Recently blocked")
        StickerCard(Modifier.fillMaxWidth()) {
            blocks.forEach { BlockRow(it, showUser = false) }
        }
    }
}
