//
//  WebURLPolicy.swift
//  M1K3Preview
//
//  The agent-facing safety gate for open_link. A visiting agent (over MCP) or
//  M1K3's own local model can surface a web page into the review panel — but it
//  must not be able to aim the embedded WebView at the user's local network: the
//  fetch fires from the user's Mac, so an un-gated open_link would let an opaque
//  automation surface poke router admin pages, localhost daemons, or the
//  169.254.169.254 cloud-metadata address (classic SSRF-lite).
//
//  This guards ONLY the automation paths. A USER typing http://localhost:3000 into
//  the address bar to review their own dev server is legitimate and is routed by
//  ReviewTargetResolver without this gate.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-20, Confidence 0.85, Prior: Unknown

import Foundation

public enum WebURLPolicy {
    /// True when `url`'s host is loopback, an RFC 1918 private range, link-local
    /// (incl. cloud metadata), an mDNS `.local` name, or an IPv6 link/unique-local
    /// address — i.e. somewhere an agent-driven open must NOT reach. A missing host
    /// is treated as local (refused) so the default is safe.
    public static func isLocalOrPrivate(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased(), !host.isEmpty else { return true }

        if host == "localhost" || host.hasSuffix(".localhost") { return true }
        if host.hasSuffix(".local") { return true }

        if let ipv4 = ipv4Octets(host) {
            return isPrivateIPv4(ipv4)
        }
        return isPrivateIPv6(host)
    }

    // MARK: - IPv4

    /// Parse a bare dotted-quad host into four 0–255 octets, or nil if it isn't one.
    private static func ipv4Octets(_ host: String) -> [Int]? {
        let parts = host.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return nil }
        var octets: [Int] = []
        for part in parts {
            guard let value = Int(part), (0 ... 255).contains(value) else { return nil }
            octets.append(value)
        }
        return octets
    }

    private static func isPrivateIPv4(_ octets: [Int]) -> Bool {
        switch (octets[0], octets[1]) {
        case (0, _): return true // 0.0.0.0/8 (incl. 0.0.0.0)
        case (10, _): return true // 10.0.0.0/8
        case (127, _): return true // loopback 127.0.0.0/8
        case (169, 254): return true // link-local 169.254.0.0/16 (cloud metadata)
        case (172, 16 ... 31): return true // 172.16.0.0/12
        case (192, 168): return true // 192.168.0.0/16
        default: return false
        }
    }

    // MARK: - IPv6

    private static func isPrivateIPv6(_ host: String) -> Bool {
        if host == "::1" || host == "::" { return true } // loopback / unspecified
        // Link-local fe80::/10 and unique-local fc00::/7 (fc.. / fd..).
        return host.hasPrefix("fe80:") || host.hasPrefix("fc") || host.hasPrefix("fd")
    }
}
