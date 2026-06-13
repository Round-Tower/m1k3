#!/usr/bin/env python3
"""
Generate M1K3 labyrinth icons — 間 (Ma) interpretation, bold edition.

Philosophy: the space between carries as much weight as the stroke. Each
mark is a confident calligraphic gesture against the silence — thick
brush, wide doorway, generous void. The labyrinth is implied, not
enumerated; the eye completes the path.

Two modes:
- mode="stroke": clean circular arcs with thick uniform stroke.
- mode="brush":  each wall is a filled annular shape with tapered ends
                 near the entrance, like a single brushstroke lifted off
                 the page. Same outline geometry, but the ends 'breathe'.
"""

import math
from pathlib import Path

OUT = Path("/sessions/lucid-vibrant-hypatia/mnt/m1k3/assets/icons/labyrinth")
OUT.mkdir(parents=True, exist_ok=True)

# --- helpers ---------------------------------------------------------------

def pt(cx, cy, r, deg):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + r * math.sin(a))

def fmt(v):
    return f"{v:.3f}".rstrip("0").rstrip(".")

def arc(cx, cy, r, a_start, a_end, sweep=1, large=1):
    x1, y1 = pt(cx, cy, r, a_start)
    x2, y2 = pt(cx, cy, r, a_end)
    return f"M {fmt(x1)} {fmt(y1)} A {fmt(r)} {fmt(r)} 0 {large} {sweep} {fmt(x2)} {fmt(y2)}"


def annulus_arc(cx, cy, r_center, half_thick_start, half_thick_end,
                a_start, a_end, sweep_outer=1, large=1):
    """
    Filled annular arc from a_start° to a_end° with variable half-thickness.
    Thickness tapers linearly from half_thick_start (at a_start) to
    half_thick_end (at a_end).

    Decomposes into:
      M start_outer
      A outer_arc end_outer
      L end_inner
      A inner_arc start_inner
      Z
    """
    r_out_s = r_center + half_thick_start
    r_in_s  = r_center - half_thick_start
    r_out_e = r_center + half_thick_end
    r_in_e  = r_center - half_thick_end
    # outer arc start
    p_out_s = pt(cx, cy, r_out_s, a_start)
    p_out_e = pt(cx, cy, r_out_e, a_end)
    p_in_e  = pt(cx, cy, r_in_e,  a_end)
    p_in_s  = pt(cx, cy, r_in_s,  a_start)
    # the arcs are circular at r_out_s, r_in_s — but if thickness tapers,
    # the path is approximate: we use the *outer* and *inner* arc segments
    # at the END radii to draw the curves (close enough at icon scale).
    sweep_inner = 1 - sweep_outer
    return (
        f"M {fmt(p_out_s[0])} {fmt(p_out_s[1])} "
        f"A {fmt(r_out_s)} {fmt(r_out_s)} 0 {large} {sweep_outer} "
        f"{fmt(p_out_e[0])} {fmt(p_out_e[1])} "
        f"L {fmt(p_in_e[0])} {fmt(p_in_e[1])} "
        f"A {fmt(r_in_s)} {fmt(r_in_s)} 0 {large} {sweep_inner} "
        f"{fmt(p_in_s[0])} {fmt(p_in_s[1])} Z"
    )

# --- generator ------------------------------------------------------------

