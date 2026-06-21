//
//  ParkedRecordings.swift
//  M1K3Calls
//
//  A recording captured while call transcription isn't ready is "parked" until it
//  can be processed. Before, the only reference was an in-memory URL pointing into
//  the OS temp dir — so a relaunch (or a temp sweep) lost the recording forever.
//  Now recordings live in a durable directory and the parked set is just "the
//  finished recordings still sitting there": this is the pure read of that listing,
//  plus the user-facing waiting message.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

public enum ParkedRecordings {
    /// Filename prefix every M1K3 recording carries (see CallAudioWriter).
    static let recordingPrefix = "m1k3-call-"

    /// The finished recordings awaiting transcription, from a directory listing.
    /// Only our own finished `.caf` files: a `.partial` is CallAudioWriter's
    /// in-progress/failed staging file, and the `m1k3-call-` prefix excludes any
    /// stray audio a user/other process dropped in the dir (which would otherwise
    /// hit the pipeline and report a confusing failure). Sorted for deterministic
    /// processing.
    public static func pending(in filenames: [String]) -> [String] {
        filenames
            .filter { $0.hasPrefix(recordingPrefix) && $0.hasSuffix(".caf") }
            .sorted()
    }

    /// Status line shown when recordings are parked because transcription isn't
    /// ready yet — so a recorded call never disappears silently.
    public static func waitingMessage(count: Int) -> String {
        let noun = count == 1 ? "recording" : "recordings"
        return "\(count) \(noun) waiting — enable call transcription to process."
    }
}
