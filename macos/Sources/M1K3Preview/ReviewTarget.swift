//
//  ReviewTarget.swift
//  M1K3Preview
//
//  What the review panel was asked to show, once a raw string has been routed.
//  `.web` and `.file` carry a ready-to-load URL; `.invalid` keeps the raw input
//  so the panel can show it back ("couldn't open '<x>'"); `.empty` is the resting
//  state (nothing typed yet).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation

/// A resolved review request. Pure value type — no UI, no I/O.
public enum ReviewTarget: Equatable, Sendable {
    /// An http(s) link to load in the web view.
    case web(URL)
    /// A local file to hand to QuickLook.
    case file(URL)
    /// Nothing to show yet (blank input).
    case empty
    /// The raw input couldn't be routed to a link or a readable file.
    case invalid(String)
}
