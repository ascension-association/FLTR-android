package org.ascensionassociation.fltr.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import org.ascensionassociation.fltr.data.DeviceBody
import org.ascensionassociation.fltr.data.SeenDevice
import org.ascensionassociation.fltr.data.parseTime
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatDuration
import org.ascensionassociation.fltr.util.formatRelative
import org.ascensionassociation.fltr.util.isValidUserName

private const val STEP_MINUTES = 15
private const val MAX_MINUTES = 24 * 60
private val PRESETS = listOf(0L, 30L, 60L, 90L, 120L, 180L)

/** Picks a daily allowance in 15-minute steps, 0 to 24 h. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllowancePicker(seconds: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            if (seconds == 0L) "Always blocked" else "${formatDuration(seconds)} a day",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (seconds == 0L) "No time unless you grant extra time."
            else "Shared by all of this person's devices. Only active use counts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = (seconds / 60f).coerceIn(0f, MAX_MINUTES.toFloat()),
            onValueChange = { minutes ->
                onChange(((minutes / STEP_MINUTES).roundToLong() * STEP_MINUTES) * 60)
            },
            // Snaps to 15 minutes in onValueChange; `steps` would draw 95 tick marks.
            valueRange = 0f..MAX_MINUTES.toFloat(),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { minutes ->
                FilterChip(
                    selected = seconds == minutes * 60,
                    onClick = { onChange(minutes * 60) },
                    label = { Text(if (minutes == 0L) "Block" else formatDuration(minutes * 60)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowanceSheet(
    name: String,
    current: Long,
    canUseRulesDefault: Boolean,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableFloatStateOf(current.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("${displayName(name)}'s daily time", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            AllowancePicker(seconds.toLong(), onChange = { seconds = it.toFloat() })
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canUseRulesDefault) {
                    TextButton(onClick = { onSave(null) }) { Text("Use FLTR default") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(seconds.toLong()) }) { Text("Save") }
            }
        }
    }
}

/** A device the person can pick: seen on the network, not yet theirs. */
private fun SeenDevice.title() = clientLabel ?: host?.let { "Last visited $it" } ?: "Unknown device"

/** MAC is steadier than IP on most home networks, unless the phone randomizes it. */
fun SeenDevice.toBody(preferMac: Boolean = true): DeviceBody =
    if (preferMac && mac != null) DeviceBody("mac", mac) else DeviceBody("ip", ip)

@Composable
private fun SeenDeviceRow(device: SeenDevice, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(device.title()) },
        supportingContent = {
            val seen = parseTime(device.lastSeen)?.let { " · seen ${formatRelative(it)}" }.orEmpty()
            val owner = device.user?.let { " · belongs to ${displayName(it)}" }.orEmpty()
            Text(listOfNotNull(device.ip, device.mac).joinToString(" · ") + seen + owner)
        },
        leadingContent = { Icon(Icons.Rounded.Devices, contentDescription = null) },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}

/**
 * Pick devices from what FLTR has seen, or type a MAC or IP address.
 * Shared by "Add device" and "Add person".
 */
@Composable
fun DeviceChooser(
    seen: List<SeenDevice>,
    selected: MutableList<DeviceBody>,
    modifier: Modifier = Modifier,
) {
    var manualType by rememberSaveable { mutableStateOf("mac") }
    var manualValue by rememberSaveable { mutableStateOf("") }
    Column(modifier) {
        if (seen.isEmpty()) {
            Text(
                "FLTR hasn't seen any devices since it started. Use a device for a minute, or type its address below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Seen on your network", style = MaterialTheme.typography.titleSmall)
            // A plain column: the sheet around it scrolls, and nested scrolling hid devices.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                seen.forEach { device ->
                    val body = device.toBody()
                    val checked = selected.any { it == body }
                    SeenDeviceRow(device, checked) {
                        if (checked) selected.removeAll { it == body } else selected.add(body)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Or add by address", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("mac" to "MAC address", "ip" to "IP address").forEachIndexed { i, (type, label) ->
                SegmentedButton(
                    selected = manualType == type,
                    onClick = { manualType = type },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = manualValue,
                onValueChange = { manualValue = it.trim() },
                placeholder = { Text(if (manualType == "mac") "aa:bb:cc:dd:ee:ff" else "192.168.1.23") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (manualType == "ip") KeyboardType.Uri else KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    selected.add(DeviceBody(manualType, manualValue))
                    manualValue = ""
                },
                enabled = manualValue.isNotEmpty(),
            ) { Text("Add") }
        }
        val manual = selected.filter { body -> seen.none { it.toBody() == body } }
        if (manual.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            manual.forEach { body ->
                Text(
                    "• ${body.value} (${body.type.uppercase()})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceSheet(
    personName: String,
    seen: List<SeenDevice>,
    onAdd: (List<DeviceBody>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember { mutableStateListOf<DeviceBody>() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding()
        ) {
            Text("Add a device for ${displayName(personName)}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            DeviceChooser(seen, selected)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAdd(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (selected.size > 1) "Add ${selected.size} devices" else "Add device") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonSheet(
    existingNames: Set<String>,
    seen: List<SeenDevice>,
    onCreate: (name: String, allowanceSeconds: Long, devices: List<DeviceBody>) -> Unit,
    onDismiss: () -> Unit,
    initialDevices: List<DeviceBody> = emptyList(),
) {
    var name by rememberSaveable { mutableStateOf("") }
    var allowance by rememberSaveable { mutableStateOf(60 * 60L) }
    val selected = remember { mutableStateListOf<DeviceBody>().apply { addAll(initialDevices) } }
    val nameError = when {
        name.isEmpty() -> null
        !isValidUserName(name) -> "Use letters, numbers, '.', '_' or '-' (no spaces)"
        name.lowercase() in existingNames -> "There's already someone called that"
        else -> null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.padding(horizontal = 24.dp).imePadding()) {
            item {
                Text("Add a person", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = { Text(nameError ?: "One word, e.g. alice") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                AllowancePicker(allowance, onChange = { allowance = it })
                Spacer(Modifier.height(24.dp))
                Text("Their devices", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DeviceChooser(seen, selected)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onCreate(name, allowance, selected.toList()) },
                    enabled = name.isNotEmpty() && nameError == null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Add ${if (name.isEmpty()) "person" else displayName(name)}") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
