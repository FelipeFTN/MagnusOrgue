# 06 — UI / UX

## Design principles

1. **Stops-first.** The player's hands live on the MIDI keyboard; the screen's job is registration. The console of drawknobs IS the app.
2. **One screen.** Everything visible at once, no navigation.
3. **Organ aesthetic.** Walnut panel, bone-colored drawknobs, brass accents, serif engravings — it should feel like a console, not a settings page.
4. **Landscape only.** Locked via `sensorLandscape` (flippable so the OTG cable can exit on either side).

## Main screen

```
┌───────────────────────────────────────────────────────────────────────┐
│ ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁ │  ← monitor keyboard
│ █████████████████████ 5 octaves, C2–C7 ████████████████████████████── │     (slim, touchable)
├───────────────────────────────────────────────────────────────────────┤
│ ╭─────────────────────── M a g n u s O r g u e ─────────────────────╮ │
│ │                             MANUALE                                │ │
│ │ (PRINCIPALE)(VOCE UMANA)(FLAUTO)(GAMBA)(OTTAVA)(FL.CONICO)(XV)(REGALE) │
│ │     8'          8'        8'      8'     4'       4'      2'   8'  │ │
│ │        PEDALE                          ACCESSORI                   │ │
│ │ (SUBBASSO)(FLAUTO)(C.FAGOTTO)   (TREMOLO)(OTTAVA BASSA)(GEN.CANCEL) │ │
│ │    16'       8'      16'                                           │ │
│ │  ──────●─────────── volume                    ● KORG microKEY     │ │
│ ╰────────────────────────────────────────────────────────────────────╯ │
└───────────────────────────────────────────────────────────────────────┘
```

### The monitor keyboard (top strip)

- Five octaves (C2–C7), full compass at a fixed, slim height (~72dp).
- Primarily a **monitor**: keys light up gold when played from the MIDI keyboard.
- Still touchable — useful for auditioning a registration without hardware.
- No octave shift needed: the whole range is always visible.

### The drawknob console

- Knobs are grouped by **division** — Manuale (8 stops), Pedale (3 stops), Accessori — with small engraved group labels.
- Each stop is a **drawknob**: a round bone-colored knob with the stop name engraved in serif capitals. **Reed stops (Regale, Contro Fagotto) are engraved in red**, as tradition demands.
- **Pulled** state = bright face, brass ring, drop shadow ("out of the panel"). Pushed = darker, recessed.
- Stops **combine** — pulling Principal 8' + Flute 8' stacks both recipes, exactly like ranks on a real organ.
- **No stops pulled = silence.** That's authentic, not a bug.
- Pedal stops only speak in their real compass (up to F4) — pulling Subbasso 16' gives the left hand a pedal foundation without touching the treble.
- **Accessori:** Tremolo (wind wobble) and Ottava Bassa (each key also plays its lower octave — coupled keys light up on the monitor keyboard, like watching coupler action move the keys).
- **General Cancel** is a knob-shaped piston (red engraving): retires all stops and accessories and silences held notes. It never shows a "pulled" state.
- Nameplate ("MagnusOrgue") across the top of the panel, like a builder's plaque.

### Bottom row of the console

| Element | Behavior |
|---|---|
| Volume slider | Master gain, smoothed in the engine |
| Pistons 1–4 | Combination pistons: tap = recall, long-press = store (gold ring + dot when set) |
| Transpose badge | Appears only when transpose ≠ 0 (+2 / -3), so nobody thinks the organ went flat |
| MIDI chip | Gray "No MIDI device" / green dot + device name |

### The side drawer (☰ on the nameplate row)

Everything that isn't playing: a quick how-to (OTG, stops, pistons),
the transpose control, links to sample-set sources (piotrgrabowski.pl,
GrandOrgue) and the GitHub repo/issues, and credits (Giubiasco / Piotr
Grabowski, Oboe, GPLv3). Opens only from the ☰ — edge swipes would fight
the keyboard's lowest keys.

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
