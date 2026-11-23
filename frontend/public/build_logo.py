import io, math, os, zipfile
import uharfbuzz as hb
import pathops
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.recordingPen import DecomposingRecordingPen
import cairosvg
from PIL import Image

OUT = "/mnt/user-data/outputs/savyor-logo"
os.makedirs(OUT, exist_ok=True)

FONT = "node_modules/@fontsource/newsreader/files/newsreader-latin-400-normal.woff"
INK, PAPER, GREEN = "#1A1A1A", "#FAFAF8", "#2F5D50"
TRACK = 0.004  # em (serifs of v and y must not touch)

# ---------------------------------------------------------------- wordmark
tt = TTFont(FONT)
upm = tt["head"].unitsPerEm
xh = tt["OS/2"].sxHeight
gs = tt.getGlyphSet()

tt.flavor = None
buf = io.BytesIO()
tt.save(buf)
face = hb.Face(buf.getvalue())
font = hb.Font(face)
hbuf = hb.Buffer()
hbuf.add_str("savyor")
hbuf.guess_segment_properties()
hb.shape(font, hbuf, {"kern": True, "liga": False})
order = tt.getGlyphOrder()

# collect clean (overlap-free) outlines positioned along the baseline
x = 0
placed = []
for info, pos in zip(hbuf.glyph_infos, hbuf.glyph_positions):
    name = order[info.codepoint]
    rec = DecomposingRecordingPen(gs)
    gs[name].draw(rec)
    p = pathops.Path()
    pen = p.getPen()
    rec.replay(pen)
    p.simplify(fix_winding=True)  # removes overlaps -> safe to fill nonzero
    placed.append((p, x + pos.x_offset))
    x += pos.x_advance + TRACK * upm

# bounds
xmin = ymin = 1e9
xmax = ymax = -1e9
for p, ox in placed:
    b = p.bounds
    xmin, xmax = min(xmin, b[0] + ox), max(xmax, b[2] + ox)
    ymin, ymax = min(ymin, b[1]), max(ymax, b[3])

S = 10.0 / xh        # 1 x-height = 10 svg units
PAD = 1.0


def glyph_d(p, ox, dx):
    sp = SVGPathPen(None, ntos=lambda v: ("%.3f" % v).rstrip("0").rstrip("."))
    # font units (y up) -> svg units (y down), baseline at y=BASE
    tp = TransformPen(sp, (S, 0, 0, -S, dx + (ox - xmin) * S, BASE))
    p.draw(tp)
    return sp.getCommands()


BASE = (ymax) * S + PAD            # baseline y in svg units
W = (xmax - xmin) * S + 2 * PAD
H = (ymax - ymin) * S + 2 * PAD
XH = 10.0                          # x-height in svg units
wm_d = " ".join(glyph_d(p, ox, PAD) for p, ox in placed)


def svg(viewbox, body, extra=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}" fill="none"{extra}>\n'
            f'{body}\n</svg>\n')


wordmark = svg(f"0 0 {W:.2f} {H:.2f}",
               f'  <path fill-rule="nonzero" fill="currentColor" d="{wm_d}"/>')

