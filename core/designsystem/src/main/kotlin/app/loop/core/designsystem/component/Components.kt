package app.loop.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.designsystem.theme.LoopTheme

/**
 * White (or raised, in dark mode) rounded card. No shadow: a hairline edge keeps it crisp against
 * the page, which is only one step darker in light mode.
 */
@Composable
fun LoopCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * The action line at the foot of a tappable card: accent text and an arrow, aligned with the
 * card's text. The whole card is the touch target, so this isn't a separate button.
 */
@Composable
fun CardAction(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(LoopIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/**
 * A text-only button. With [alignToText] its label lines up with the text above it (inside a
 * card) instead of sitting indented by the button's own padding; the touch area is unchanged.
 */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, alignToText: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).then(if (alignToText) Modifier.offset(x = (-12).dp) else Modifier),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Screen title row with an optional back arrow. */
@Composable
fun LoopTopBar(title: String, onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = if (onBack == null) LoopTheme.spacing.gutter else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(LoopIcons.Back, contentDescription = "Back") }
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = LoopTheme.spacing.gutter, vertical = LoopTheme.spacing.sm)
            .semantics { heading() },
    )
}

/** A tappable settings-style row: icon, title, optional detail, and a trailing slot (chevron by default). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {
        if (onClick != null) {
            Icon(LoopIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = LoopTheme.spacing.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        detail = detail,
        modifier = modifier,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

enum class PillTone { POSITIVE, NEUTRAL, ATTENTION }

@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        PillTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        PillTone.ATTENTION -> LoopTheme.areas.screen.copy(alpha = 0.18f) to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = modifier
            .background(bg, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Small colour dot that identifies an area. */
@Composable
fun AreaDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).background(color, CircleShape))
}

/** Friendly empty state: mark, one line of title, one line of explanation. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit = { LoopMark(size = 56.dp) },
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(LoopTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        art()
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
