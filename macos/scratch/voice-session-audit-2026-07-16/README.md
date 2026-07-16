# Voice/STT session-lifetime hardening — 2026-07-16

The second implementation slice of the concurrency deep pass: the voice/STT
session-lifetime race cluster (audit findings 1, 2, 6, 13, 14, 16, 19). These
two providers are hardware-adjacent (mic / TCC / CoreML) and cannot run under
`swift test`, so the correctness gate for this change is **adversarial
verification**, not unit tests — per the repo's verify-by-launch convention.

## The bugs (from the main audit)

- **AppleSpeechTranscriber** — a stale recognition callback (a superseded
  session's cancel-error or late `isFinal`) ran `stopListening` against *current*
  state and silently killed the fresh session; and a stop landing while `begin()`
  sat in the TCC-authorization suspension let `begin` re-arm the engine
  afterward — **mic hot after stop, no teardown path** (the audit's worst voice
  finding, verified HIGH).
- **WhisperKitProvider** — every `AudioStreamTranscriber` wrapped the **same**
  `kit.audioProcessor` (one shared engine slot), so a fire-and-forget old-stop
  could kill the new session's capture, and a zombie streamer's callbacks kept
  writing the shared `lastText` so the next session's stop re-sent the previous
  utterance to the model as a fresh turn.

## The fix, and how the two verify rounds shaped it

`verify-round1.json` — 3 adversarial lenses on the first attempt (generation
stamp + a self-stop "belt" on both providers). **Result: not sound.** All three
lenses converged: the belt could not make WhisperKit's **shared** processor
safe — `stopStreamTranscription()` tears down whichever engine occupies the
shared slot, so the belt killed *live successors*. AppleSpeech's stale-cleanup
paths (unwind / commit-epilogue / route handler) touched the shared engine
without ownership scoping.

The redesign:
- **WhisperKit → per-session `AudioProcessor()`.** Each session owns its engine,
  so `stopRecording()` is session-scoped and cross-session capture damage is
  *structurally* impossible. The shared `kit.audioProcessor` is gone.
- **AppleSpeech → an `engineOwner: UInt64?` stamp** (under `engineLock`). Every
  engine mutation — install, start, teardown, route-change reinstall — acts only
  if it still owns the engine, so a stale path can never strip a successor's tap
  or stop its running engine.

`verify-round2.json` — same 3 lenses on the revised code. **All round-1 findings
confirmed closed; AppleSpeech sound.** One blocking finding remained, unanimous:
the WhisperKit "residual" I'd documented as a brief/acceptable window was
actually a **permanent** hot mic — a stop landing in `startStreamTranscription`'s
per-start XPC permission hop spends the provider's one fire-and-forget teardown
on a not-yet-armed engine, and the session then records forever. Fix (all three
lenses gave the same one): **re-add the self-stop belt** — now *correct* because
each session owns its processor — with the generation check moved **above** the
empty-text guard so the placeholder-text zombie is actually caught, self-stopping
its own capture within one hop.

The lesson worth keeping: the self-stop belt was **wrong on the shared processor
and right on the per-session one** — the structural change (per-session
processor) is what made the mitigation safe. Deleting the belt in round 2's first
cut and re-adding it after the structural fix was the correct arc, driven
entirely by the adversarial rounds.

## Round 3 — a fresh metacognitive pass (post-round-2)

`verify-round3` (a single deep code-quality reviewer, told not to rubber-stamp
and to cross-check M1K3's assumptions against the *vendored WhisperKit source*,
not the comments) confirmed both files sound on every attack the two adversarial
rounds set up — no double-finish, no never-finish, no `engineLock`↔`lock`
deadlock, no permanent `engineOwner` leak — and independently re-derived the
StreamerBox belt's correctness against `AudioStreamTranscriber`'s actual
synchronous prefix (`state.isRecording = true` → `startRecordingLive` arms the
mic *before* the scheduled self-stop Task can run, so the self-stop is never a
no-op against an unarmed engine).

It found **one real, reachable asymmetry**, now folded: AppleSpeech's
non-final/non-error result branch yielded a partial *without* a generation gate,
while its own error branch and the WhisperKit `onState` both re-check
`generation == self.generation` first. Reachable interleaving: a stop bumps the
generation and captures/clears the continuation but hasn't reached its own
`finish()` yet (it still has `teardownEngineIfOwner` + observer removal to do);
concurrently the recognizer's in-flight delivery for the *superseded* generation
fires one more partial and yields it. Not a crash/leak and it cannot cross into a
successor's stream (the continuation is closure-captured per session), but it
could briefly flicker a stale partial into the transcript UI right after "stop"
was requested. Fix: the same `lock.withLock { generation == self.generation }`
guard the error branch uses, hoisted above the yield — the two providers are now
uniform in exactly the dimension this pass set out to unify.

## Follow-up (bot review, not this PR)

The second bot review made a good structural suggestion worth a future PR: the
crux logic — claim/supersede over a monotonic `generation` counter plus the
`engineOwner` stamp — is pure and hardware-independent, so it could be extracted
into a small non-AVFoundation value type (e.g. `SessionClaim<Owner>`) and pinned
with `swift test`. Today the trickiest logic in the change (the interleavings
three adversarial rounds were needed to reason through) has zero `swift test`
coverage and leans entirely on verify-by-launch + adversarial review. That's the
documented convention for the AVAudioEngine/SFSpeech/WhisperKit *edges*, but the
claim logic itself doesn't touch hardware and could get fast deterministic
regression coverage. Deferred to keep this PR a focused race-fix; logged here so
it isn't lost.

## Residual (honest)

Two nits survive, both documented in-source and neither a regression:
1. AppleSpeech's config-change observer uses `queue: nil` (runs on the posting
   thread); a *theoretical* synchronous post from inside `engine.start()/stop()`
   would self-deadlock the non-reentrant `engineLock`. Pre-existing structure;
   the notification is empirically async; no trigger constructible. Left as-is to
   avoid re-timing verify-by-launch route-change recovery I can't exercise.
2. Upstream WhisperKit: `AudioProcessor.audioSamples` tap-thread/actor race and
   `TextDecoder.languageLogitsFilter` lazy-cache race — pre-existing, upstream,
   unreachable today (`.en` models pin language). A one-line caveat now sits at
   the `DecodingOptions(language:)` derivation for whoever adopts a multilingual
   variant.

Signed: Kev + claude-fable-5, 2026-07-16, Confidence 0.8, Prior: Unknown
Context: the fix is adversarially verified across two rounds, not launch-tested;
every conclusion about AVFoundation/CoreML/SFSpeech timing is static reasoning
per the metallib-wall convention. ⌘R feel-check owed: rapid voice-mode toggling,
barge-in restarts, a route flip mid-listen, and the mic indicator going dark
promptly on stop.
