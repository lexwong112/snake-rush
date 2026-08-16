#!/usr/bin/env python3
"""Deterministically synthesize the Snake Rush SFX into app/src/main/res/raw.

Run from the repo root:  python3 tools/generate_sounds.py

No randomness and no external dependencies (stdlib ``wave`` only), so every
run regenerates byte-identical WAV files that can be committed safely. The
clips are short 22.05 kHz mono 16-bit PCM — a few KB each, perfect for
SoundPool. To iterate on a sound, tweak a generator below and re-run.
"""

import math
import os
import struct
import wave

SAMPLE_RATE = 22050
OUT_DIR = os.path.join("app", "src", "main", "res", "raw")


def write_wav(name, samples):
    path = os.path.join(OUT_DIR, name)
    os.makedirs(OUT_DIR, exist_ok=True)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for s in samples:
            clamped = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(clamped * 32767))
        w.writeframes(bytes(frames))
    print(f"wrote {path} ({os.path.getsize(path)} bytes)")


def env_attack(t, attack):
    return min(1.0, t / attack) if attack > 0 else 1.0


def env_decay(t, dur, k):
    """Exponential fade-out (k=3 → ~5% left at end; k=8 → crisp cutoff)."""
    return math.exp(-k * t / dur)


def segment(dur, freq_start, freq_end, volume=1.0, attack=0.004, decay=6.0):
    """One tone sweeping linearly from freq_start to freq_end Hz.

    Phase is integrated analytically (linear chirp) so the sweep is smooth.
    """
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        # phase = 2*pi * (f0*t + 0.5*(f1-f0)*t^2/dur)
        phase = 2.0 * math.pi * (freq_start * t + 0.5 * (freq_end - freq_start) * t * t / dur)
        amp = env_attack(t, attack) * env_decay(t, dur, decay) * volume
        out.append(amp * math.sin(phase))
    return out


def concat(*parts):
    return [s for part in parts for s in part]


def main():
    # eat: bright rising "ding" — short so it stays audible at hard speed.
    write_wav("sfx_eat.wav", segment(0.14, 660, 990, volume=0.8, decay=7.0))
    # game_over: slow falling groan.
    write_wav("sfx_game_over.wav", segment(0.50, 330, 110, volume=0.9, decay=4.0))
    # start: quick ascending two-note "ta-da".
    write_wav(
        "sfx_start.wav",
        concat(
            segment(0.06, 520, 520, volume=0.7, decay=9.0),
            segment(0.10, 780, 780, volume=0.8, decay=8.0),
        ),
    )
    # pause: single soft blip.
    write_wav("sfx_pause.wav", segment(0.12, 440, 440, volume=0.6, decay=8.0))


if __name__ == "__main__":
    main()
