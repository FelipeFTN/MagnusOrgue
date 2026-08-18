#pragma once

// The stop list. Since the move to sampled pipes (Giubiasco, imported via
// tools/import_ranks.py) a stop is just a pointer to its rank pack — the
// sound itself lives in the samples.
//
// NOTE: order here must match STOPS in the Kotlin UI (StopsPanel.kt) and
// RANKS in tools/import_ranks.py.
// TODO: generate all three from one source instead of trusting future-me
// to keep them in sync.

struct StopDefinition {
    const char* name;
    const char* assetPath;
};

constexpr StopDefinition kStops[] = {
    {"Principale 8'", "ranks/principale8.mrk"},
    {"Flauto 8'",     "ranks/flauto8.mrk"},
    {"Gamba 8'",      "ranks/gamba8.mrk"},
    {"Ottava 4'",     "ranks/ottava4.mrk"},
};

constexpr int kStopCount = sizeof(kStops) / sizeof(kStops[0]);
