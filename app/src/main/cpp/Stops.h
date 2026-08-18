#pragma once

// Organ stops as additive-synthesis recipes.
//
// A (flue) organ pipe is close enough to a stack of sine harmonics that we
// can fake convincing stops with amplitude tables — same idea as Hammond
// drawbars. Each entry below is "how loud is harmonic N relative to the
// fundamental". Numbers were tuned by ear against recordings, so don't
// look for math in them.
//
// Quick reference on what real stops sound like:
// https://en.wikipedia.org/wiki/Organ_stop

constexpr int kMaxHarmonics = 16;

struct StopDefinition {
    const char* name;
    float harmonics[kMaxHarmonics];
    float attackMs;   // pipe "speech" — how fast the note blooms
    float releaseMs;
};

// NOTE: order here must match STOP_NAMES on the Kotlin side (TopBar.kt).
// TODO: generate both from one source instead of trusting future-me to
// keep two files in sync.
constexpr StopDefinition kStops[] = {
    {
        // The bread-and-butter organ sound. Full fundamental, harmonics
        // rolling off smoothly.
        "Principal 8'",
        {1.00f, 0.45f, 0.28f, 0.14f, 0.10f, 0.06f, 0.04f, 0.02f,
         0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f},
        10.0f, 90.0f,
    },
    {
        // Stopped flute: mostly fundamental, a touch of the odd harmonics.
        // (Stopped pipes physically suppress even harmonics.)
        "Flute 8'",
        {1.00f, 0.06f, 0.20f, 0.03f, 0.06f, 0.00f, 0.02f, 0.00f,
         0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f},
        14.0f, 110.0f,
    },
    {
        // Gamba-ish string stop: weak-ish fundamental, long bright series.
        "Strings 8'",
        {0.70f, 0.55f, 0.45f, 0.38f, 0.30f, 0.25f, 0.20f, 0.16f,
         0.12f, 0.09f, 0.07f, 0.05f, 0.04f, 0.03f, 0.02f, 0.02f},
        22.0f, 120.0f,
    },
    {
        // Everything at once: principal + octave (h2) + fifth (h3) pumped up.
        // The "final hymn verse" sound.
        "Tutti",
        {1.00f, 0.90f, 0.60f, 0.50f, 0.30f, 0.25f, 0.18f, 0.12f,
         0.08f, 0.05f, 0.04f, 0.03f, 0.02f, 0.02f, 0.01f, 0.01f},
        7.0f, 80.0f,
    },
};

constexpr int kStopCount = sizeof(kStops) / sizeof(kStops[0]);