# ------------------------------------------------------------------- marks
def rounded_poly(pts, r):
    """closed polygon path with rounded corners (quadratic corners)."""
    n = len(pts)
    segs = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        v1 = (p0[0] - p1[0], p0[1] - p1[1]); v2 = (p2[0] - p1[0], p2[1] - p1[1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        a = (p1[0] + v1[0] / l1 * r, p1[1] + v1[1] / l1 * r)
        b = (p1[0] + v2[0] / l2 * r, p1[1] + v2[1] / l2 * r)
        segs.append((a, p1, b))
    d = f"M {segs[0][0][0]:.3f} {segs[0][0][1]:.3f} "
    for i, (a, c, b) in enumerate(segs):
        if i:
            d += f"L {a[0]:.3f} {a[1]:.3f} "
        d += f"Q {c[0]:.3f} {c[1]:.3f} {b[0]:.3f} {b[1]:.3f} "
    return d + "Z"


def bookmark(x0, x1, y0, y1, notch_y, rtop):
    xm = (x0 + x1) / 2
    return (f"M {x0+rtop} {y0} L {x1-rtop} {y0} Q {x1} {y0} {x1} {y0+rtop} "
            f"L {x1} {y1} L {xm} {notch_y} L {x0} {y1} L {x0} {y0+rtop} Q {x0} {y0} {x0+rtop} {y0} Z")


# main mark: 24-unit box, bookmark x5-19 y2-22
MAIN = bookmark(5, 19, 2, 22, 17.5, 2.5) + " " + rounded_poly(
    [(10.4, 6.5), (15.5, 9.75), (10.4, 13.0)], 0.75)
# small-size mark: wider body, shallower notch, bigger play triangle (~44% of width)
SMALL = bookmark(3.5, 20.5, 1.5, 22.5, 18.4, 3) + " " + rounded_poly(
    [(9.2, 5.6), (16.9, 10.2), (9.2, 14.8)], 0.95)

mark = svg("0 0 24 24", f'  <path fill-rule="evenodd" fill="currentColor" d="{MAIN}"/>')
mark_small = svg("0 0 24 24", f'  <path fill-rule="evenodd" fill="currentColor" d="{SMALL}"/>')

# ------------------------------------------------------------------ lockup
MARK_H = 14.5                  # 1.45 x x-height
sc = MARK_H / 20.0             # main mark is 20 units tall (y2..y22)
mark_w = 14 * sc
gap = 0.4 * mark_w
x_center_line = BASE - XH / 2  # centre of the x-height band
mark_top = x_center_line - MARK_H / 2
# place the mark path: map (x5,y2) -> (padX, mark_top)
padX = PAD
tx = padX - 5 * sc
ty = mark_top - 2 * sc
wm_x = padX + mark_w + gap - PAD  # wordmark already has PAD inside
LW = wm_x + W
lock_body = (f'  <path fill-rule="evenodd" fill="currentColor" transform="translate({tx:.3f} {ty:.3f}) scale({sc:.4f})" d="{MAIN}"/>\n'
             f'  <path fill-rule="nonzero" fill="currentColor" transform="translate({wm_x:.3f} 0)" d="{wm_d}"/>')
# make sure the mark never clips top/bottom
top_needed = min(0, mark_top - PAD)
bot_needed = max(H, mark_top + MARK_H + PAD)
if top_needed < 0:
    shift = -top_needed
    lock_body = (f'  <g transform="translate(0 {shift:.3f})">\n{lock_body}\n  </g>')
LH = bot_needed - top_needed
lockup = svg(f"0 0 {LW:.2f} {LH:.2f}", lock_body)

# ---------------------------------------------------------------- app icons
fav = svg("0 0 64 64",
          f'  <rect width="64" height="64" rx="14" fill="{GREEN}"/>\n'
          f'  <path transform="translate(8 8) scale(2)" fill-rule="evenodd" fill="{PAPER}" d="{SMALL}"/>')


def icon_svg(size, kind):
    """kind: round | bleed | maskable"""
    s = size
    if kind == "round":
        bg = f'<rect width="{s}" height="{s}" rx="{s*0.225:.1f}" fill="{GREEN}"/>'
    else:
        bg = f'<rect width="{s}" height="{s}" fill="{GREEN}"/>'
    frac = {"round": 0.58, "bleed": 0.58, "maskable": 0.54}[kind]
    path = SMALL if size <= 192 else MAIN
    h_units = 21 if size <= 192 else 20
    k = s * frac / h_units
    cx = cy = s / 2
    # centre of mark box is (12,12) for both variants
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{s}" height="{s}" viewBox="0 0 {s} {s}">'
            f'{bg}<path transform="translate({cx-12*k:.2f} {cy-12*k:.2f}) scale({k:.4f})" '
            f'fill-rule="evenodd" fill="{PAPER}" d="{path}"/></svg>')


files = {
    "logo-wordmark.svg": wordmark,
    "logo-mark.svg": mark,
    "logo-mark-small.svg": mark_small,
    "logo-lockup.svg": lockup,
    "favicon.svg": fav,
}
for n, c in files.items():
    open(f"{OUT}/{n}", "w", newline="\n").write(c)

for name, size, kind in [("apple-touch-icon.png", 180, "bleed"), ("icon-192.png", 192, "round"),
                         ("icon-512.png", 512, "round"), ("icon-512-maskable.png", 512, "maskable")]:
    cairosvg.svg2png(bytestring=icon_svg(size, kind).encode(), write_to=f"{OUT}/{name}",
                     output_width=size, output_height=size)

# apple icon must be opaque
im = Image.open(f"{OUT}/apple-touch-icon.png").convert("RGB")
im.save(f"{OUT}/apple-touch-icon.png")

print("W,H", round(W, 2), round(H, 2), "lockup", round(LW, 2), round(LH, 2))
