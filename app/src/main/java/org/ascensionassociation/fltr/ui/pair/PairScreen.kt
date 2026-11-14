package org.ascensionassociation.fltr.ui.pair

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.ascensionassociation.fltr.ui.PairState
import org.ascensionassociation.fltr.ui.components.Astronaut
import org.ascensionassociation.fltr.ui.components.StickerCard
import org.ascensionassociation.fltr.ui.theme.Brand

@Composable
fun PairScreen(
    state: PairState,
    onCode: (String) -> Unit,
    onConfirm: (org.ascensionassociation.fltr.data.Pairing) -> Unit,
    onSaveAnyway: (org.ascensionassociation.fltr.data.Pairing) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPaste by rememberSaveable { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onCode)
    }
    val scan = {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Point at the QR code from FLTR's log")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Surface(shape = CircleShape, color = Brand.SkySoft, modifier = Modifier.size(240.dp)) {
                Astronaut(Modifier.padding(28.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to FLTR",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Healthy screen time for the whole family. Let's connect this phone to your FLTR box.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(24.dp))

            StickerCard(Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
                Step(1, "Join your home Wi-Fi", "FLTR can only be managed from inside your home.")
                Step(2, "Show the pairing code", "Run “ramekin logs fltr” on your computer. The QR code is at the top.")
                Step(3, "Scan it", "Keep the code private. Anyone who has it can change FLTR's settings.")
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = scan, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).height(56.dp)) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Scan pairing code")
            }
            TextButton(onClick = { showPaste = true }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Paste a pairing link instead")
            }
        }
    }

    if (showPaste) {
        PasteDialog(onDismiss = { showPaste = false }, onSubmit = { showPaste = false; onCode(it) })
    }

    when (state) {
        PairState.Idle -> Unit
        PairState.Checking -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            title = { Text("Connecting…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Saying hello to your FLTR box.")
                }
            },
        )
        is PairState.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pair with this FLTR box?") },
            text = {
                Text(
                    "A link asked to pair this phone with the FLTR box at ${state.pairing.displayHost}. " +
                        "Only continue if you just scanned your own box's code."
                )
            },
            confirmButton = { Button(onClick = { onConfirm(state.pairing) }) { Text("Pair") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        is PairState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't pair") },
            text = { Text(state.message) },
            confirmButton = {
                if (state.pairing != null) {
                    Button(onClick = { onConfirm(state.pairing) }) { Text("Try again") }
                } else {
                    Button(onClick = { onDismiss(); scan() }) { Text("Scan again") }
                }
            },
            dismissButton = {
                if (state.canSaveAnyway && state.pairing != null) {
                    TextButton(onClick = { onSaveAnyway(state.pairing) }) { Text("Save anyway") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            },
        )
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste pairing link") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("gkfltr://pair?…") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Pair") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
