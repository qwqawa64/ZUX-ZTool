# web_frames — sample web animations into frames and specs

Porting a CSS/SVG/Web-Animations effect to a Canvas `Drawable` used to mean reading the
CSS by eye and retyping the numbers. This tool drives a real headless Chrome instead, so
the numbers and the frames come from the browser that implements the spec.

Use it for every new `LoadingIndicatorDrawable` style (or any other hand-rolled Canvas
animation) whose reference is a web implementation.

## Requirements

* Node 20+ (uses the built-in `WebSocket`, no npm install).
* Chrome or Edge. Override the path with `--chrome <path>` if it is not in the usual
  Windows/Linux location.

## Commands

```powershell
# 1. Exact numbers: duration, delay, easing, keyframes with per-keyframe easing.
node tools/web_frames/webframes.mjs spec `
  --url tools/web_frames/specimens/mdc-circular.html --selector ".mdc-circular-progress"

# 2. Frames at chosen times; writes PNGs plus manifest.json into --out.
node tools/web_frames/webframes.mjs frames `
  --url tools/web_frames/specimens/mdc-circular.html --selector ".mdc-circular-progress" `
  --times 0,167,333,500,667,833,1000,1167 --out "$env:TEMP\wf-mdc" --scale 2

# 3. One labelled filmstrip for review.
python tools/web_frames/strip.py --dir "$env:TEMP\wf-mdc"
```

| Option | Applies to | Meaning |
|---|---|---|
| `--url` | both | Page to open: a path (turned into `file://`) or an `http(s)` URL |
| `--selector` | both | Element to inspect / clip. `spec` matches animations on it and its descendants |
| `--times` | frames | Comma-separated seek times in ms |
| `--count` / `--period` | frames | Even sampling alternative to `--times` (default 8 frames over 1000 ms) |
| `--out` | frames | Output directory (default `frames`) |
| `--scale` | frames | Device scale factor for the screenshot (default 2) |
| `--width` / `--height` | frames | Viewport, if the layout needs room |
| `--settle` | both | Extra ms to wait after load, for CDN stylesheets |
| `--chrome` | both | Browser executable |

Frames are deterministic: every animation from `document.getAnimations()` is paused and
`currentTime` is set to the requested millisecond before the screenshot, so no timing
race is involved. `strip.py` reads `manifest.json` for the labels.

## Workflow for porting an effect

1. **Capture the reference.** Write a small HTML page under `specimens/` (or point
   `--url` at the library's demo page) that reproduces the effect as published. Prefer
   the library's own CSS over a rewritten approximation.
2. **Extract the spec.** `spec` prints `animationName`, `duration`, `delay`, `easing`,
   `iterations`, `fill` and `getKeyframes()` with a `computedOffset` and per-keyframe
   easing. Those are the values to hardcode, not the ones in the `.scss`.
3. **Look at the frames.** `frames` + `strip.py` gives a filmstrip at identical
   timestamps for the reference and, if wanted, for your own render, so a port can be
   compared row to row.
4. **Port into a `LoadingIndicatorDrawable`** (`hook/modules/zuiui/indicator/`). Keep
   the source constants next to the timing table in the class KDoc, and copy the
   keyframe easing into `Easing.cubicBezier(...)` rather than approximating it with a
   smoothstep — that difference is visible as a wrong arc length.

### Reading the spec output

* `timing.easing: "linear"` on the animation means the real curve lives in the
  **keyframes**: each entry carries its own `easing` (what CSS applies per segment).
* `iterations: "infinite"` is normalised from the DOM's `Infinity`.
* SVG tricks matter: `stroke-dasharray` + `stroke-dashoffset` can encode a semi-circle,
  and `overflow: hidden` clippers can turn a rotation into an arc expansion. Read the
  specimen markup, not only the keyframes.
* Absolute phase is arbitrary when porting; the sweep range, the rhythm and the rotation
  speed are what must match.

## Specimens

| File | Purpose |
|---|---|
| `specimens/spinkit-grid.html` | SpinKit `.sk-grid` (2.0.1 rules verbatim) — used for the cube-grid style |
| `specimens/mdc-circular.html` | Material Components circular progress, indeterminate — used for the circle style |

Both are what the shipped styles were derived from; re-running `spec` on them is a quick
regression check for the constants in `CubeGridLoadingIndicator` / `CircularLoadingIndicator`.

## Limitations

* Only WAAPI/CSS animations are seekable. Effects driven by `requestAnimationFrame`,
  `setInterval`, `<canvas>`/WebGL or Lottie are invisible to `getAnimations()`; for those,
  override the page's clock (e.g. stub `performance.now`/`requestAnimationFrame`) or fall
  back to capturing a screen recording.
* A stylesheet fetched over the network must actually load — pass `--settle 1500` when
  using a CDN link, and check that the frames are not static.
* `file://` pages are opened with `--allow-file-access-from-files`; anything the browser
  still blocks is a browser policy decision, not a tool bug.
