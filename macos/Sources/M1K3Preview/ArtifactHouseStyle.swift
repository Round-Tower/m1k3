//
//  ArtifactHouseStyle.swift
//  M1K3Preview
//
//  M1K3's output signature — the artistic direction for everything the
//  resident generates. A CLASSLESS base layer: it styles semantic HTML
//  directly (h1, p, code, table…) so any model output looks deliberately
//  designed with zero cooperation from the model, and it is injected FIRST
//  in <head> so the model's own <style> always cascades over it. The house
//  look is a floor, never a cage.
//
//  The direction (named by the resident himself, 2026-07-15, "The Ghost in
//  the Machine" brief, art-directed by Kev 2026-07-16): near-black ground,
//  off-white ink, ONE accent, sharp corners, generous negative space, a
//  typographic scale that breathes, and quiet CSS motion — content rises in
//  once, nothing loops, and prefers-reduced-motion kills it all. The accent
//  is the pixel-M / CRT phosphor green that already identifies M1K3's
//  companion surfaces — swap `--m1k3-accent` to restyle the whole signature
//  (12B's own pitch was "surgical violet": #8b5cf6).
//
//  Self-contained by necessity: the artifact preview is hermetically sealed
//  (no network, no JS), so no url(), no @import, no webfonts — system stacks
//  only. Pinned by ArtifactHouseStyleTests.
//
//  Signed: Kev + claude-fable-5, 2026-07-16, Confidence 0.85 (structure and
//  cascade order are test-pinned; the aesthetic itself is a taste call
//  verified by render-probe screenshot, not assertion). Prior: Unknown
//

import Foundation

public enum ArtifactHouseStyle {
    /// Marker attribute for idempotence checks and debugging — one per document.
    public static let marker = "data-m1k3-house"

    /// The sheet wrapped as the injectable <style> element.
    public static var styleElement: String {
        "<style \(marker)>\(css)</style>"
    }

    /// The classless house sheet. Custom properties first so a future theme
    /// layer (or a curious user exporting the file) can restyle in one place.
    /// Scale: a ~1.333 (perfect fourth) type ramp on a 16px base; whitespace
    /// runs on an 8px soul with deliberate air around display type.
    public static let css = """
    :root {
      color-scheme: dark;
      --m1k3-bg: #0c0e0d;
      --m1k3-surface: #151816;
      --m1k3-ink: #e8ebe9;
      --m1k3-muted: #98a29c;
      --m1k3-accent: #3ddc97;
      --m1k3-rule: #242826;
    }
    * { box-sizing: border-box; }
    body {
      background: var(--m1k3-bg);
      color: var(--m1k3-ink);
      font: 17px/1.7 -apple-system, system-ui, sans-serif;
      max-width: 42rem;
      margin: 0 auto;
      padding: 5rem 2rem 6rem;
      -webkit-font-smoothing: antialiased;
      /* Entrance is TRANSFORM-ONLY on purpose: a fade-from-0 leaves the page
         invisible if the animation never runs (suspended compositor, occluded
         window — caught live by the render probe 2026-07-16). Content must
         never depend on an animation running to be readable. */
      animation: m1k3-rise 0.6s cubic-bezier(0.2, 0.6, 0.2, 1) backwards;
    }
    h1, h2, h3, h4, h5, h6 {
      line-height: 1.15;
      letter-spacing: -0.022em;
      text-wrap: balance;
      margin: 2.2em 0 0.6em;
    }
    h1 {
      font-size: 2.4rem;
      font-weight: 800;
      padding-bottom: 0.4em;
      border-bottom: 1px solid var(--m1k3-rule);
    }
    h2 { font-size: 1.75rem; font-weight: 700; }
    h3 { font-size: 1.3rem; font-weight: 650; }
    h4 { font-size: 1.05rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--m1k3-muted); }
    h1:first-child, h2:first-child, header:first-child h1 { margin-top: 0; }
    p, ul, ol { margin: 0.9em 0; }
    p { hanging-punctuation: first; }
    header, section, article, footer { margin: 2.5rem 0; }
    a {
      color: var(--m1k3-accent);
      text-decoration: underline;
      text-decoration-color: transparent;
      text-underline-offset: 0.2em;
      transition: text-decoration-color 0.25s ease;
    }
    a:hover { text-decoration-color: var(--m1k3-accent); }
    strong { color: #ffffff; font-weight: 650; }
    em { color: var(--m1k3-ink); }
    small { color: var(--m1k3-muted); font-size: 0.85em; }
    ul, ol { padding-left: 1.5em; }
    li { margin: 0.35em 0; }
    li::marker { color: var(--m1k3-accent); }
    code, kbd, samp, pre { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.88em; }
    code, kbd { background: var(--m1k3-surface); padding: 0.18em 0.45em; }
    pre {
      background: var(--m1k3-surface);
      border: 1px solid var(--m1k3-rule);
      border-left: 2px solid var(--m1k3-accent);
      padding: 1.2em 1.4em;
      margin: 1.4em 0;
      overflow-x: auto;
      line-height: 1.55;
    }
    pre code { background: none; padding: 0; }
    blockquote {
      border-left: 2px solid var(--m1k3-accent);
      margin: 1.4em 0;
      padding: 0.3em 0 0.3em 1.2em;
      color: var(--m1k3-muted);
      font-style: italic;
    }
    table { border-collapse: collapse; width: 100%; margin: 1.6em 0; font-variant-numeric: tabular-nums; }
    th, td { border: 1px solid var(--m1k3-rule); padding: 0.6em 0.85em; text-align: left; }
    th { background: var(--m1k3-surface); font-weight: 650; letter-spacing: 0.02em; }
    hr { border: none; border-top: 1px solid var(--m1k3-rule); margin: 3rem 0; }
    img, video, svg { max-width: 100%; height: auto; }
    figure { margin: 1.6em 0; }
    figcaption { color: var(--m1k3-muted); font-size: 0.88em; margin-top: 0.5em; }
    button, input, select, textarea {
      font: inherit;
      color: inherit;
      background: var(--m1k3-surface);
      border: 1px solid var(--m1k3-rule);
      padding: 0.55em 1em;
      transition: border-color 0.25s ease;
    }
    button:hover, input:focus, textarea:focus { border-color: var(--m1k3-accent); outline: none; }
    ::selection { background: var(--m1k3-accent); color: var(--m1k3-bg); }
    @keyframes m1k3-rise {
      from { transform: translateY(14px); }
      to { transform: none; }
    }
    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { animation: none; transition: none; }
    }
    """
}
