# Swift 6 concurrency deep pass — 2026-07-16

A full-codebase async/await + Swift 6 concurrency audit of `macos/Sources` (24
modules) plus both app shells, run as an 8-dimension × adversarial-verify
workflow (67 agents: 8 finders → 58 verifiers → synthesis). Every finding below
survived an independent refutation pass against the real code; 22 additional
claims were killed by the verifiers (list at the end of FINDINGS.md).

**Method:** finders swept one dimension each (`@unchecked Sendable` audit ·
unstructured Tasks · continuations/AsyncStream · legacy locks · MainActor
hygiene · polling/priority · await-parallelism · modern-API adoption). Each
finding then went to a dedicated verifier prompted to REFUTE it — read the
callers, check reachability, check compatibility with signed MurphySig
decisions and pinned tests. Verdicts include severity re-grades (several
claimed "high" were downgraded on reachability) and revised fixes where the
auditor's fix was wrong (several were).

**Score: 36 confirmed / 22 refuted.** FINDINGS.md carries the full digest
(scenario, fix, revised fix, verifier reasoning) for all 36.

## The clusters

1. **Voice/STT session-lifetime races** (findings 1, 2, 6, 13, 14, 16, 19):
   AppleSpeechTranscriber and WhisperKitProvider restart races. Worst case:
   a stop racing `begin()`'s TCC-dialog suspension leaves the **mic engine hot
   after stop** with no teardown path (6/16, verified high). Shared fix shape:
   lock-guarded session generation + teardown sequencing + `onTermination`.
   Largely verify-by-launch (real engines, TCC dialogs).
2. **Model-loading single-flight gaps** (0, 17, 12/32): MLXEmbeddingService
   check-then-act double-load; SingleFlightLoader's documented waiter-cancel
   contract is dead code (proven by compiled probe — `task.value` is not
   cancellation-responsive for waiters); Kokoro's FileDownloader stages HTTP
   error bodies as weights and permanently bricks the neural voice.
3. **MainActor hangs** (21, 22, 24, 25, 30, 31, 23): Touch-ID Keychain read
   blocks launch; synchronous file reads on drag-and-drop in both shells;
   whole-conversation JSON encode per turn; Calls decrypt-all on open.
4. **Call-recording memory** (11, 26): ~1.4 GB/hour of PCM in RAM (peak ~2× at
   stop()), reallocs under a lock shared with the capture threads; a crash
   loses the whole recording.
5. **The word clock** (27): 30 Hz MainActor polling of engine-lock-taking
   AVAudioNode properties — the concrete candidate for the long-open bench
   item "#4 audio priority-inversion"; fix is anchor-and-extrapolate.

## What shipped from this audit (PR: concurrency hardening pass 1)

Package-only, fully unit-provable slice: findings **17** (SingleFlightLoader
waiter cancellation made real), **0** (MLXEmbeddingService → SingleFlightLoader),
**12/32** (Kokoro download status validation + staged-file self-heal + download
cancellation), **4/20** (Mutex + checked-Sendable conversions), **33** (dead
`@preconcurrency` drops in M1K3Calls).

## Deliberately deferred (and why)

- **Everything touching `M1K3App/AppEnvironment.swift`** (5, 7, 21, 22, 31, 34):
  a parallel session had uncommitted WIP in that file during this pass
  (the dictation bail-out, committed as 6f067667 on local master). Finding 5
  (send() re-entrancy, verified HIGH) lands in the exact dictation code that
  session was editing — first in line once the tree is shared again.
- **Voice/STT cluster** (1, 2, 6, 13, 14, 16, 19): its own PR; fixes are
  interlocking (generation counter + teardown ordering) and verify-by-launch.
- **Call-recording streaming-to-disk** (11/26) and **word clock** (27): M-effort
  designs with signed-invariant interactions (CallAudioWriter atomicity,
  SpeakingState doctrine); 27 doubles as the bench-item-#4 close.
- **ChatSession async save** (25/30): needs the pinned-test-compatible
  await-inside-send shape described in the digest; rides an M1K3Chat touch.
- **embedBatch mini-batching** (29): needs an MLX cosine-parity integration
  test; right-padding is load-bearing (attention mask is INERT in
  Qwen3Model.callAsFunction — see the verifier note before implementing).

Signed: Kev + claude-fable-5, 2026-07-16, Confidence 0.85, Prior: Unknown
Context: audit evidence for the concurrency deep pass; verdicts are
adversarially verified but severity/effort remain judgment calls. The 22
refuted titles at the end of FINDINGS.md are intentionally kept — they are the
claims a future audit should NOT re-raise without new evidence.
