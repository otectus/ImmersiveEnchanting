#!/usr/bin/env python3
"""Export (--write) or verify (--check, default) the editable Stoneborn GUI tiles."""
import argparse
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
from gui_art import textures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    artwork = textures()
    root = ROOT / 'src/main/resources/assets/immersive_enchanting/textures'
    failures = []
    for name, expected in artwork.items():
        path = root / name
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            expected.save(path)
        elif not path.exists():
            failures.append(name)
        else:
            with Image.open(path) as image:
                if image.size != expected.size or image.convert('RGBA').tobytes() != expected.tobytes():
                    failures.append(name)
    for name in failures:
        print('Missing or stale:', name)
    print(f'{len(artwork) - len(failures)}/{len(artwork)} GUI textures ' + ('exported' if args.write else 'up to date'))
    return bool(failures)


if __name__ == '__main__':
    sys.exit(main())
