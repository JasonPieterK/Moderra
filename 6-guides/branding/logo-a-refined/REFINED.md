# Moderra - Bubble Gate, refined (A1 / A2 / A3)

Owner brief: rounded corners, better colours. Same idea in all three: bubble, three text lines, middle line blocked. Guidance: Logo Design skill (rounded-corner/bone-effect, optical centring, geometric construction). Built by script from one geometry, so the three differ only in colour.

## Geometry (256 grid, shared)
- Bubble body x 28-228, y 32-184, corner radius 40. The tail is one rounded wedge hanging from the bottom edge: right edge at 45 degrees, left edge 15 degrees off vertical, both joined to the bottom edge with concave radius-20 fillets (half the body radius), tip radius 10 (tip y 236). An earlier tail with a vertical left edge made a small boot-like bulge; it was replaced by this single clean shape in all three variants.
- Bars 26 high, pill ends (radius 13), 136 / 136 / 84 wide, 18 gap, inset 32 from the bubble. Blocked bar is the middle one at full width.
- Radii family: 40 / 20 / 13 / 10 (corner, fillet, pill, tip). Mark is centred slightly above geometric centre (tail adds weight below).
- Icon tile: rounded square, radius 56 (22%), mark at 80% scale. Tile is full-bleed so it also works under Modrinth's own rounding.
- No text, strokes, gradients, filters or transforms; 3 colours per mark, plus the tile.

## Palettes and contrast (WCAG 2.x ratios)
| | Bubble | Lines | Blocked | Tile |
|---|---|---|---|---|
| A1 Indigo Coral | #2E2A82 | #FFF1DC | #FF6A55 | #FBF3E4 |
| A2 Night Blue | #1F5BE6 | #E4ECFF | #FF7A6B | #0A1124 |
| A3 Teal Amber | #0B4F57 | #EFFBF7 | #FFB224 | #DCF2EA |

| Pair | A1 | A2 | A3 |
|---|---|---|---|
| bubble vs tile | 10.76 | 3.31 | 7.90 |
| lines vs bubble | 10.66 | 4.80 | 8.73 |
| blocked vs bubble | 4.21 | 2.23 | 5.13 |
| blocked vs lines | 2.53 | 2.15 | 1.70 |

Notes: all bubble/tile and line/bubble pairs pass the 3:1 graphics threshold. The blocked line differs from the lines mainly by hue (warm vs cool) and by value against the bubble; in A2 its contrast to the bubble (2.23) is under 3:1, so that variant relies on hue, and it is the weakest on this point (blocked vs the navy tile is 7.37). In A3 the amber is close to the lines in lightness (1.70) but far from the bubble (5.13), so it still pops. None resembles a flag: no red/white/blue stripes, the red is coral or replaced by amber, and the field is indigo, deep teal or navy. Transparent marks: A1 and A3 bubbles are dark, so use them on light backgrounds (or use the tile on dark); A2's bright blue bubble works on both light and dark.

## Audit (scripts/svg_audit.py, all 6 SVGs)
100/100, no warnings: a1-indigo-coral, a1-indigo-coral-icon, a2-night-blue, a2-night-blue-icon, a3-teal-amber, a3-teal-amber-icon. No near-miss angles (tail edges are 45 and 15 degrees, all else is orthogonal or arcs), no tiny details (smallest feature is the 10-radius tip, 4% of the grid). First A2 draft used pure #FFFFFF lines and scored 92 (white-knockout warning); the cool tint #E4ECFF fixes it and is also softer on a dark tile.

## Files and PNG sizes (all far below 256 KiB)
Per variant: `<id>.svg` (transparent mark), `<id>-icon.svg` (tile), `<id>-512.png` (mark, ~10 KiB), `<id>-icon-{512,256,64,32,16}.png` (about 12.9 / 6.1 / 1.6 / 0.8 / 0.5 KiB). Comparison sheet `refined.png` (73.5 KiB): three tiles at 512, 64, 32, 16 on light and dark. Ids: a1-indigo-coral, a2-night-blue, a3-teal-amber. Rendered with Playwright Chromium; every PNG viewed (512 tiles, marks, 16 px, comparison sheet).

## 16 / 32 px
All three keep the silhouette, three bars and the odd-coloured middle bar at 32 px. At 16 px the bars are about 1.6 px tall: a bubble with a coloured middle stripe still reads. A3 amber stripe and A1 coral stripe are clearest on their light tiles; A2's coral stripe on blue is the lowest-contrast at 16 px.

## Recommendation
A2 Night Blue (owner's choice) is the primary mark. It reads well on dark UIs (Discord, launchers, Modrinth dark theme), the bright blue bubble with a warm coral blocked line is the clearest at 16 px on its tile, and its transparent mark works on light and dark. Its one weakness is the blocked line vs bubble contrast (2.23, hue-led; 7.37 vs the tile), noted above. A1 (cream tile, best raw contrast) and A3 (most distinctive teal/amber) remain as alternates. Trademark and lookalike checks not done.

## A2 final kit (same folder)
- `moderra-icon.svg` (tile, = a2-night-blue-icon.svg), `moderra-icon-512.png` (Modrinth project icon, 13.0 KiB), `favicon-32.png` (0.9 KiB), `favicon-16.png` (0.5 KiB).
- `moderra-mark-white.svg` and `moderra-mark-dark.svg` (#0A1124): one colour, single evenodd path. The two text bars are real holes; the blocked middle bar is deliberately left solid (not cut out), so it reads as a missing/redacted line. All three score 100/100.
