#pragma once

// The stop list — one entry per sampled rank (Giubiasco, imported via
// tools/import_ranks.py). A stop is just a pointer to its rank pack; the
// sound itself lives in the samples.
//
// Accessories (Tremolo, the sub-octave coupler, General Cancel) are NOT
// stops: they have no pipes. Tremolo lives in the engine as an LFO, the
// coupler lives in the Kotlin controller, and none of them belong here.
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
    // Manuale (Grand'Organo + Positivo)
    {"Principale 8'",     "ranks/principale8.mrk"},
    {"Voce Umana 8'",     "ranks/voceumana8.mrk"},    // celeste — beats against the Principale
    {"Flauto 8'",         "ranks/flauto8.mrk"},
    {"Gamba 8'",          "ranks/gamba8.mrk"},
    {"Ottava 4'",         "ranks/ottava4.mrk"},
    {"Flauto Conico 4'",  "ranks/flautoconico4.mrk"},
    {"Quintadecima 2'",   "ranks/quintadecima2.mrk"},
    {"Regale 8'",         "ranks/regale8.mrk"},       // reed
    // Pedale — these ranks only speak up to F4, which is their compass
    // and, conveniently, exactly the pedal-division behavior we want.
    {"Subbasso 16'",      "ranks/subbasso16.mrk"},
    {"Flauto Ped. 8'",    "ranks/pflauto8.mrk"},
    {"C. Fagotto 16'",    "ranks/controfagotto16.mrk"},  // reed
};

constexpr int kStopCount = sizeof(kStops) / sizeof(kStops[0]);
