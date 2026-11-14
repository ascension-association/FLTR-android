package org.ascensionassociation.fltr.ui.devices

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ascensionassociation.fltr.data.DeviceBody
import org.ascensionassociation.fltr.data.FltrUser
import org.ascensionassociation.fltr.data.SeenDevice
import org.ascensionassociation.fltr.data.parseTime
import org.ascensionassociation.fltr.ui.HomeState
import org.ascensionassociation.fltr.ui.components.Illustrated
import org.ascensionassociation.fltr.ui.components.PersonAvatar
import org.ascensionassociation.fltr.ui.components.Pill
import org.ascensionassociation.fltr.ui.components.ProblemBanner
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.sheets.toBody
import org.ascensionassociation.fltr.util.displayName
import org.ascensionassociation.fltr.util.formatRelative

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    state: HomeState,
    contentPadding: PaddingValues,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPairAgain: () -> Unit,
    onAssign: (DeviceBody, String) -> Unit,
    onRename: (SeenDevice, String) -> Unit,
    onAddPerson: (DeviceBody?) -> Unit,
) {
    var assigning by remember { mutableStateOf<SeenDevice?>(null) }
    var renaming by remember { mutableStateOf<SeenDevice?>(null) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.problem?.let { problem ->
                item { ProblemBanner(problem, onRetry = onRefresh, onPairAgain = onPairAgain) }
            }
            item {
                Text(
                    "Devices FLTR has seen since it last started. Tap one to name it or choose who it belongs to.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (state.devices.isEmpty()) {
                item {
                    Illustrated(
                        title = "No devices yet",
                        body = "Devices show up here once they use the internet through FLTR. Pull down to check again.",
                    )
                }
            }
            items(state.devices, key = { it.ip + (it.mac ?: "") }) { device ->
                DeviceCard(device, onClick = { assigning = device })
            }
        }
    }

    assigning?.let { device ->
        AssignSheet(
            device = device,
            users = state.users,
            onAssign = { body, name -> assigning = null; onAssign(body, name) },
            onAddPerson = { body -> assigning = null; onAddPerson(body) },
            onRename = { assigning = null; renaming = device },
            onDismiss = { assigning = null },
        )
    }

    renaming?.let { device ->
        RenameDialog(
            device = device,
            onSave = { name -> renaming = null; onRename(device, name) },
            onDismiss = { renaming = null },
        )
    }
}

/** Longest name FLTR accepts. */
private const val MAX_NAME = 64

@Composable
private fun RenameDialog(device: SeenDevice, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(device.name.orEmpty()) }
    val canSave = text.isNotBlank() && text.trim() != device.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (device.name == null) "Name this device" else "Rename device") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_NAME) text = it.filterNot(Char::isISOControl) },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Alice's iPad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    (if (device.mac != null) "Saved on FLTR for MAC ${device.mac}" else "Saved on FLTR for IP ${device.ip}") +
                        ", so every paired phone sees it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            if (device.name != null) {
                TextButton(onClick = { onSave("") }) { Text("Remove name") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun DeviceCard(device: SeenDevice, onClick: () -> Unit) {
    StickerCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Rounded.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.title ?: "Unknown device",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // With a name as the title, still show the software it runs.
                    listOfNotNull(device.clientLabel.takeIf { device.name != null }, device.ip, device.mac)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val detail = listOfNotNull(
                    device.host,
                    parseTime(device.lastSeen)?.let { formatRelative(it) },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            if (device.user != null) {
                PersonAvatar(device.user, size = 36.dp)
            } else {
                Pill("Unassigned")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSheet(
    device: SeenDevice,
    users: List<FltrUser>,
    onAssign: (DeviceBody, String) -> Unit,
    onAddPerson: (DeviceBody) -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    var byMac by remember { mutableStateOf(device.mac != null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Whose device is this?", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.title ?: device.ip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (device.name == null) "Name it" else "Rename")
                }
            }
            if (device.mac != null) {
                Spacer(Modifier.height(16.dp))
                Text("Recognize it by", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(byMac, { byMac = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("MAC address") }
                    SegmentedButton(!byMac, { byMac = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("IP address") }
                }
                Text(
                    if (byMac) "Best for most devices. Turn off \"private Wi-Fi address\" on the phone so it stays the same."
                    else "Use this if the device changes its MAC address. Give it a fixed IP in your router.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            users.forEach { user ->
                ListItem(
                    headlineContent = { Text(displayName(user.name)) },
                    leadingContent = { PersonAvatar(user.name, size = 36.dp) },
                    trailingContent = { if (device.user == user.name) Pill("Current") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .then(
                            if (device.user == user.name) Modifier
                            else Modifier.clickable { onAssign(device.toBody(preferMac = byMac), user.name) }
                        ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAddPerson(device.toBody(preferMac = byMac)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Someone new")
            }
        }
    }
}
