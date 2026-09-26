"""Original Stoneborn-style GUI tiles, matching the Immersive workshop palette."""
from PIL import Image, ImageDraw

def gui_stone():
    """Quiet 16x8 masonry, using the Stoneborn GUI ramp in Ultima's inventory and anvil.

    Original tile layout: the text and controls sit on solid insets, leaving masonry as the surround.
    """
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 31), fill='#242426')
    for row in range(4):
        y = row * 8
        for col in range(-1, 3):
            x = col * 16 + (8 if row % 2 else 0)
            d.rectangle((x + 1, y + 1, x + 15, y + 6), fill='#3a3939')
            d.line((x + 2, y + 1, x + 14, y + 1), fill='#484848')
            d.line((x + 1, y + 2, x + 1, y + 5), fill='#404040')
            d.rectangle((x + 3, y + 2, x + 7, y + 3), fill='#404040')
            d.line((x + 9, y + 5, x + 13, y + 5), fill='#343434')
            d.point((x + 12, y + 2), fill='#434445')
            d.line((x + 2, y + 7, x + 15, y + 7), fill='#313131')
    return im


def gui_slot():
    """The shared 18px recessed slot, also used by the JEI categories."""
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 17, 17), fill='#675d4e')
    d.line((0, 0, 17, 0), fill='#151310')
    d.line((0, 0, 0, 17), fill='#151310')
    d.rectangle((1, 1, 16, 16), fill='#37322a')
    return im


def textures():
    return {"gui/stone.png": gui_stone(), "gui/slot.png": gui_slot()}
