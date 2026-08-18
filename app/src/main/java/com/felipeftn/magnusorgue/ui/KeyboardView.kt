package com.felipeftn.magnusorgue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.felipeftn.magnusorgue.controller.OrganController
import com.felipeftn.magnusorgue.ui.theme.Gold
import com.felipeftn.magnusorgue.ui.theme.Ivory
import kotlin.math.abs

// Keyboard geometry. None of the stock widgets look remotely like a keybed,
// so this is a Canvas and some arithmetic.
//
// The whole layout derives from the white keys: 15 equal columns (two
// octaves plus the top C), with black keys floating on the boundaries.

/** Semitone offsets of the white keys within one octave (C D E F G A B). */
private val WHITE_SEMIS = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/**
 * White keys that have a black key on their right edge, mapped to that black
 * key's semitone offset. E has no E#, B has no B# — hence the gaps.
 */
private val BLACK_AFTER = mapOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)

private const val WHITE_KEYS = 15
private const val BLACK_HEIGHT = 0.62f // fraction of full key height
private const val BLACK_WIDTH = 0.60f  // fraction of a white key's width

private val IvoryPressed = Gold
private val Ebony = Color(0xFF17171B)
private val EbonyPressed = Color(0xFF8A6420)
private val KeyGap = Color(0xFF0B0B0E)

@Composable
fun KeyboardView(controller: OrganController, modifier: Modifier = Modifier) {
    // baseOctave 4 -> C4 -> MIDI 60. (MIDI note = (octave + 1) * 12 for a C.)
    val lowNote = (controller.baseOctave + 1) * 12

    Canvas(
        modifier
            .background(KeyGap)
            // Keyed on lowNote: shifting the octave restarts the gesture
            // handler, and the finally below releases anything still held.
            .pointerInput(lowNote) { trackTouches(controller, lowNote) }
    ) {
        drawKeyboard(controller.activeNotes, lowNote)
    }
}

/**
 * Raw multitouch tracking: each pointer id maps to the note it pressed.
 * Down/up/cancel all collapse into "is this pointer pressed or not", which
 * conveniently handles gesture cancellation without a special case.
 */
private suspend fun PointerInputScope.trackTouches(
    controller: OrganController,
    lowNote: Int,
) {
    val held = mutableMapOf<PointerId, Int>()
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                for (change in event.changes) {
                    val id = change.id
                    if (change.pressed && id !in held) {
                        val note = noteAt(change.position.x, change.position.y, size, lowNote)
                        held[id] = note
                        controller.noteOn(note)
                    } else if (!change.pressed && id in held) {
                        controller.noteOff(held.remove(id)!!)
                    }
                    // TODO glissando: if a held pointer slides onto another
                    // key, release the old note and trigger the new one.
                    if (change.pressed) change.consume()
                }
            }
        }
    } finally {
        // The handler restarts on octave shift (and on dispose). Whatever
        // was held would become a stuck note — the organist's nightmare.
        held.values.forEach(controller::noteOff)
    }
}

/** Maps a touch position to a MIDI note. Black keys win — they sit on top. */
private fun noteAt(x: Float, y: Float, size: IntSize, lowNote: Int): Int {
    val whiteW = size.width.toFloat() / WHITE_KEYS

    if (y < size.height * BLACK_HEIGHT) {
        for (i in 0 until WHITE_KEYS - 1) {
            val semi = BLACK_AFTER[i % 7] ?: continue
            val centerX = (i + 1) * whiteW // black keys sit on white-key boundaries
            if (abs(x - centerX) <= whiteW * BLACK_WIDTH / 2f) {
                return lowNote + (i / 7) * 12 + semi
            }
        }
    }

    val wi = (x / whiteW).toInt().coerceIn(0, WHITE_KEYS - 1)
    return lowNote + (wi / 7) * 12 + WHITE_SEMIS[wi % 7]
}

private fun DrawScope.drawKeyboard(activeNotes: Set<Int>, lowNote: Int) {
    val whiteW = size.width / WHITE_KEYS

    // White keys first, 1px gaps letting the background show as separators.
    for (i in 0 until WHITE_KEYS) {
        val note = lowNote + (i / 7) * 12 + WHITE_SEMIS[i % 7]
        drawRoundRect(
            color = if (note in activeNotes) IvoryPressed else Ivory,
            topLeft = Offset(i * whiteW + 1f, 0f),
            size = Size(whiteW - 2f, size.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }

    // Black keys painted over them.
    val blackW = whiteW * BLACK_WIDTH
    val blackH = size.height * BLACK_HEIGHT
    for (i in 0 until WHITE_KEYS - 1) {
        val semi = BLACK_AFTER[i % 7] ?: continue
        val note = lowNote + (i / 7) * 12 + semi
        drawRoundRect(
            color = if (note in activeNotes) EbonyPressed else Ebony,
            topLeft = Offset((i + 1) * whiteW - blackW / 2f, 0f),
            size = Size(blackW, blackH),
            cornerRadius = CornerRadius(6f, 6f),
        )
    }
}
