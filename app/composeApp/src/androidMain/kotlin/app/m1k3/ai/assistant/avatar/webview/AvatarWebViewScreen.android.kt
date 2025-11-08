package app.m1k3.ai.assistant.avatar.webview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import app.m1k3.ai.assistant.avatar.AvatarViewModel
import app.m1k3.ai.assistant.avatar.collectAsState
import app.m1k3.ai.assistant.design.tokens.MaColors
import com.multiplatform.webview.web.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of 3D Avatar WebView Screen
 *
 * Uses compose-webview-multiplatform library for WebView integration.
 *
 * JavaScript Bridge Communication:
 * - Kotlin → JS: evaluateJavaScript("updateAvatarState(...)")
 * - JS → Kotlin: window.AndroidBridge methods (if needed)
 *
 * Asset Loading:
 * - HTML: file:///android_asset/avatar3d/index.html
 * - Models: Base64 data URLs (required due to WebView CORS restrictions)
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AvatarWebViewScreen(
    avatarViewModel: AvatarViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // WebView state - Use file:// URL (standard Android assets)
    val webViewState = rememberWebViewState("file:///android_asset/avatar3d/index.html")
    val webViewNavigator = rememberWebViewNavigator()

    // Avatar state
    val avatarState by avatarViewModel.collectAsState()

    // FPS tracking (fetched from JavaScript)
    var currentFPS by remember { mutableStateOf(0) }

    // Current model name display
    var currentModelName by remember { mutableStateOf("No Model") }

    // WebView loaded flag
    var isWebViewLoaded by remember { mutableStateOf(false) }

    // Update Three.js scene when avatar state changes
    LaunchedEffect(avatarState) {
        if (isWebViewLoaded) {
            val stateJson = avatarState.toJS()
            val javascript = """
                if (typeof updateAvatarState === 'function') {
                    updateAvatarState('$stateJson');
                }
            """.trimIndent()

            webViewNavigator.evaluateJavaScript(javascript) { result ->
                // JavaScript execution complete
            }
        }
    }

    // Periodically fetch FPS from JavaScript
    LaunchedEffect(isWebViewLoaded) {
        if (isWebViewLoaded) {
            while (true) {
                delay(1000) // Update every second

                webViewNavigator.evaluateJavaScript("getCurrentFPS()") { result ->
                    result?.toIntOrNull()?.let { fps ->
                        currentFPS = fps
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "3D Avatar - WebView POC",
                        color = MaColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaColors.BgElevated
                )
            )
        },
        containerColor = MaColors.BgPrimary
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Three.js WebView (top 60%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                WebView(
                    state = webViewState,
                    navigator = webViewNavigator,
                    modifier = Modifier.fillMaxSize(),
                    onCreated = { webView ->
                        // Configure WebView settings
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false

                            // Mobile optimizations
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        println("[間 AI WebView] WebView created and configured")
                    },
                    onDispose = { webView ->
                        println("[間 AI WebView] WebView disposed")
                    }
                )

                // Monitor loading state
                when (val loadingState = webViewState.loadingState) {
                    is LoadingState.Finished -> {
                        LaunchedEffect(Unit) {
                            // Wait a bit for JavaScript to initialize
                            delay(500)
                            isWebViewLoaded = true
                            println("[間 AI WebView] Page fully loaded, JavaScript ready")
                        }
                    }
                    is LoadingState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(16.dp),
                            color = MaColors.Orange
                        )
                    }
                    else -> { /* Initial or error state */ }
                }
            }

            Divider(color = MaColors.BgSecondary, thickness = 2.dp)

            // Controls (bottom 40% - scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
            ) {
                WebViewDemoControls(
                    avatarViewModel = avatarViewModel,
                    onLoadModel = { animal ->
                        scope.launch {
                            // WebView CORS restrictions require Base64 data URLs
                            // WebViewAssetLoader doesn't work with compose-webview-multiplatform
                            val glbBytes = withContext(Dispatchers.IO) {
                                context.assets.open("models/${animal.fileName}").use { it.readBytes() }
                            }
                            val base64 = android.util.Base64.encodeToString(glbBytes, android.util.Base64.NO_WRAP)
                            val dataUrl = "data:model/gltf-binary;base64,$base64"

                            // Update current model name display
                            currentModelName = "${animal.emoji} ${animal.name}"

                            println("[間 AI WebView] Loading ${animal.name}: ${glbBytes.size} bytes (29 morphs + 18 anims!)")

                            val javascript = """
                                if (typeof loadGLBModel === 'function') {
                                    loadGLBModel('$dataUrl');
                                }
                            """.trimIndent()

                            webViewNavigator.evaluateJavaScript(javascript) { result ->
                                println("[間 AI WebView] ${animal.name} model loaded with morphs + animations!")
                            }
                        }
                    },
                    currentFPS = currentFPS,
                    currentModelName = currentModelName
                )
            }
        }
    }
}
