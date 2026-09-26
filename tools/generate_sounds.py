#!/usr/bin/env python3
"""Synthesizes Immersive Enchanting's ritual sounds.

Every sound is generated from a fixed recipe and seed, so the output is reproducible and the mod ships no
third-party audio. Writes Ogg Vorbis files to src/main/resources/assets/immersive_enchanting/sounds/ (via sox).

    python3 tools/generate_sounds.py            # write all sounds
    python3 tools/generate_sounds.py --check    # only report missing sound files (exit 1 if any)
"""
import argparse
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "immersive_enchanting", "sounds")


def t_axis(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def env(t, attack=0.004, decay=0.3):
    a = np.clip(t / attack, 0, 1)
    return a * np.exp(-t / decay)


def fade_tail(x, ms=40):
    """Raised-cosine fade over the last few milliseconds, so nothing ends in a click."""
    n = min(len(x), int(RATE * ms / 1000))
    if n > 1:
        x = x.copy()
        x[-n:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, n)))
    return x


def bell(freq, seconds, decay=0.35, partials=((1.0, 1.0), (2.76, 0.4), (5.4, 0.15), (8.93, 0.04)), attack=0.003):
    """Struck-crystal tone: inharmonic partials, higher ones fading faster. Rendered long enough to decay fully."""
    t = t_axis(max(seconds, decay * 7))
    out = np.zeros_like(t)
    for ratio, amp in partials:
        if freq * ratio > 16000:
            continue
        out += amp * np.sin(2 * np.pi * freq * ratio * t) * env(t, attack, decay / (1 + 0.35 * (ratio - 1)))
    return fade_tail(out)


def noise(seconds, seed):
    return np.random.default_rng(seed).uniform(-1, 1, int(RATE * seconds))


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def room(x, amount=0.3, delays=(0.041, 0.067, 0.093), feedback=0.45, tail=0.6):
    """A small feedback-delay reverb, enough to give chimes some air."""
    y = np.concatenate([x, np.zeros(int(RATE * tail))])
    wet = np.zeros_like(y)
    for d in delays:
        n = int(RATE * d)
        buf = np.zeros_like(y)
        for i in range(n, len(y)):
            buf[i] = y[i - n] + feedback * buf[i - n]
        wet += buf / len(delays)
    return y + amount * wet


def mix(*signals):
    """Sums signals of different lengths, padding the shorter ones with silence."""
    out = np.zeros(max(len(x) for x in signals))
    for x in signals:
        out[: len(x)] += x
    return out


def place(parts, seconds):
    seconds = max(seconds, max(start + len(sig) / RATE for start, sig in parts))
    out = np.zeros(int(RATE * seconds))
    for start, sig in parts:
        s = int(RATE * start)
        e = min(len(out), s + len(sig))
        out[s:e] += sig[: e - s]
    return out


def normalize(x, peak=0.8):
    x = fade_tail(x)
    m = np.max(np.abs(x))
    return x if m == 0 else x * (peak / m)


