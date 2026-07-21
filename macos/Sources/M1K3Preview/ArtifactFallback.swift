//
//  ArtifactFallback.swift
//  M1K3Preview
//
//  What the artifact Preview tab shows when it CANNOT safely show the artifact.
//
//  The preview renders untrusted, model-generated HTML behind a WKContentRuleList
//  seal (ArtifactSandboxPolicy). If that seal won't compile, the renderer fails
//  closed — it refuses to load the content rather than fall back to the
//  navigation delegate alone, which never sees sub-resource loads.
//
//  The placeholder that replaces it used to be a bare white page with grey text.
//  That is a bad failure state, not because it's ugly but because it is
//  INDISTINGUISHABLE from a broken renderer: "the artifact pane went white" could
//  equally mean the seal failed closed (working as designed) or the house sheet
//  didn't apply (a real bug). One of those is fine and one is not, and you cannot
//  tell them apart by looking. So the failure now wears the house sheet and says
//  what happened — a deliberate-looking failure, with a code to grep for.
//

import Foundation

public enum ArtifactFallback {
    /// Stable token shared by the placeholder and the log line, so a screenshot
    /// and a log query can be tied together without guessing.
    public static let diagnosticCode = "M1K3-ARTIFACT-SEAL-001"

    /// Shown in the Preview tab when the content sandbox fails to compile.
    /// Self-contained by construction: no network, no scripts, no url() — it must
    /// render under exactly the conditions that just failed.
    public static var sealFailureHTML: String {
        """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8">\(ArtifactHouseStyle.styleElement)</head>
        <body>
        <h1>Preview held back</h1>
        <p>This artifact was <strong>not</strong> rendered, on purpose. M1K3 shows
        generated HTML inside a content sandbox, and this time the sandbox could not
        be initialised — so the preview failed closed rather than display untrusted
        content with weaker protection than usual.</p>
        <p>Nothing is wrong with the artifact itself. Switch to the
        <strong>Code</strong> tab to read the source, or export it and open it in a
        browser you trust.</p>
        <h4>Reference</h4>
        <p><code>\(diagnosticCode)</code></p>
        </body></html>
        """
    }
}
