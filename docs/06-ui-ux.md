# 06 — UI / UX

## Design principles

1. **One screen.** Everything needed to play is visible at once; settings hide behind one icon.
2. **Big touch targets.** Keys and buttons sized for playing, not for precision tapping.
3. **Dark by default.** Organ consoles live in dim churches; a bright white screen is hostile.
4. **Landscape first.** More keys, better hand position.

## Main screen (landscape)

```
┌────────────────────────────────────────────────────────────────┐
│ [⚙] MagnusOrgue   [Principal 8' ▾]   [◀ Oct 4 ▶]  🔊──●──   │
│                    🎹 KORG microKEY (connected)        [PANIC] │
├────────────────────────────────────────────────────────────────┤
│ ██  █ █  ██  █ █ █  ██  █ █  ██  █ █ █  ██  █ █  ██           │  ← black keys
│ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │ │           │
│ │C4│D4│E4│F4│G4│A4│B4│C5│D5│E5│F5│G5│A5│B5│C6│ │ │           │  ← white keys
│ └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴─┴─┘           │
└────────────────────────────────────────────────────────────────┘
```

### Top bar elements

| Element | Behavior |
|---|---|
| ⚙ Settings | Opens settings (P1; in MVP may open a simple dialog) |
| Stop selector | Dropdown/segmented control: Principal 8' · Flute 8' · Strings 8' · Tutti |
| Octave shift `◀ Oct N ▶` | Shifts the visible on-screen range by octaves (does NOT transpose MIDI input) |
| Volume slider | Master gain, with smoothing |
| MIDI status | Gray "No MIDI device" / green + device name when connected. Tap = detail (device info, disconnect hint) |
| PANIC | Immediately releases all notes. Prominent but not accidental (edge position) |

## The keyboard component

Custom Compose `Canvas` (not stock widgets — none fit):

- **Layout:** white keys as equal-width rectangles; black keys overlaid at correct offsets (real organ/piano geometry: black key ≈ 60% white key width, ≈ 62% height).
- **Visible range:** ~2 octaves landscape, ~1 octave portrait. Range = `C(octave)` to `C(octave+2)`.
- **Multitouch:** track every pointer id → pressed key. Pointer down = noteOn, up/cancel = noteOff, move across keys = glissando (P1).
- **Hit testing:** black keys checked first (they sit on top).
- **Pressed state rendering:** pressed keys get an accent tint. The pressed set = union of touch-pressed and MIDI-pressed notes (from `OrganUiState.activeNotes`), so playing the external keyboard lights up the screen — great feedback and a fun "it works!" moment.
- **Note labels** (P1): small `C4`-style labels on white keys, toggleable.

## Portrait mode

Supported but secondary: same layout, fewer visible keys (~1 octave). No special design work in MVP beyond "doesn't break".

## Settings screen (P1)

- Ignore MIDI velocity (default ON — organ behavior)
- Note labels on keys (default OFF)
- MIDI channel: Omni / 1–16 (default Omni)
- Reverb on/off (default ON when implemented)
- About (version, open source licenses)

## States & feedback

| State | UI |
|---|---|
| No MIDI device | Neutral gray chip "No MIDI device" — never an error; on-screen keys always work |
| MIDI connected | Green chip with device name; subtle one-time snackbar "KORG microKEY connected" |
| MIDI disconnected (unplug) | Chip returns to gray; all its held notes released |
| Audio engine failed to start | Full-width error banner with "Retry" — the only true error state in the app |

## Visual identity (lightweight)

- **Colors:** near-black background (`#121212`), warm ivory whites for keys, deep charcoal black keys, one accent (deep gold/amber — pipes, brass) for pressed keys and highlights.
- **Icon:** stylized organ pipes forming an "M". Keep it simple; can be a placeholder in MVP.
- **Typeface:** system default (Roboto) — no custom fonts in MVP.
