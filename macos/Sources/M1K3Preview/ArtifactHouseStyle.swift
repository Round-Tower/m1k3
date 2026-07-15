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
//  the Machine" brief; re-directed by Kev 2026-07-16 "LaTeX / academia,
//  for the craic"): warm paper ground, serif ink (New York / ui-serif plays
//  Computer Modern — no webfonts, the sandbox stays sealed), CENTERED title,
//  auto-numbered sections via CSS counters, justified + hyphenated prose
//  with LaTeX paragraph indents, booktabs tables (horizontal rules only),
//  small-caps h4, ONE maroon accent (hyperref vibes), and quiet transform-
//  only motion. Swap `--m1k3-accent` to restyle the signature in one line.
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
    /// Typesetting: 17px serif on a 38rem measure, justified + hyphenated with
    /// LaTeX paragraph indents; sections number themselves via CSS counters
    /// ("\\2003" is an em-space — the gap LaTeX puts after a section number).
    public static let css = """
    :root {
      color-scheme: light;
      --m1k3-bg: #faf7f0;
      --m1k3-surface: #f1ede2;
      --m1k3-ink: #1b1b1d;
      --m1k3-muted: #6c6c70;
      --m1k3-accent: #7b1e1e;
      --m1k3-rule: #d9d3c5;
    }
    * { box-sizing: border-box; }
    body {
      background: var(--m1k3-bg);
      color: var(--m1k3-ink);
      font: 17px/1.65 "New York", ui-serif, Georgia, "Times New Roman", serif;
      max-width: 38rem;
      margin: 0 auto;
      padding: 5rem 2rem 6rem;
      text-rendering: optimizeLegibility;
      counter-reset: m1k3-sec;
      /* Entrance is TRANSFORM-ONLY on purpose: a fade-from-0 leaves the page
         invisible if the animation never runs (suspended compositor, occluded
         window — caught live by the render probe 2026-07-16). Content must
         never depend on an animation running to be readable. */
      animation: m1k3-rise 0.6s cubic-bezier(0.2, 0.6, 0.2, 1) backwards;
    }
    h1, h2, h3, h4, h5, h6 { line-height: 1.25; text-wrap: balance; font-weight: 700; }
    h1 { font-size: 1.9rem; text-align: center; margin: 0 0 2.2rem; }
    h2 { font-size: 1.35rem; margin: 2.4em 0 0.7em; counter-increment: m1k3-sec; counter-reset: m1k3-subsec; }
    h2::before { content: counter(m1k3-sec) "\\2003"; }
    h3 { font-size: 1.1rem; margin: 1.8em 0 0.5em; counter-increment: m1k3-subsec; }
    h3::before { content: counter(m1k3-sec) "." counter(m1k3-subsec) "\\2003"; }
    h4 { font-size: 1rem; font-variant-caps: small-caps; letter-spacing: 0.04em; margin: 1.6em 0 0.4em; }
    p { margin: 0.45em 0; text-align: justify; hyphens: auto; -webkit-hyphens: auto; }
    p + p { text-indent: 1.5em; margin-top: 0; }
    a { color: var(--m1k3-accent); text-decoration: underline; text-decoration-color: transparent; text-underline-offset: 0.18em; transition: text-decoration-color 0.25s ease; }
    a:hover { text-decoration-color: var(--m1k3-accent); }
    small, figcaption { color: var(--m1k3-muted); font-size: 0.85em; }
    ul, ol { margin: 0.8em 0; padding-left: 1.8em; }
    li { margin: 0.3em 0; }
    code, kbd, samp, pre { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.84em; }
    code, kbd { background: var(--m1k3-surface); padding: 0.14em 0.4em; }
    pre { background: var(--m1k3-surface); border: 1px solid var(--m1k3-rule); padding: 1em 1.2em; margin: 1.3em 0; overflow-x: auto; line-height: 1.5; }
    pre code { background: none; padding: 0; }
    blockquote { margin: 1.3em 2em; font-style: italic; color: var(--m1k3-muted); }
    table { border-collapse: collapse; width: 100%; margin: 1.6em 0; font-variant-numeric: tabular-nums; border-top: 2px solid var(--m1k3-ink); border-bottom: 2px solid var(--m1k3-ink); }
    th { border-bottom: 1px solid var(--m1k3-ink); font-weight: 700; }
    th, td { padding: 0.5em 0.85em; text-align: left; border-left: none; border-right: none; }
    hr { border: none; border-top: 1px solid var(--m1k3-rule); margin: 2.6rem auto; width: 38%; }
    img, video, svg { max-width: 100%; height: auto; display: block; margin: 1.2em auto; }
    figure { margin: 1.6em 0; }
    figcaption { text-align: center; font-style: italic; margin-top: 0.5em; }
    button, input, select, textarea { font: inherit; color: inherit; background: var(--m1k3-bg); border: 1px solid var(--m1k3-rule); padding: 0.5em 0.95em; transition: border-color 0.25s ease; }
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
