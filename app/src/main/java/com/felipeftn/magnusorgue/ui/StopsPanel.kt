package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felipeftn.magnusorgue.R
import com.felipeftn.magnusorgue.controller.OrganController
import com.felipeftn.magnusorgue.ui.theme.Gold

/**
 * The console: a walnut panel with drawknobs. This is the heart of the app —
 * the player's hands live on the MIDI keyboard, so the screen's whole job
 * is registration.
 *
 * Stop names, in the exact order of kStops in cpp/Stops.h. If you add a stop
 * there, add it here too, in the same slot.
 * TODO: one source of truth for this list instead of a comment and a prayer.
 */
private val STOPS = listOf(
    "Principale" to "8'",
    "Flauto" to "8'",
    "Gamba" to "8'",
    "Ottava" to "4'",
)

// Console palette. Dark walnut, brass, bone.
private val Walnut = Color(0xFF241A12)
private val WalnutEdge = Color(0xFF3D2E1E)
private val Engraving = Color(0xFF3A3020)
private val CancelEngraving = Color(0xFF7A1F1F)

@Composable
fun StopsPanel(controller: OrganController, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Walnut,
        border = BorderStroke(1.dp, WalnutEdge),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Nameplate, like the builder's plaque above a real console.
            Text(
                text = "MagnusOrgue",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Gold,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                letterSpacing = 4.sp,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STOPS.forEachIndexed { index, (name, pitch) ->
                    DrawKnob(
                        title = name,
                        subtitle = pitch,
                        pulled = index in controller.activeStops,
                        onClick = { controller.toggleStop(index) },
                    )
                }

                // General Cancel: the piston that retires every stop and
                // shuts the organ up. Never shows as "pulled" — it's a
                // momentary control, not a stop.
                DrawKnob(
                    title = "General",
                    subtitle = "Cancel",
                    pulled = false,
                    engraving = CancelEngraving,
                    onClick = controller::generalCancel,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Slider(
                    value = controller.volume,
                    onValueChange = controller::changeVolume,
                    modifier = Modifier.weight(1f).widthIn(max = 260.dp),
                )
                MidiChip(controller.midiDeviceName)
            }
        }
    }
}

/**
 * One drawknob. Pulled knobs sit "out": brighter bone, brass ring, a bit of
 * shadow. Pushed-in knobs recede into the panel shade.
 */
@Composable
private fun DrawKnob(
    title: String,
    subtitle: String,
    pulled: Boolean,
    engraving: Color = Engraving,
    onClick: () -> Unit,
) {
    val face = if (pulled) {
        Brush.radialGradient(0f to Color(0xFFF4EDD9), 0.75f to Color(0xFFD6CAA6), 1f to Color(0xFF9A8C6A))
    } else {
        Brush.radialGradient(0f to Color(0xFFCFC6AE), 0.75f to Color(0xFFA1957A), 1f to Color(0xFF5F5540))
    }

    Box(
        modifier = Modifier
            .size(86.dp)
            .shadow(if (pulled) 10.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(face)
            .border(
                width = if (pulled) 3.dp else 2.dp,
                color = if (pulled) Gold else Color(0xFF14100B),
                shape = CircleShape,
            )
            // No ripple: a splash of Material purple on a walnut console
            // would be a crime.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title.uppercase(),
                color = engraving,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle.uppercase(),
                    color = engraving,
                    fontFamily = FontFamily.Serif,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun MidiChip(deviceName: String?) {
    val connected = deviceName != null
    Surface(shape = CircleShape, color = Color(0xFF1B130C), border = BorderStroke(1.dp, WalnutEdge)) {
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
