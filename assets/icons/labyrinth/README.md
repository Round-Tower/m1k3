# M1K3 Labyrinth Icons — 間 (Ma)

A family of circular labyrinth marks interpreted through Ma — the space between the strokes carries as much weight as the line itself. One doorway at the bottom, one crossing at the top of the innermost ring, a small ring-void at centre. Everything else is silence.

## Files

| File | Rings | Target size | Use case |
|---|---|---|---|
| `labyrinth-classical.svg` | 4 | 256 px+ | Hero, splash, loading, onboarding |
| `labyrinth-mid.svg`       | 3 | 64–256 px | Avatar state indicator, cards |
| `labyrinth-glyph.svg`     | 2 | 24–64 px  | Toolbar, menu items, buttons |
| `labyrinth-mark.svg`      | 1 | 16–24 px  | Favicon, system tray — pure enso |

Bold edition: thick calligraphic strokes (3–13% of viewBox), wide doorways
(24°–52°), generous margins. The mark is a single enso brushstroke. Tune
the boldness via `stroke_ratio`, `entrance_deg`, and `margin_ratio` in
`gen_labyrinth.py`.
| `labyrinth.css`           | — | — | Animation module |
| `showcase.html`           | — | — | Live demo of every variant × state × emotion |
| `gen_labyrinth.py`        | — | — | Regenerate with different Ma parameters |

All variants share one geometric language: a wide bottom opening, an optional top crossing on the innermost ring, a centre ring-void. Only the ring count changes.

## Structure

```
<svg class="m1k3-labyrinth">
  <g fill="none" stroke="currentColor" …>
    <path class="m1k3-wall m1k3-wall-0" d="…"/>   ← outer ring
    <path class="m1k3-wall m1k3-wall-1" d="…"/>   ← inner rings
    …
    <circle class="m1k3-core" …/>                 ← centre ring-void (optional)
  </g>
</svg>
```

Each wall is its own path so rings can animate independently. No radial caps — the eye completes the path.

## Quick start

```html
<link rel="stylesheet" href="/assets/icons/labyrinth/labyrinth.css">

<svg class="m1k3-labyrinth is-idle m1k3-emotion-calm" …>
  <!-- paste labyrinth-mid.svg contents here -->
</svg>
```

Inline SVG is required for CSS animations. For static uses, `<img src="labyrinth-glyph.svg">` is fine.

## Animation states

| Class | Behaviour |
|---|---|
| `is-drawing` | Rings reveal outer → inner, then the core lands |
| `is-idle`    | One slow breath — scale + opacity |
| `is-thinking`| Rings drift in alternating directions |
| `is-listening` | Outer ring softly expands |
| `is-speaking`  | Ripple travels centre → outer |
| `is-error`     | Red + brief shake |

Emotion colour modifiers: `m1k3-emotion-{neutral, calm, focus, happy, warm, curious, sad}`.

```html
<svg class="m1k3-labyrinth is-speaking m1k3-emotion-warm">…</svg>
```

## Wiring into M1K3

Map MCP avatar states (from `mcp_unified_server.py`) to class names:

```python
state_map = {
    "idle":      "is-idle",
    "thinking":  "is-thinking",
    "listening": "is-listening",   # start_voice_input
    "speaking":  "is-speaking",    # TTS active
    "error":     "is-error",
}
emotion_map = {
    "neutral":  "m1k3-emotion-neutral",
    "happy":    "m1k3-emotion-happy",
    "sad":      "m1k3-emotion-sad",
    "curious":  "m1k3-emotion-curious",
    "focused":  "m1k3-emotion-focus",
    "calm":     "m1k3-emotion-calm",
    "warm":     "m1k3-emotion-warm",
}
```

Pairs naturally with the 3D avatar (`src/web-avatar/`) as a 2D badge, and the `labyrinth-mark.svg` enso makes a clean tray icon for the Tauri popover (`src/avatar-popover/`).

## Custom properties

Set on `.m1k3-labyrinth` or any ancestor:

```css
--m1k3-color:    currentColor;                 /* stroke + core */
--m1k3-duration: 3.2s;                         /* base animation speed */
--m1k3-ease:     cubic-bezier(.4, 0, .2, 1);
```

## Accessibility

- `role="img"` + `aria-label="M1K3 labyrinth"` on every SVG. Override per context.
- All animations disabled under `prefers-reduced-motion: reduce`; `is-drawing` snaps to final state.

## Regenerating

```bash
python3 gen_labyrinth.py
```

The Ma is tuned by three parameters on `generate()`:

- `entrance_deg` — bottom opening width (bigger = more Ma)
- `inner_top_deg` — top crossing width (0 disables)
- `margin_ratio` — breathing room inside the viewBox

## Preview

Open `showcase.html` in a browser.