def generate(viewbox=400, n_circuits=4, stroke_ratio=0.035,
             entrance_deg=28, inner_top_deg=22, core_ring=True,
             margin_ratio=0.09, mode="stroke", taper=0.55):
    """
    n_circuits:   number of concentric walls (outer boundary included).
    stroke_ratio: stroke thickness as fraction of viewbox.
    entrance_deg: angular width of the bottom opening.
    inner_top_deg: top crossing on the innermost wall (0 to disable).
    margin_ratio: breathing room from the viewport edge.
    core_ring:    show a small ring-void at centre.
    mode:         "stroke" = uniform stroked arcs;
                  "brush"  = filled annular paths with tapered ends.
    taper:        for brush mode, ratio of end thickness to mid thickness
                  (0.0 = sharp point, 1.0 = uniform). 0.4–0.6 reads as brush.
    """
    cx = cy = viewbox / 2
    sw = viewbox * stroke_ratio
    margin = viewbox * margin_ratio
    r_outer = viewbox / 2 - margin
    # core ring needs to be clear of its own stroke
    core_r = max(r_outer * 0.16, sw * 1.4)
    spacing = (r_outer - core_r * 1.7) / max(n_circuits - 1, 1) if n_circuits > 1 else 0
    radii = [r_outer - i * spacing for i in range(n_circuits)]

    BOTTOM, TOP = 90.0, 270.0
    half_e = entrance_deg / 2
    half_t = inner_top_deg / 2
    half_thick = sw / 2

    walls = []

    if mode == "stroke":
        for i, r in enumerate(radii):
            is_innermost = (i == len(radii) - 1)
            if is_innermost and inner_top_deg > 0:
                walls.append(arc(cx, cy, r, BOTTOM - half_e, TOP + half_t,
                                 sweep=0, large=1))
                walls.append(arc(cx, cy, r, TOP - half_t, BOTTOM + half_e,
                                 sweep=0, large=1))
            else:
                walls.append(arc(cx, cy, r, BOTTOM + half_e, BOTTOM - half_e,
                                 sweep=1, large=1))
    elif mode == "brush":
        for i, r in enumerate(radii):
            is_innermost = (i == len(radii) - 1)
            if is_innermost and inner_top_deg > 0:
                # two brushstrokes meeting on the side, each tapered at both ends
                walls.append(annulus_arc(
                    cx, cy, r,
                    half_thick * taper, half_thick * taper,
                    BOTTOM - half_e, TOP + half_t,
                    sweep_outer=0, large=1))
                walls.append(annulus_arc(
                    cx, cy, r,
                    half_thick * taper, half_thick * taper,
                    TOP - half_t, BOTTOM + half_e,
                    sweep_outer=0, large=1))
            else:
                # single brushstroke from one side of the entrance to the
                # other, tapered at both ends (brush lift on either side
                # of the doorway).
                walls.append(annulus_arc(
                    cx, cy, r,
                    half_thick * taper, half_thick * taper,
                    BOTTOM + half_e, BOTTOM - half_e,
                    sweep_outer=1, large=1))
    else:
        raise ValueError(f"unknown mode: {mode}")

    # --- assemble SVG ---
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {viewbox} {viewbox}"',
        f'     role="img" aria-label="M1K3 labyrinth" class="m1k3-labyrinth">',
    ]
    if mode == "stroke":
        svg += [
            f'  <g fill="none" stroke="currentColor" stroke-width="{fmt(sw)}"',
            f'     stroke-linecap="round" stroke-linejoin="round">',
        ]
        for i, d in enumerate(walls):
            svg.append(f'    <path class="m1k3-wall m1k3-wall-{i}" d="{d}"/>')
        if core_ring:
            svg.append(
                f'    <circle class="m1k3-core" cx="{fmt(cx)}" cy="{fmt(cy)}" '
                f'r="{fmt(core_r)}" fill="none"/>'
            )
    else:  # brush
        svg += [
            f'  <g fill="currentColor" stroke="none">',
        ]
        for i, d in enumerate(walls):
            # brush mode: thicken slightly past the centre of each arc by
            # using a mid-arc bump — done via a second pass; for simplicity
            # we keep uniform half-thickness inside each segment and rely on
            # the line-cap-equivalent rounded ends from sw * taper at the ends.
            svg.append(f'    <path class="m1k3-wall m1k3-wall-{i}" d="{d}"/>')
        if core_ring:
            # ring void: draw as a filled annulus instead of a stroked circle
            outer = annulus_arc(cx, cy, core_r,
                                half_thick * taper, half_thick * taper,
                                0, 359.99,
                                sweep_outer=1, large=1)
            svg.append(f'    <path class="m1k3-core" d="{outer}"/>')
    svg.append('  </g>')
    svg.append('</svg>')
    return "\n".join(svg) + "\n"


# --- variant set: bold, fewer rings, generous openings ---------------------

variants = {
    "labyrinth-classical.svg": dict(viewbox=400, n_circuits=4,
                                    stroke_ratio=0.034, entrance_deg=24,
                                    inner_top_deg=20, margin_ratio=0.09),
    "labyrinth-mid.svg":       dict(viewbox=256, n_circuits=3,
                                    stroke_ratio=0.048, entrance_deg=30,
                                    inner_top_deg=22, margin_ratio=0.10),
    "labyrinth-glyph.svg":     dict(viewbox=128, n_circuits=2,
                                    stroke_ratio=0.075, entrance_deg=38,
                                    inner_top_deg=26, margin_ratio=0.12),
    "labyrinth-mark.svg":      dict(viewbox=64,  n_circuits=1,
                                    stroke_ratio=0.13,  entrance_deg=52,
                                    inner_top_deg=0,   margin_ratio=0.14,
                                    core_ring=False),
}

for name, params in variants.items():
    svg = generate(**params)
    (OUT / name).write_text(svg)
    print(f"wrote {name} ({len(svg)} bytes)")
