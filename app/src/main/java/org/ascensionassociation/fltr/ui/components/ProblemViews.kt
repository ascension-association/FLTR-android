package org.ascensionassociation.fltr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.ascensionassociation.fltr.ui.Problem

private fun Problem.title(): String = when (this) {
    is Problem.OffHomeNetwork -> if (onLocalNetwork) "Can't reach FLTR" else "Connect to your home Wi-Fi"
    Problem.Unpaired -> "This phone was unpaired"
    Problem.WrongBox -> "That's not your FLTR box"
    is Problem.RateLimited -> "Too many tries"
    is Problem.Other -> "Something went wrong"
}

private fun Problem.body(): String = when (this) {
    is Problem.OffHomeNetwork ->
        if (onLocalNetwork) "FLTR only answers on your home network. Check that this phone is on your home Wi-Fi " +
            "(not a guest network) and that the FLTR box is plugged in."
        else "For safety, FLTR can only be managed from your home network. Join your home Wi-Fi and we'll reconnect."
    Problem.Unpaired -> "Someone reset FLTR's pairing. Show a new code with “ramekin restart fltr”, then scan it."
    Problem.WrongBox -> "A different device now answers at your FLTR box's address, so we stopped to keep your " +
        "settings safe. If you reinstalled FLTR, pair again."
    is Problem.RateLimited -> "FLTR is pausing logins from this phone" +
        (seconds?.let { " for $it seconds" } ?: "") + ". Try again shortly."
    is Problem.Other -> message
}

/** Full-screen problem, for when there is nothing loaded to show underneath. */
@Composable
fun ProblemState(problem: Problem, onRetry: () -> Unit, onPairAgain: () -> Unit, modifier: Modifier = Modifier) {
    val needsPairing = problem is Problem.Unpaired || problem is Problem.WrongBox
    Illustrated(
        title = problem.title(),
        body = problem.body(),
        modifier = modifier,
        primary = if (needsPairing) "Pair again" to onPairAgain else "Try again" to onRetry,
        secondary = if (needsPairing) "Try again" to onRetry else null,
    )
}

/** Slim banner above stale data. */
@Composable
fun ProblemBanner(problem: Problem, onRetry: () -> Unit, onPairAgain: () -> Unit, modifier: Modifier = Modifier) {
    val needsPairing = problem is Problem.Unpaired || problem is Problem.WrongBox
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (needsPairing) Icons.Rounded.LinkOff else Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(problem.title(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = if (needsPairing) onPairAgain else onRetry) {
                Text(if (needsPairing) "Pair again" else "Retry")
            }
        }
    }
}
