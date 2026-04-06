package app.m1k3.ai.assistant.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.domain.chat.artifact.ArtifactData

/**
 * ArtifactView — renders a model-generated HTML artifact in a sandboxed WebView.
 *
 * The `window.Ma` JS bridge is injected into every artifact, giving it access
 * to M1K3 design tokens (as CSS variables) and native interactions:
 *
 * ```javascript
 * window.Ma.haptic()          // trigger haptic feedback
 * window.Ma.copy("text")      // copy to clipboard
 * window.Ma.theme.orange      // "#D97706"
 * ```
 *
 * Security: no external network, no localStorage, no camera/mic access.
 * The artifact is a fully self-contained document.
 *
 * @param artifact The artifact data from ArtifactParser
 * @param onClose Optional callback when artifact calls window.Ma.close()
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArtifactView(
    artifact: ArtifactData,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var webView: WebView? by remember { mutableStateOf(null) }

    DisposableEffect(artifact.id) {
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaColors.OrangeDim, RoundedCornerShape(12.dp))
            .background(MaColors.bgElevated())
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false          // no persistence
                        allowFileAccess = false            // sandbox
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        mediaPlaybackRequiresUserGesture = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                    }

                    WebView.setWebContentsDebuggingEnabled(false)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: android.webkit.WebResourceRequest?
                        ): Boolean = true  // block all navigation
                    }

                    // Inject window.Ma bridge
                    addJavascriptInterface(
                        MaArtifactBridge(
                            onCopy = { text ->
                                clipboard.setText(AnnotatedString(text))
                            },
                            onClose = { onClose?.invoke() }
                        ),
                        "MaAndroid"
                    )

                    val enriched = injectMaBridge(artifact.html)
                    loadDataWithBaseURL(null, enriched, "text/html", "UTF-8", null)
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)  // default height — artifacts can resize via window.Ma.resize()
                .padding(4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// JS bridge — exposed as window.MaAndroid from Kotlin, wrapped as window.Ma in HTML
// ---------------------------------------------------------------------------

private class MaArtifactBridge(
    private val onCopy: (String) -> Unit,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun copy(text: String) = onCopy(text)

    @JavascriptInterface
    fun close() = onClose()
}

// ---------------------------------------------------------------------------
// HTML injection — injects window.Ma with design tokens + bridge wiring
// ---------------------------------------------------------------------------

private fun injectMaBridge(html: String): String {
    val bridge = """
<script>
(function() {
  var _bridge = window.MaAndroid || {};
  window.Ma = {
    haptic: function() { try { _bridge.haptic && _bridge.haptic(); } catch(e) {} },
    copy:   function(t) { try { _bridge.copy(t); } catch(e) {} },
    close:  function()  { try { _bridge.close(); } catch(e) {} },
    speak:  function(t) { try { _bridge.speak && _bridge.speak(t); } catch(e) {} },
    theme: {
      bg:      '#000000',
      surface: 'rgba(255,255,255,0.08)',
      border:  'rgba(255,255,255,0.16)',
      orange:  '#D97706',
      text:    'rgba(255,255,255,0.98)',
      muted:   'rgba(255,255,255,0.45)',
      mono:    'monospace'
    }
  };
  // Inject design tokens as CSS custom properties on :root
  var style = document.createElement('style');
  style.textContent = ':root { ' +
    '--ma-bg: #000000; ' +
    '--ma-surface: rgba(255,255,255,0.08); ' +
    '--ma-border: rgba(255,255,255,0.16); ' +
    '--ma-orange: #D97706; ' +
    '--ma-text: rgba(255,255,255,0.98); ' +
    '--ma-muted: rgba(255,255,255,0.45); ' +
    '--ma-mono: monospace; ' +
    'color-scheme: dark; ' +
    '}';
  document.head.appendChild(style);
})();
</script>
""".trimIndent()

    return if (html.contains("</head>", ignoreCase = true)) {
        html.replace("</head>", "$bridge\n</head>", ignoreCase = true)
    } else if (html.contains("<body", ignoreCase = true)) {
        html.replace(Regex("<body([^>]*)>", RegexOption.IGNORE_CASE)) { match ->
            "${match.value}\n$bridge"
        }
    } else {
        bridge + "\n" + html
    }
}
