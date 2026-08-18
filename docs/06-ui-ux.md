# 06 — UI / UX

## Design principles

1. **Stops-first.** The player's hands live on the MIDI keyboard; the screen's job is registration. The console of drawknobs IS the app.
2. **One screen.** Everything visible at once, no navigation.
3. **Organ aesthetic.** Walnut panel, bone-colored drawknobs, brass accents, serif engravings — it should feel like a console, not a settings page.
4. **Landscape only.** Locked via `sensorLandscape` (flippable so the OTG cable can exit on either side).

## Main screen

```
┌────────────────────────────────────────────────────────────────┐
│ ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁ │  ← monitor keyboard
│ ███████████████████ 5 octaves, C2–C7 ██████████████████████── │     (slim, touchable)
├────────────────────────────────────────────────────────────────┤
│ ╭──────────────────── M a g n u s O r g u e ─────────────────╮ │
│ │                                                            │ │
│ │   (PRINCIPAL)   (FLUTE)   (STRINGS)   (TUTTI)   (GENERAL)  │ │  ← drawknobs
│ │      8'           8'         8'                  CANCEL    │ │
│ │                                                            │ │
│ │   ──────●─────────────── volume        ● KORG microKEY    │ │
│ ╰────────────────────────────────────────────────────────────╯ │
└────────────────────────────────────────────────────────────────┘
```

### The monitor keyboard (top strip)

- Five octaves (C2–C7), full compass at a fixed, slim height (~72dp).
- Primarily a **monitor**: keys light up gold when played from the MIDI keyboard.
- Still touchable — useful for auditioning a registration without hardware.
- No octave shift needed: the whole range is always visible.

### The drawknob console

- Each stop is a **drawknob**: a round bone-colored knob with the stop name engraved in serif capitals.
- **Pulled** state = bright face, brass ring, drop shadow ("out of the panel"). Pushed = darker, recessed.
- Stops **combine** — pulling Principal 8' + Flute 8' stacks both recipes, exactly like ranks on a real organ.
- **No stops pulled = silence.** That's authentic, not a bug.
- **General Cancel** is a knob-shaped piston (red engraving): retires all stops and silences held notes. It replaced the old PANIC button and never shows a "pulled" state.
- Nameplate ("MagnusOrgue") across the top of the panel, like a builder's plaque.

### Bottom row of the console

| Element | Behavior |
|---|---|
| Volume slider | Master gain, smoothed in the engine |
| MIDI chip | Gray "No MIDI device" / green dot + device name |

## States & feedback

| State | UI |
|---|---|
| No MIDI device | Neutral gray chip — never an error; touch keys always work |
| MIDI connected | Green dot + device name |
| MIDI disconnected (unplug) | Chip returns to gray; held notes released |
| Audio engine failed to start | Full-width error banner with "Retry" — the only true error state |

## Visual identity

- **Palette:** near-black background (`#121212`), dark walnut console (`#241A12`), bone knobs, gold/brass accent (`#C99A3A`), ivory keys.
- **Type:** serif (system) for engravings and the nameplate; default sans elsewhere.
- **Icon:** the five gold pipes from the logo.
