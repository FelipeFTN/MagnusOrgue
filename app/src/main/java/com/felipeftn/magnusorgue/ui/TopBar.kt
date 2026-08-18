package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.felipeftn.magnusorgue.R
import com.felipeftn.magnusorgue.controller.OrganController

/**
 * Stop names, in the exact order of kStops in cpp/Stops.h. If you add a stop
 * there, add it here too, in the same slot.
 * TODO: one source of truth for this list instead of a comment and a prayer.
 */
val STOP_NAMES = listOf("Principal 8'", "Flute 8'", "Strings 8'", "Tutti")

@Composable
fun TopBar(controller: OrganController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StopSelector(controller)
        OctaveSelector(controller)

        // Volume gets whatever width is left over.
        Slider(
            value = controller.volume,
            onValueChange = controller::changeVolume,
            modifier = Modifier.weight(1f).width(120.dp),
        )

        MidiChip(controller.midiDeviceName)

        Button(
            onClick = controller::panic,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.panic))
        }
    }
}

@Composable
private fun StopSelector(controller: OrganController) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(STOP_NAMES[controller.stopIndex], color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            STOP_NAMES.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        controller.selectStop(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OctaveSelector(controller: OrganController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { controller.shiftOctave(-1) }) { Text("‹") }
        // The label shows the lowest visible C, e.g. "C4".
        Text("C${controller.baseOctave}", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { controller.shiftOctave(+1) }) { Text("›") }
    }
}

@Composable
private fun MidiChip(deviceName: String?) {
    val connected = deviceName != null
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        color = if (connected) Color(0xFF4CAF50) else Color(0xFF5A5A5A),
                        shape = CircleShape,
                    )
            )
            Text(
                text = deviceName ?: stringResource(R.string.no_midi_device),
                style = MaterialTheme.typography.labelMedium,
                color = if (connected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