def recipes():
    r = {}
    # Rune judgements: short, bright, clearly ranked by brightness.
    r["rune/perfect"] = normalize(room(mix(bell(1568.0, 0.55, decay=0.22), 0.5 * bell(2349.3, 0.55, decay=0.16)), 0.22, tail=0.25), 0.7)
    r["rune/good"] = normalize(room(bell(1174.7, 0.45, decay=0.18, partials=((1.0, 1.0), (2.76, 0.3), (5.4, 0.1))), 0.18, tail=0.2), 0.6)
    t = t_axis(0.22)
    graze = (np.sin(2 * np.pi * 587.3 * t) * 0.7 + 0.25 * lowpass(noise(0.22, 3), 0.25)) * env(t, 0.002, 0.07)
    r["rune/graze"] = normalize(graze * (1 + 0.5 * np.sin(2 * np.pi * 38 * t)), 0.45)
    t = t_axis(0.26)
    crack = 0.7 * lowpass(noise(0.26, 7), 0.22) * env(t, 0.001, 0.018) + np.sin(2 * np.pi * (150 - 180 * t) * t) * env(t, 0.002, 0.05)
    crack += 0.25 * bell(740.0, 0.26, decay=0.05, partials=((1.0, 1.0), (1.47, 0.6)))[: len(t)]
    r["rune/miss"] = normalize(fade_tail(crack), 0.5)
    t = t_axis(0.5)
    hum = (np.sin(2 * np.pi * 329.6 * t) + 0.5 * np.sin(2 * np.pi * 494.0 * t) + 0.2 * np.sin(2 * np.pi * 659.3 * t))
    r["rune/hold_pulse"] = normalize(hum * env(t, 0.03, 0.2) * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * t)), 0.4)

    # Ritual frame.
    t = t_axis(0.09)
    r["ritual/count_tick"] = normalize(np.sin(2 * np.pi * 1318.5 * t) * env(t, 0.001, 0.018) + 0.3 * np.sin(2 * np.pi * 2637 * t) * env(t, 0.001, 0.01), 0.45)
    t = t_axis(1.3)
    sweep = np.sin(2 * np.pi * (330 * t + 260 * t * t)) * env(t, 0.25, 0.6)
    shimmer = mix(*(0.25 * bell(f, 1.3, decay=0.5) for f in (987.8, 1318.5, 1975.5)))
    r["ritual/begin"] = normalize(room(mix(0.6 * sweep, place([(0.35, shimmer)], 1.3)), 0.35, tail=0.5), 0.6)

    # Results.
    c = (523.3, 659.3, 784.0, 1046.5)
    r["ritual/success"] = normalize(room(place([(i * 0.09, bell(f, 1.1, decay=0.45)) for i, f in enumerate(c)], 1.5), 0.35, tail=0.6), 0.7)
    bright = c + (1318.5, 1568.0, 2093.0)
    perfect = place([(i * 0.07, bell(f, 1.4, decay=0.55)) for i, f in enumerate(bright)], 2.0)
    perfect = mix(perfect, place([(0.5, 0.35 * bell(3136.0, 1.2, decay=0.4))], 2.0))
    r["ritual/perfect"] = normalize(room(perfect, 0.4, tail=0.8), 0.72)
    frayed = place([(0.0, bell(523.3, 1.0, decay=0.4)), (0.1, bell(659.3, 1.0, decay=0.4)),
                    (0.22, bell(739.99, 1.0, decay=0.3)), (0.38, 0.8 * bell(698.5, 1.0, decay=0.35))], 1.4)
    r["ritual/frayed"] = normalize(room(frayed, 0.3, tail=0.5), 0.6)
    fail = place([(0.0, bell(466.2, 1.0, decay=0.35)), (0.12, bell(415.3, 1.0, decay=0.35)), (0.26, bell(349.2, 1.0, decay=0.4))], 1.4)
    t = t_axis(1.4)
    fail[: len(t)] += 0.35 * lowpass(noise(1.4, 11), 0.3) * env(t, 0.01, 0.12) * (t > 0.3)
    r["ritual/failure"] = normalize(room(fail, 0.25, tail=0.4), 0.6)
    return r


def encode(samples, path):
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        os.makedirs(os.path.dirname(path), exist_ok=True)
        subprocess.run(["sox", wav_path, "-C", "4", path], check=True)
    finally:
        os.unlink(wav_path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="only list missing sound files")
    args = parser.parse_args()
    sounds = recipes()
    if args.check:
        missing = [name for name in sounds if not os.path.exists(os.path.join(OUT, name + ".ogg"))]
        for name in missing:
            print("missing", name)
        print(f"{len(sounds) - len(missing)}/{len(sounds)} sounds present")
        return 1 if missing else 0
    for name, samples in sounds.items():
        path = os.path.join(OUT, name + ".ogg")
        encode(samples, path)
        print(f"wrote {os.path.relpath(path, ROOT)} ({len(samples) / RATE:.2f} s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
