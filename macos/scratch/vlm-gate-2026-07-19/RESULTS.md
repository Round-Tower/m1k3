# VLM load-path gate — Big via VLMModelFactory (2026-07-19)

**Change under test:** `MLXGemmaProvider` routes gemma-4-12B through
`VLMModelFactory` (exact-id allow-list; everything else stays on MLXLLM).
Same checkpoint, same weights, same template — different factory, vision
tower resident. Acceptance gate per the #43 precedent: live-path CHATEVAL
vs the 2026-07-15 runB numbers (LLM path, same fixtures, idle GPU both days).

## Results (Debug build, app closed / GPU idle, single rolls)

| arm | VLM path (today) | LLM path (runB 07-15) |
|---|---|---|
| tool-use (native gemma dialect) | **5/5 · 32.7s median** | 4/5 · 30.9s (one 125s ceiling nick) |
| code-gen (live path) | **5/5 · max 62s** (incl. code-landing-page, e4b's 17-min melter) | 5/5 · 22.5s median, max 57s |
| open-chat (live path) | 6/8, re-roll **7/8** | 8/8 |

RAM: unchanged in practice (VLM-loaded 12B measured 7333MB peak on 07-14;
today's runs behaved identically — no pressure events).

## The one repeat offender: chat-capabilities (0/2 today)

Failed BOTH rolls with a near-identical flowery opening ("I'm your companion
in this machine…") containing none of the fixture's capability keywords.
chat-explain-simply failed roll 1, passed roll 2 → ordinary sampling band.

**Render-difference hypothesis tested and ELIMINATED:** the VLM path's
`Gemma4MessageGenerator` renders user content as typed parts
(`[{"type":"text",…}]`) where the LLM path sends a plain string — but
rendering both shapes through the checkpoint's own `chat_template.jinja`
(jinja2, add_generation_prompt, no tools) produces **byte-identical**
prompts. With the render identical and tools/code-gen *better*, the
chat-capabilities repeat is either a low-p sampling sequence (p(fail)~0.5 →
0/2 is a coin flip away from runB's 1/1) or a genuine register sensitivity
on this one fixture. Named a WATCH-ITEM for Kev's ⌘R feel-check, not a
blocker: the answer is on-persona, just doesn't enumerate capabilities.

## Verdict

**PASS.** Tool-calling (the reason this dep moves, per Package.swift) is
perfect through the VLM processor and better than the LLM baseline; the
code-gen tail behaviour is dramatically better than the e4b era and on par
with runB; open-chat is within band with one named watch fixture. The VLM
path also carries the two strategic unlocks: image input in chat (this PR)
and MTP drafter state emission (once upstream wires Gemma4Unified — PR #61's
upstream-patch draft).

*Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (single rolls per
arm + one re-roll; the render-identity check is template-level via python
jinja2, not a token-id diff through swift-jinja — a residual gap, named; the
chat-capabilities watch-item is honestly unresolved). Prior: Unknown.*

## Addendum — the end-to-end vision proof (same day)

The `M1K3_SELFTEST_VISIONCHAT` probe drives an image through the REAL stack —
`AgentRAGResponder.answerStreaming(images:)` → LocalAgent native loop →
MLXToolTurnSession → VLM-loaded Big — exactly the seam the composer's attach
button sends.

- **Run 4 (image outside the container): CAUGHT A REAL FAILURE MODE.** The
  sandboxed app can't read `~/Development/...`; the image silently never
  reaches the model and Big answers "I can't see any images right now"
  (22.7s). No error surfaces — the processor's read fails inside the turn
  and the responder's fallback swallows it. THIS IS WHY AttachmentStore
  copies every attachment into the container before the send; the UI path
  is immune by construction.
- **Run 5 (container-resident image): PASS.** 34.4s, correct and in-persona:
  "It looks like a bit of stylized ASCII art or a low-resolution blocky
  graphic reading 'M1K3'. It's quite the bold look for my corner of the
  machine." (The M1K3 pixel-face icon — same image class the 07-14 vision
  spike verified through bare ChatSession; this run proves the FULL
  production seam: ToolMessage images → chat mapping → UserInput flattening
  → processor pixels → answer.)

Follow-up worth a red test later: surface the unreadable-attachment case
loudly (a turn-level "couldn't read that image" instead of a blind answer).
