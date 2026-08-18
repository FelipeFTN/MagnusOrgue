package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * The console: a walnut panel with drawknobs grouped by division. This is
 * the heart of the app — the player's hands live on the MIDI keyboard, so
 * the screen's whole job is registration.
 */

private data class StopSpec(val title: String, val subtitle: String, val reed: Boolean = false)

/**
 * Stop knobs in the exact order of kStops in cpp/Stops.h (manual first,
 * then pedal — the stop index is the bit the engine sees). If you add a
 * rank there, add its knob here, same slot.
 * TODO: one source of truth instead of a comment and a prayer.
 */
private val MANUAL_STOPS = listOf(
    StopSpec("Principale", "8'"),
    StopSpec("Voce Umana", "8'"),
    StopSpec("Flauto", "8'"),
    StopSpec("Gamba", "8'"),
    StopSpec("Ottava", "4'"),
    StopSpec("Fl. Conico", "4'"),
    StopSpec("XV", "2'"),  // Quintadecima — "XV" is how consoles engrave it
    StopSpec("Regale", "8'", reed = true),
)
private val PEDAL_STOPS = listOf(
    StopSpec("Subbasso", "16'"),
    StopSpec("Flauto", "8'"),
    StopSpec("C. Fagotto", "16'", reed = true),
)

// Console palette. Dark walnut, brass, bone. Reeds get their traditional
// red engraving.
private val Walnut = Color(0xFF241A12)
private val WalnutEdge = Color(0xFF3D2E1E)
private val Engraving = Color(0xFF3A3020)
private val ReedEngraving = Color(0xFF8A1B1B)
private val CancelEngraving = Color(0xFF7A1F1F)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopsPanel(controller: OrganController, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Walnut,
        border = BorderStroke(1.dp, WalnutEdge),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Nameplate, like the builder's plaque above a real console.
            Text(
                text = "MagnusOrgue",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Gold,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                letterSpacing = 4.sp,
            )

            DivisionLabel("Manuale")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MANUAL_STOPS.forEachIndexed { index, spec ->
                    StopKnob(spec, pulled = index in controller.activeStops) {
                        controller.toggleStop(index)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(3f)) {
                    DivisionLabel("Pedale")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PEDAL_STOPS.forEachIndexed { i, spec ->
                            val index = MANUAL_STOPS.size + i
                            StopKnob(spec, pulled = index in controller.activeStops) {
                                controller.toggleStop(index)
                            }
                        }
                    }
                }
                Column(Modifier.weight(3f)) {
                    DivisionLabel("Accessori")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StopKnob(
                            StopSpec("Tremolo", ""),
                            pulled = controller.tremulant,
                            onClick = controller::toggleTremulant,
                        )
                        // Sub-octave coupler: every key also plays its lower
                        // octave. The budget cousin of a 16' manual stop.
                        StopKnob(
                            StopSpec("Ottava", "Bassa"),
                            pulled = controller.subOctaveCoupler,
                            onClick = controller::toggleSubOctaveCoupler,
                        )
                        // General Cancel: retires everything. A momentary
                        // piston, so it never shows as pulled.
                        StopKnob(
                            StopSpec("General", "Cancel"),
                            pulled = false,
                            engraving = CancelEngraving,
                            onClick = controller::generalCancel,
                        )
                    }
                }
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

@Composable
private fun DivisionLabel(name: String) {
    Text(
        text = name.uppercase(),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Gold.copy(alpha = 0.55f),
        fontFamily = FontFamily.Serif,
        fontSize = 9.sp,
        letterSpacing = 3.sp,
    )
}

/**
 * One drawknob. Pulled knobs sit "out": brighter bone, brass ring, a bit of
 * shadow. Pushed-in knobs recede into the panel shade.
 */
@Composable
private fun StopKnob(
    spec: StopSpec,
    pulled: Boolean,
    engraving: Color = if (spec.reed) ReedEngraving else Engraving,
    onClick: () -> Unit,
) {
    val face = if (pulled) {
        Brush.radialGradient(0f to Color(0xFFF4EDD9), 0.75f to Color(0xFFD6CAA6), 1f to Color(0xFF9A8C6A))
    } else {
        Brush.radialGradient(0f to Color(0xFFCFC6AE), 0.75f to Color(0xFFA1957A), 1f to Color(0xFF5F5540))
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .shadow(if (pulled) 8.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .background(face)
            .border(
                width = if (pulled) 2.dp else 1.dp,
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
                text = spec.title.uppercase(),
                color = engraving,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1,
            )
            if (spec.subtitle.isNotEmpty()) {
                Text(
                    text = spec.subtitle.uppercase(),
                    color = engraving,
                    fontFamily = FontFamily.Serif,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
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
