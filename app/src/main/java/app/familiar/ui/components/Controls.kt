package app.familiar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.familiar.R
import app.familiar.ui.theme.U
import app.familiar.ui.theme.UType

/**
 * A length of time as hours and minutes, the way players and shops show it. [minutes] is null
 * while both are empty; more than 59 minutes is fine, they just add up.
 */
@Composable
fun HoursMinutesField(minutes: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    fun total(h: String, m: String) = if (h.isEmpty() && m.isEmpty()) null else (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0)
    var h by remember { mutableStateOf(minutes?.let { (it / 60).toString() } ?: "") }
    var m by remember { mutableStateOf(minutes?.let { (it % 60).toString() } ?: "") }
    // Set from outside (by a percent, a found book): show the new time.
    LaunchedEffect(minutes) {
        if (minutes != total(h, m)) {
            h = minutes?.let { (it / 60).toString() } ?: ""
            m = minutes?.let { (it % 60).toString() } ?: ""
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UTextField(h, { h = it.filter(Char::isDigit).take(3); onChange(total(h, m)) }, Modifier.weight(1f), placeholder = "0", number = true, suffix = stringResource(R.string.unit_h))
        UTextField(m, { m = it.filter(Char::isDigit).take(4); onChange(total(h, m)) }, Modifier.weight(1f), placeholder = "0", number = true, suffix = stringResource(R.string.unit_min))
    }
}

/** − value +; [onStep] gets −1 or +1 for a tap, and ±[holdStep] again and again while a button is held. */
@Composable
fun Stepper(label: String, value: String, onStep: (Int) -> Unit, modifier: Modifier = Modifier, color: Color = U.c.accent, holdStep: Int = 10) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = UType.Body, modifier = Modifier.weight(1f))
        RoundIcon(Icons.Rounded.Remove, stringResource(R.string.step_less), color) { long -> onStep(if (long) -holdStep else -1) }
        Text(value, style = UType.Number.copy(textAlign = TextAlign.Center), modifier = Modifier.width(96.dp))
        RoundIcon(Icons.Rounded.Add, stringResource(R.string.step_more), color) { long -> onStep(if (long) holdStep else 1) }
    }
}

/** A round button that answers a tap with [onStep] (false), and a hold with [onStep] (true) every moment until let go. */
@Composable
private fun RoundIcon(icon: ImageVector, label: String, color: Color, onStep: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = if (pressed) 0.3f else 0.14f))
            .semantics { role = Role.Button; contentDescription = label; onClick { step(false); true } }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    var held = false
                    val repeat = scope.launch {
                        delay(HOLD_MS)
                        held = true
                        while (true) {
                            step(true)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            delay(REPEAT_MS)
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeat.cancel()
                    pressed = false
                    if (up != null && !held) step(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
}

private const val HOLD_MS = 400L
private const val REPEAT_MS = 220L

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, color: Color = U.c.accent) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Body)
            if (sub != null) Text(sub, style = UType.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = U.c.surface, uncheckedTrackColor = U.c.surface2, uncheckedBorderColor = U.c.line),
        )
    }
}

/** A choice among a few, drawn as a card with a tick. */
@Composable
fun SelectCard(
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(U.RMd)
            .background(if (selected) color.copy(alpha = 0.12f) else U.c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color.copy(alpha = 0.7f) else U.c.line, U.RMd)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) { content() }
        trailing()
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** A small number with a caption, for stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = U.c.ink) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = UType.Number.copy(color = color), maxLines = 1)
        Text(label, style = UType.Small.copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}
