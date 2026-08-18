#!/usr/bin/env python3
"""Convert GrandOrgue rank samples into MagnusOrgue .mrk asset packs.

Reads the attack samples (A0) of selected ranks from a GrandOrgue sample
set, keeps every Nth pipe, downmixes to mono 16-bit, truncates each pipe
right after its loop end, and writes one compact binary pack per stop.

The samples are NOT committed to git — they belong to the sample set's
author and license. Run this locally against your own copy:

    python3 tools/import_ranks.py "~/Documents/GrandOrgue/Organs/Giubiasco_GrandOrgue"

.mrk format (little-endian), consumed by cpp/Rank.cpp:

    char[4]  magic "MORK"
    u32      version (1)
    u32      sampleRate
    u32      pipeCount
    f32      gain            # per-rank trim, 1.0 = as recorded
    pipeCount * {
        i32  rootNoteMilli   # MIDI note * 1000, tuning fraction included
        u32  loopStart       # frames
        u32  loopEnd         # frames
        u32  frameCount
        u32  dataOffset      # bytes into the PCM blob
    }
    i16[]    PCM blob, all pipes concatenated
"""

import glob
import os
import struct
import sys

import numpy as np

# Which ranks to import and what to call them. Order matches kStops in
# cpp/Stops.h and STOPS in StopsPanel.kt.
RANKS = [
    ("GO Principale 8",      "principale8.mrk"),
    ("GO Flauto a camino 8", "flauto8.mrk"),
    ("GO Viola da Gamba 8",  "gamba8.mrk"),
    ("GO Ottava 4",          "ottava4.mrk"),
]

# Keep one pipe every N semitones; the engine pitch-shifts to the nearest
# kept pipe. 3 (a minor third of coverage, so at most 1 semitone of shift)
# is inaudible on organ tones and cuts the assets to a third.
KEEP_EVERY = 3

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "ranks")


def parse_wav(path):
    """Returns (sample_rate, mono_int16, root_note_milli, loop_start, loop_end).

    Hand-rolled RIFF walk because the stdlib `wave` module refuses 24-bit
    files and knows nothing about the smpl chunk anyway.
    """
    data = open(path, "rb").read()
    assert data[:4] == b"RIFF" and data[8:12] == b"WAVE", f"not a WAV: {path}"

    sr = channels = bits = None
    pcm = None
    root_milli = loop_start = loop_end = None

    pos = 12
    while pos + 8 <= len(data):
        cid = data[pos:pos + 4]
        size = struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = data[pos + 8:pos + 8 + size]

        if cid == b"fmt ":
            fmt, channels, sr, _, _, bits = struct.unpack("<HHIIHH", body[:16])
            assert fmt == 1, f"only plain PCM supported, got fmt={fmt} in {path}"
        elif cid == b"data":
            pcm = body
        elif cid == b"smpl":
            # https://www.recordingblogs.com/wiki/sample-chunk-of-a-wave-file
            unity_note = struct.unpack("<I", body[12:16])[0]
            fraction = struct.unpack("<I", body[16:20])[0]  # 1/2^32 of a semitone
            root_milli = unity_note * 1000 + round(fraction / 2**32 * 1000)
            if struct.unpack("<I", body[28:32])[0] >= 1:
                loop_start = struct.unpack("<I", body[44:48])[0]
                loop_end = struct.unpack("<I", body[48:52])[0]

        pos += 8 + size + (size & 1)  # chunks are word-aligned

    assert pcm is not None and loop_start is not None, f"no data/loop in {path}"
    assert bits == 24, f"expected 24-bit, got {bits} in {path}"

    # 24-bit LE -> int32 (sign-extended) -> average channels -> int16.
    raw = np.frombuffer(pcm, dtype=np.uint8)
    raw = raw[: len(raw) // (3 * channels) * 3 * channels].reshape(-1, channels, 3)
    samples = (
        raw[:, :, 0].astype(np.int32)
        | (raw[:, :, 1].astype(np.int32) << 8)
        | (raw[:, :, 2].astype(np.int8).astype(np.int32) << 16)
    )
    mono = samples.mean(axis=1)
    mono16 = np.clip(np.round(mono / 256.0), -32768, 32767).astype(np.int16)

    return sr, mono16, root_milli, loop_start, loop_end


def build_rank(rank_dir, out_path):
    files = sorted(glob.glob(os.path.join(rank_dir, "A0", "*.wav")))
    kept = files[::KEEP_EVERY]

    headers = []
    blobs = []
    offset = 0
    sample_rate = None

    for path in kept:
        sr, mono, root_milli, loop_start, loop_end = parse_wav(path)
        sample_rate = sample_rate or sr
        assert sr == sample_rate, f"mixed sample rates in {rank_dir}"

        # Everything after the loop is never played; drop it. +2 frames of
        # slack so linear interpolation can read pos+1 at the loop edge.
        frames = min(len(mono), loop_end + 2)
        mono = mono[:frames]

        headers.append(struct.pack("<iIIII", root_milli, loop_start, loop_end, frames, offset))
        blobs.append(mono.tobytes())
        offset += len(blobs[-1])

    with open(out_path, "wb") as out:
        out.write(b"MORK")
        out.write(struct.pack("<III", 1, sample_rate, len(headers)))
        out.write(struct.pack("<f", 1.0))
        out.write(b"".join(headers))
        out.write(b"".join(blobs))

    mb = (offset / 1e6)
    print(f"  {os.path.basename(out_path)}: {len(headers)} pipes, {mb:.1f} MB")


def main():
    if len(sys.argv) != 2:
        sys.exit(f"usage: {sys.argv[0]} <path to Giubiasco_GrandOrgue>")

    organ_dir = os.path.expanduser(sys.argv[1])
    # The data folder is named after the set; just find it.
    data_dirs = [d for d in glob.glob(os.path.join(organ_dir, "Data*")) if os.path.isdir(d)]
    assert data_dirs, f"no 'Data*' folder inside {organ_dir}"
    data_dir = data_dirs[0]

    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Importing from {data_dir}")
    for rank_name, out_name in RANKS:
        rank_dir = os.path.join(data_dir, rank_name)
        assert os.path.isdir(rank_dir), f"rank folder missing: {rank_dir}"
        build_rank(rank_dir, os.path.join(OUT_DIR, out_name))
    print("Done.")


if __name__ == "__main__":
    main()
