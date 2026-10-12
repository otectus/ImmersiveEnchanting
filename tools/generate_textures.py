#!/usr/bin/env python3
"""Verify (--check, default) that the retired Stoneborn GUI textures stay gone, or delete them (--write).

The ritual, result and pending panels draw Minecraft's own GUI textures (the container panel and button faces), so a
resource pack that restyles vanilla containers restyles them too. The mod ships no GUI textures of its own.
"""
import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / 'src/main/resources/assets/immersive_enchanting/textures'
# Textures of the retired Stoneborn GUI. These must never come back.
RETIRED = ['gui/stone.png', 'gui/slot.png']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    failures = []
    for name in RETIRED:
        path = OUTPUT / name
        if args.write and path.exists():
            path.unlink()
        elif path.exists():
            failures.append(name)
    for name in failures:
        print('Retired texture present:', name)
    print(f'{len(RETIRED) - len(failures)}/{len(RETIRED)} retired GUI textures absent' + (' (deleted)' if args.write else ''))
    return bool(failures)


if __name__ == '__main__':
    sys.exit(main())
