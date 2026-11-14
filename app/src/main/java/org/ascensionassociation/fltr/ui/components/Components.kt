package org.ascensionassociation.fltr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ascensionassociation.fltr.R
import org.ascensionassociation.fltr.ui.theme.FltrTheme

/** The official astronaut. */
@Composable
fun Astronaut(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.fltr_astronaut),
        contentDescription = null,
        modifier = modifier,
    )
}

/**
 * A card in the style of the artwork: flat fill with a bold outline.
 */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(2.dp, FltrTheme.colors.stroke)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

/**
 * Ring showing how much of today's time is left, like a fuel gauge.
 */
@Composable
fun TimeRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.secondary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val px = stroke.toPx()
            val inset = px / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - px, this.size.height - px)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(px))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(px, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

private val AvatarColors = listOf(
    Color(0xFFFFD9E4), Color(0xFFD6EFFC), Color(0xFFE5F6D3), Color(0xFFFFEBC2),
    Color(0xFFEADCF3), Color(0xFFFFE2E0), Color(0xFFD5F2EC),
)

/** A round initial badge with a pastel colour picked from the name. */
@Composable
fun PersonAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val bg = AvatarColors[(name.lowercase().hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, FltrTheme.colors.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = Color(0xFF371A45),
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/**
 * A centred illustration with a message and up to two actions: empty lists,
 * "connect to your home Wi-Fi", and so on.
 */
@Composable
fun Illustrated(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Astronaut(Modifier.widthIn(max = 220.dp).fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (primary != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = primary.second, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(primary.first)
            }
        }
        if (secondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = secondary.second, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(secondary.first)
            }
        }
    }
}
