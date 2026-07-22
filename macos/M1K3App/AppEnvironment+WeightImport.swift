//
//  AppEnvironment+WeightImport.swift
//  M1K3App
//
//  Glue for the Advanced pane's "Import weights from a folder…" affordance —
//  the UI entry point for `WeightImport.importWeights` (M1K3MLX, shipped
//  2026-07-21 with zero callers until this file). Everything that decides
//  WHAT to say lives in the pure `WeightImportDisplay` (M1K3MLX); this file
//  only runs the import off the main actor and folds the result into
//  observable state the pane switches over.
//
//  Runs on `Task.detached` (not a plain `await` on the main actor) because
//  `WeightImport.importWeights` is synchronous and hashes gigabytes of
//  pinned files at source — the same reason `IssueReporter.buildReportBody`
//  and `MessageView`'s link detector do the same. The button click that
//  starts this is on the main actor; the hashing is not.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-21, Confidence 0.8 (the state
//  machine is a straight three-state idle/importing/result with a
//  single-flight guard, unit-testable shape but not unit-tested here — it's
//  five lines of side-effect glue over an already-pinned pure core, the same
//  bar `switchEmbeddings`/`enableWhisperKit` are held to in this file
//  family). Honest caveat: the whole path — panel → import → pane copy — is
//  verify-by-launch; no test here drives the app's actual NSOpenPanel or
//  confirms the copy renders in the real Form. Prior: Unknown
//

import Foundation
import M1K3Inference
import M1K3MLX

/// UI lifecycle for one import run. Not persisted — a relaunch mid-import
/// always resumes at `.idle`; the import itself is safely re-runnable
/// (`WeightImport`'s own idempotency: `.alreadyPresent` on a verified
/// destination, never a re-copy).
enum WeightImportRunState: Equatable {
    case idle
    case importing
    case result(WeightImportDisplay.Outcome)
}

extension AppEnvironment {
    /// Verify `source` against `tier`'s pinned manifest and, if it checks
    /// out, install it where the Hub downloader would have put it — so the
    /// app never re-downloads a model the user already has on disk.
    ///
    /// Single-flight: a second call while one is already running is a no-op
    /// (mirrors `switchEmbeddings`'s `!isReindexing` guard) — the button is
    /// also disabled during `.importing`, so this only matters against a
    /// caller that skips the UI guard.
    func importWeights(from source: URL, for tier: BrainTier) async {
        guard weightImportState != .importing else { return }
        guard let modelID = tier.mlxModelID else { return } // Mini: nothing to import
        weightImportState = .importing
        let destination = WeightImport.defaultDestination(for: modelID)
        do {
            let outcome = try await Task.detached(priority: .userInitiated) {
                try WeightImport.importWeights(from: source, repoID: modelID, into: destination)
            }.value
            weightImportState = .result(WeightImportDisplay.outcome(for: outcome))
        } catch let error as WeightImportError {
            weightImportState = .result(WeightImportDisplay.outcome(for: error))
        } catch let error as WeightTamperError {
            weightImportState = .result(WeightImportDisplay.outcome(for: error))
        } catch let error as WeightUnverifiableError {
            weightImportState = .result(WeightImportDisplay.outcome(for: error))
        } catch {
            weightImportState = .result(WeightImportDisplay.outcome(unrecognized: error))
        }
    }
}
