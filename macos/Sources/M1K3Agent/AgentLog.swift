//
//  AgentLog.swift
//  M1K3Agent
//
//  The canonical logging surface (`M1K3Log`, the category catalogue, and
//  `LogPreview`) now lives in the dependency-free `M1K3LogCore` target so the
//  heavy seam modules can reference it too. It is re-exported here so existing
//  `import M1K3Agent` call sites (the agent loop, M1K3Chat, M1K3AgentTools)
//  keep resolving `M1K3Log.agentLoop` / `M1K3Log.subsystem` / `LogPreview`
//  unchanged.
//
//  Watch a whole turn live:
//
//      log stream --predicate 'subsystem == "app.m1k3"' --level debug
//
//  `@_exported` is an underscore SPI (no Swift-Evolution proposal), but it has
//  been stable in Apple + community libraries for years; the alternative is
//  touching every `import M1K3Agent` call site, which buys nothing.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-30, Confidence 0.9 (moved to M1K3LogCore
//  to break the heavy-module dependency landmine; re-export keeps callers stable).
//  Prior: Kev + claude-fable-5, 2026-06-10 (the original M1K3Log/LogPreview).

@_exported import M1K3LogCore
