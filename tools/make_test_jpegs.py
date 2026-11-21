"""Regenerates core/src/jvmTest/resources/photos/*.jpg for RealJpegTest (needs Pillow).

    python tools/make_test_jpegs.py core/src/jvmTest/resources/photos

Synthetic stripes with made-up phone metadata (PhoneMakerX / PhoneModelX, GPS of central Lausanne) — no personal data.
"""
import sys, io
from PIL import Image, ImageDraw
out = sys.argv[1]
img = Image.new("RGB", (320, 200), "white")
d = ImageDraw.Draw(img)
for i in range(0, 320, 20): d.rectangle([i, 0, i + 10, 200], fill=(i % 255, 120, 255 - i % 255))
def exif(orientation=6):
    e = Image.Exif()
    e[0x010F] = "PhoneMakerX"; e[0x0110] = "PhoneModelX"; e[0x0112] = orientation; e[0x0132] = "2026:11:21 08:15:00"
    gps = e.get_ifd(0x8825)
    gps[1] = "N"; gps[2] = (46.0, 31.0, 11.0); gps[3] = "E"; gps[4] = (6.0, 37.0, 56.0)
    return e
img.save(f"{out}/baseline.jpg", "JPEG", quality=90, exif=exif())
img.save(f"{out}/progressive.jpg", "JPEG", quality=90, progressive=True, exif=exif(), comment=b"between-secret comment")
second = io.BytesIO(); img.resize((80, 50)).save(second, "JPEG", exif=exif(1))
open(f"{out}/trailer.jpg", "wb").write(open(f"{out}/baseline.jpg", "rb").read() + second.getvalue() + b"\x00\x00\x00\x18ftypmp42 video")
print("ok")
