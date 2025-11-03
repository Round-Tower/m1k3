# Phase 4: Multi-Modal & Projects (Weeks 11-12)

## Overview

**Duration:** 2 weeks (Weeks 11-12)
**Total Tickets:** 20 tickets
**Dependencies:** Phase 3 complete (knowledge systems functional)

### Goals
- Implement multi-modal support (text + images)
- Integrate CameraX for image capture
- Add ML Kit vision capabilities (OCR, object detection, label detection)
- Build project management CRUD operations
- Create project browser UI with scoped conversations
- Implement project-scoped memory filtering

### Success Criteria
- [ ] Camera capture functional with image preview
- [ ] ML Kit detects objects, text, labels in images
- [ ] AI describes images contextually
- [ ] Projects CRUD working with all validations
- [ ] Project browser UI functional
- [ ] Memory scoped correctly per project
- [ ] All 20 tests passing

---

## Week 11: Multi-Modal (Tickets 001-010)

### PHASE4-001: CameraX Integration ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 5h | **Status:** [ ]

**Description:**
Integrate CameraX library for image capture with preview and permission handling.

**Implementation:**
File: `app/composeApp/src/androidMain/kotlin/ai/ma/camera/CameraManager.kt`

```kotlin
/**
 * Camera Manager - Handles image capture using CameraX
 *
 * Provides camera preview, image capture, and permission management
 * for multi-modal AI interactions.
 */
class CameraManager(
    private val context: Context
) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    /**
     * Start camera preview
     *
     * @param lifecycleOwner Activity lifecycle
     * @param previewView Surface for camera preview
     * @param lensFacing Front or back camera
     */
    suspend fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK
    ) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        this.cameraProvider = cameraProvider

        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image capture use case
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Select camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            // Unbind previous use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            throw CameraException("Failed to start camera preview", e)
        }
    }

    /**
     * Capture image
     *
     * @return URI of captured image
     */
    suspend fun captureImage(): Uri = suspendCoroutine { continuation ->
        val imageCapture = imageCapture ?: run {
            continuation.resumeWithException(
                CameraException("Image capture not initialized")
            )
            return@suspendCoroutine
        }

        // Create output file
        val photoFile = File(
            context.cacheDir,
            "IMG_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Capture image
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    continuation.resume(Uri.fromFile(photoFile))
                }

                override fun onError(exc: ImageCaptureException) {
                    continuation.resumeWithException(
                        CameraException("Image capture failed", exc)
                    )
                }
            }
        )
    }

    /**
     * Stop camera
     */
    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    /**
     * Check camera permission
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class CameraException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Extension for ProcessCameraProvider
suspend fun ProcessCameraProvider.Companion.getInstance(context: Context): ProcessCameraProvider {
    return suspendCoroutine { continuation ->
        getInstance(context).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}
```

File: `app/composeApp/src/androidMain/AndroidManifest.xml` (modification)

```xml
<!-- Camera permission -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

**Acceptance Criteria:**
- [ ] CameraX integrated with dependencies
- [ ] Camera preview functional
- [ ] Image capture saves to cache directory
- [ ] Permission handling implemented
- [ ] Front/back camera switching supported
- [ ] Proper lifecycle management

**Tests:**
- [ ] `CameraManagerTest.kt`: `@Test fun captureImage_returnsValidUri()`
- [ ] `CameraManagerTest.kt`: `@Test fun startPreview_bindsToLifecycle()`
- [ ] `CameraManagerTest.kt`: `@Test fun hasPermission_checksCorrectly()`

**Dependencies:** None (platform APIs)

**Blocks:** PHASE4-002 (ML Kit integration)

---

### PHASE4-002: ML Kit Vision Integration

**Priority:** P0 | **Estimated Hours:** 6h | **Status:** [ ]

**Description:**
Integrate ML Kit for image analysis (object detection, OCR, label detection).

**Implementation:**
File: `app/composeApp/src/androidMain/kotlin/ai/ma/vision/VisionAnalyzer.kt`

```kotlin
/**
 * Vision Analyzer - ML Kit image analysis
 *
 * Provides object detection, text recognition (OCR), and label detection
 * for multi-modal AI conversations.
 */
class VisionAnalyzer(
    private val context: Context
) {
    private val objectDetector: ObjectDetector
    private val textRecognizer: TextRecognizer
    private val imageLabeler: ImageLabeler

    init {
        // Object detector
        val objectDetectorOptions = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(objectDetectorOptions)

        // Text recognizer (OCR)
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Image labeler
        val labelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()
        imageLabeler = ImageLabeling.getClient(labelerOptions)
    }

    /**
     * Analyze image - runs all detectors
     *
     * @param imageUri URI of image to analyze
     * @return Complete vision analysis
     */
    suspend fun analyzeImage(imageUri: Uri): VisionAnalysis {
        val inputImage = InputImage.fromFilePath(context, imageUri)

        // Run all detectors in parallel
        val objectsDeferred = async { detectObjects(inputImage) }
        val textDeferred = async { recognizeText(inputImage) }
        val labelsDeferred = async { detectLabels(inputImage) }

        return VisionAnalysis(
            objects = objectsDeferred.await(),
            text = textDeferred.await(),
            labels = labelsDeferred.await(),
            imageUri = imageUri
        )
    }

    /**
     * Detect objects in image
     */
    private suspend fun detectObjects(image: InputImage): List<DetectedObject> {
        return suspendCoroutine { continuation ->
            objectDetector.process(image)
                .addOnSuccessListener { objects ->
                    val detected = objects.map { obj ->
                        DetectedObject(
                            boundingBox = obj.boundingBox,
                            trackingId = obj.trackingId,
                            labels = obj.labels.map { label ->
                                ObjectLabel(
                                    text = label.text,
                                    confidence = label.confidence
                                )
                            }
                        )
                    }
                    continuation.resume(detected)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * Recognize text (OCR)
     */
    private suspend fun recognizeText(image: InputImage): RecognizedText {
        return suspendCoroutine { continuation ->
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox,
                            confidence = block.confidence ?: 0f,
                            lines = block.lines.map { line ->
                                TextLine(
                                    text = line.text,
                                    boundingBox = line.boundingBox
                                )
                            }
                        )
                    }

                    continuation.resume(
                        RecognizedText(
                            fullText = visionText.text,
                            blocks = blocks
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * Detect image labels
     */
    private suspend fun detectLabels(image: InputImage): List<ImageLabel> {
        return suspendCoroutine { continuation ->
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    val detected = labels.map { label ->
                        ImageLabel(
                            text = label.text,
                            confidence = label.confidence,
                            index = label.index
                        )
                    }
                    continuation.resume(detected)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * Generate natural language description
     */
    fun generateDescription(analysis: VisionAnalysis): String {
        val parts = mutableListOf<String>()

        // Labels (most confident)
        if (analysis.labels.isNotEmpty()) {
            val topLabels = analysis.labels
                .sortedByDescending { it.confidence }
                .take(3)
                .joinToString(", ") { it.text }
            parts.add("This image contains: $topLabels")
        }

        // Objects detected
        if (analysis.objects.isNotEmpty()) {
            val objectCount = analysis.objects.size
            parts.add("I detected $objectCount object(s)")

            // Describe objects with labels
            analysis.objects.forEach { obj ->
                if (obj.labels.isNotEmpty()) {
                    val label = obj.labels.maxByOrNull { it.confidence }
                    parts.add("- ${label?.text} (${(label?.confidence?.times(100))?.toInt()}% confidence)")
                }
            }
        }

        // Text recognized
        if (analysis.text.fullText.isNotBlank()) {
            parts.add("Text detected: \"${analysis.text.fullText.take(100)}...\"")
        }

        return parts.joinToString("\n")
    }

    fun close() {
        objectDetector.close()
        textRecognizer.close()
        imageLabeler.close()
    }
}

// Data models
data class VisionAnalysis(
    val objects: List<DetectedObject>,
    val text: RecognizedText,
    val labels: List<ImageLabel>,
    val imageUri: Uri
)

data class DetectedObject(
    val boundingBox: Rect,
    val trackingId: Int?,
    val labels: List<ObjectLabel>
)

data class ObjectLabel(
    val text: String,
    val confidence: Float
)

data class RecognizedText(
    val fullText: String,
    val blocks: List<TextBlock>
)

data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
    val lines: List<TextLine>
)

data class TextLine(
    val text: String,
    val boundingBox: Rect?
)

data class ImageLabel(
    val text: String,
    val confidence: Float,
    val index: Int
)
```

**Acceptance Criteria:**
- [ ] ML Kit dependencies configured
- [ ] Object detection functional
- [ ] OCR recognizes text accurately
- [ ] Label detection works
- [ ] Natural language description generation
- [ ] Async processing (non-blocking)

**Tests:**
- [ ] `VisionAnalyzerTest.kt`: `@Test fun analyzeImage_detectsObjects()`
- [ ] `VisionAnalyzerTest.kt`: `@Test fun recognizeText_extractsText()`
- [ ] `VisionAnalyzerTest.kt`: `@Test fun detectLabels_confidenceThreshold()`
- [ ] `VisionAnalyzerTest.kt`: `@Test fun generateDescription_formatsProperly()`

**Dependencies:** PHASE4-001 (Camera manager)

**Blocks:** PHASE4-003 (Multi-modal AI integration)

---

### PHASE4-003: Multi-Modal AI Integration ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 5h | **Status:** [ ]

**Description:**
Extend AI engine to handle image inputs with vision analysis context.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/ai/MultiModalEngine.kt`

```kotlin
/**
 * Multi-Modal Engine - Handles text + image inputs
 *
 * Combines vision analysis with text generation for contextual
 * image understanding and conversation.
 */
class MultiModalEngine(
    private val visionAnalyzer: VisionAnalyzer,
    private val ragEngine: RAGEngine,
    private val aiEngine: SmolLM2Engine
) {
    /**
     * Generate response for image + optional text query
     *
     * @param imageUri Image to analyze
     * @param query Optional text query about the image
     * @param conversationHistory Recent messages for context
     * @return AI response describing/answering about the image
     */
    suspend fun generateResponseForImage(
        imageUri: Uri,
        query: String? = null,
        conversationHistory: List<Message>
    ): MultiModalResponse {
        // 1. Analyze image with ML Kit
        val visionAnalysis = visionAnalyzer.analyzeImage(imageUri)

        // 2. Generate vision description
        val visionDescription = visionAnalyzer.generateDescription(visionAnalysis)

        // 3. Build multi-modal context
        val context = buildMultiModalContext(
            visionAnalysis = visionAnalysis,
            visionDescription = visionDescription,
            query = query,
            conversationHistory = conversationHistory
        )

        // 4. Generate AI response
        val aiQuery = query ?: "Describe this image in detail"
        val aiResponse = aiEngine.generateWithContext(
            query = aiQuery,
            context = context
        )

        return MultiModalResponse(
            text = aiResponse,
            visionAnalysis = visionAnalysis,
            imageUri = imageUri
        )
    }

    private fun buildMultiModalContext(
        visionAnalysis: VisionAnalysis,
        visionDescription: String,
        query: String?,
        conversationHistory: List<Message>
    ): String {
        return buildString {
            appendLine("You are M1K3 AI analyzing an image.")
            appendLine()

            appendLine("**Image Analysis:**")
            appendLine(visionDescription)
            appendLine()

            // Add detailed vision data
            if (visionAnalysis.labels.isNotEmpty()) {
                appendLine("**Detected Labels:**")
                visionAnalysis.labels.forEach { label ->
                    appendLine("- ${label.text} (${(label.confidence * 100).toInt()}%)")
                }
                appendLine()
            }

            if (visionAnalysis.text.fullText.isNotBlank()) {
                appendLine("**Text in Image:**")
                appendLine("\"${visionAnalysis.text.fullText}\"")
                appendLine()
            }

            if (visionAnalysis.objects.isNotEmpty()) {
                appendLine("**Objects:**")
                visionAnalysis.objects.forEach { obj ->
                    val label = obj.labels.maxByOrNull { it.confidence }
                    if (label != null) {
                        appendLine("- ${label.text}")
                    }
                }
                appendLine()
            }

            // Add conversation context
            if (conversationHistory.isNotEmpty()) {
                appendLine("**Recent Conversation:**")
                conversationHistory.takeLast(3).forEach { msg ->
                    val role = if (msg.isUser) "User" else "Assistant"
                    appendLine("$role: ${msg.content.take(100)}")
                }
                appendLine()
            }

            // Add user query
            if (query != null) {
                appendLine("**User Question:**")
                appendLine(query)
            }
        }
    }
}

data class MultiModalResponse(
    val text: String,
    val visionAnalysis: VisionAnalysis,
    val imageUri: Uri
)
```

**Acceptance Criteria:**
- [ ] MultiModalEngine implemented
- [ ] Integrates vision analysis with AI generation
- [ ] Builds comprehensive multi-modal context
- [ ] Handles optional text queries
- [ ] Context stays within token budget

**Tests:**
- [ ] `MultiModalEngineTest.kt`: `@Test fun generateResponseForImage_describesImage()`
- [ ] `MultiModalEngineTest.kt`: `@Test fun generateResponseForImage_answersQuery()`
- [ ] `MultiModalEngineTest.kt`: `@Test fun buildMultiModalContext_includesVisionData()`

**Dependencies:**
- PHASE4-002 (Vision analyzer)
- PHASE3-006 (RAG engine)

**Blocks:** PHASE4-004 (Camera UI)

---

### PHASE4-004: Camera Capture UI

**Priority:** P1 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create camera capture UI with preview, capture button, and gallery access.

**Implementation:**
File: `app/composeApp/src/androidMain/kotlin/ai/ma/ui/camera/CameraScreen.kt`

```kotlin
@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraManager = remember { CameraManager(context) }

    var hasCameraPermission by remember {
        mutableStateOf(cameraManager.hasPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraPreviewScreen(
            cameraManager = cameraManager,
            lifecycleOwner = lifecycleOwner,
            onImageCaptured = onImageCaptured,
            onClose = onClose
        )
    } else {
        PermissionDeniedScreen(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onClose = onClose
        )
    }
}

@Composable
fun CameraPreviewScreen(
    cameraManager: CameraManager,
    lifecycleOwner: LifecycleOwner,
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    coroutineScope.launch {
                        cameraManager.startPreview(
                            lifecycleOwner = lifecycleOwner,
                            previewView = this@apply
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar with close button
        TopAppBar(
            title = { Text("Take Photo") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            ),
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery button (future: pick from gallery)
            IconButton(
                onClick = { /* TODO: Gallery picker */ },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Photo,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Capture button
            Button(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                        coroutineScope.launch {
                            try {
                                val uri = cameraManager.captureImage()
                                onImageCaptured(uri)
                            } catch (e: Exception) {
                                // Handle error
                                isCapturing = false
                            }
                        }
                    }
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.Gray
                ),
                enabled = !isCapturing
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black
                    )
                }
            }

            // Camera flip button
            IconButton(
                onClick = { /* TODO: Switch camera */ },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stop()
        }
    }
}

@Composable
fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "M1K3 AI needs camera access to analyze images",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }

        TextButton(onClick = onClose) {
            Text("Cancel")
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Camera preview displays correctly
- [ ] Capture button functional with loading state
- [ ] Permission handling with rationale
- [ ] Close/back navigation works
- [ ] Responsive layout (portrait/landscape)

**Tests:**
- [ ] `CameraScreenTest.kt`: `@Composable @Test fun captureButton_enabled()`
- [ ] `CameraScreenTest.kt`: `@Composable @Test fun permissionDenied_showsRationale()`

**Dependencies:** PHASE4-001 (Camera manager)

**Blocks:** PHASE4-005 (Image message UI)

---

### PHASE4-005: Image Message UI

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Extend message UI to display image attachments with analysis results.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/domain/model/Message.kt` (modification)

```kotlin
data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant,
    val projectId: String?,

    // Multi-modal support
    val imageUri: String? = null,
    val visionAnalysis: String? = null // JSON serialized
)
```

File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/chat/MessageCard.kt` (modification)

```kotlin
@Composable
fun MessageCard(
    message: Message,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Image attachment (if present)
                if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "Attached image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Message text
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Timestamp
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Message model supports image attachments
- [ ] Image displays in message card
- [ ] Image scales properly (max height)
- [ ] Vision analysis stored as metadata
- [ ] Backward compatible (text-only messages)

**Tests:**
- [ ] `MessageCardTest.kt`: `@Composable @Test fun messageWithImage_displays()`
- [ ] `MessageCardTest.kt`: `@Composable @Test fun textOnlyMessage_noImage()`

**Dependencies:** PHASE4-003 (Multi-modal engine)

**Blocks:** PHASE4-006 (ChatViewModel integration)

---

### PHASE4-006: Integrate Multi-Modal with ChatViewModel

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Extend ChatViewModel to handle image inputs and trigger vision analysis.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/viewmodel/ChatViewModel.kt` (modification)

```kotlin
class ChatViewModel(
    private val aiEngine: SmolLM2Engine,
    private val messageRepository: MessageRepository,
    private val memoryManager: MemoryManager,
    private val ragEngine: RAGEngine,
    private val multiModalEngine: MultiModalEngine // NEW
) : ViewModel() {

    // ... existing code ...

    /**
     * Send message with optional image attachment
     */
    fun sendMessage(content: String, imageUri: Uri? = null) {
        viewModelScope.launch {
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                content = content,
                isUser = true,
                timestamp = Clock.System.now(),
                projectId = _currentProject.value?.id,
                imageUri = imageUri?.toString()
            )

            _messages.value += userMessage
            messageRepository.createMessage(userMessage)

            _isGenerating.value = true

            try {
                val response = if (imageUri != null) {
                    // Multi-modal response
                    multiModalEngine.generateResponseForImage(
                        imageUri = imageUri,
                        query = content.ifBlank { null },
                        conversationHistory = _messages.value
                    )
                } else {
                    // Text-only response
                    ragEngine.generateResponse(
                        query = content,
                        conversationHistory = _messages.value,
                        projectId = _currentProject.value?.id
                    )
                }

                val assistantMessage = Message(
                    id = UUID.randomUUID().toString(),
                    content = response.text,
                    isUser = false,
                    timestamp = Clock.System.now(),
                    projectId = _currentProject.value?.id,
                    visionAnalysis = if (response is MultiModalResponse) {
                        serializeVisionAnalysis(response.visionAnalysis)
                    } else null
                )

                _messages.value += assistantMessage
                messageRepository.createMessage(assistantMessage)

                // Create memories
                memoryManager.createMemoriesFromMessage(userMessage)
                memoryManager.createMemoriesFromMessage(assistantMessage)

            } catch (e: Exception) {
                _error.value = "Failed to generate response: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun serializeVisionAnalysis(analysis: VisionAnalysis): String {
        // Serialize to JSON for storage
        return Json.encodeToString(analysis)
    }
}
```

**Acceptance Criteria:**
- [ ] ChatViewModel handles image messages
- [ ] Calls multiModalEngine when image present
- [ ] Falls back to text-only for non-image messages
- [ ] Stores vision analysis in message metadata
- [ ] Creates memories from multi-modal messages

**Tests:**
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_withImage_callsMultiModal()`
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_textOnly_callsRAG()`

**Dependencies:**
- PHASE4-003 (Multi-modal engine)
- PHASE4-005 (Image message UI)

**Blocks:** PHASE4-007 (Camera button in chat)

---

### PHASE4-007: Camera Button in Chat Interface

**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Add camera/attachment button to chat input bar.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/chat/ChatInput.kt` (modification)

```kotlin
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCameraClick: () -> Unit, // NEW
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Camera/attachment button
        IconButton(
            onClick = onCameraClick,
            enabled = enabled
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Take photo"
            )
        }

        // Text input field
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message...") },
            enabled = enabled,
            maxLines = 4
        )

        // Send button
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send"
            )
        }
    }
}
```

File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/chat/ChatScreen.kt` (modification)

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    var showCamera by remember { mutableStateOf(false) }
    val inputText by viewModel.inputText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    if (showCamera) {
        CameraScreen(
            onImageCaptured = { uri ->
                viewModel.sendMessage(inputText, imageUri = uri)
                showCamera = false
            },
            onClose = { showCamera = false }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Messages list
            MessageList(
                messages = viewModel.messages.collectAsState().value,
                modifier = Modifier.weight(1f)
            )

            // Input bar
            ChatInput(
                value = inputText,
                onValueChange = { viewModel.updateInputText(it) },
                onSend = { viewModel.sendMessage(inputText) },
                onCameraClick = { showCamera = true },
                enabled = !isGenerating
            )
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Camera button added to input bar
- [ ] Opens camera screen when clicked
- [ ] Sends message with image when captured
- [ ] Disabled during AI generation
- [ ] Icon clearly indicates camera function

**Tests:**
- [ ] `ChatInputTest.kt`: `@Composable @Test fun cameraButton_clickable()`
- [ ] `ChatScreenTest.kt`: `@Composable @Test fun cameraButton_opensCamera()`

**Dependencies:**
- PHASE4-004 (Camera UI)
- PHASE4-006 (ChatViewModel integration)

---

### PHASE4-008: Multi-Modal Benchmark Test

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Benchmark vision analysis performance and accuracy.

**Implementation:**
File: `app/shared/src/androidUnitTest/kotlin/ai/ma/benchmark/MultiModalBenchmarkTest.kt`

```kotlin
class MultiModalBenchmarkTest {

    @Test
    fun visionAnalysis_performance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val visionAnalyzer = VisionAnalyzer(context)

        // Load test images
        val testImages = loadTestImages() // 10 diverse images

        val times = mutableListOf<Long>()

        testImages.forEach { imageUri ->
            val start = System.currentTimeMillis()
            val analysis = visionAnalyzer.analyzeImage(imageUri)
            val duration = System.currentTimeMillis() - start

            times.add(duration)

            println("""
                Image: $imageUri
                Time: ${duration}ms
                Objects: ${analysis.objects.size}
                Labels: ${analysis.labels.size}
                Text: ${analysis.text.fullText.length} chars
            """.trimIndent())
        }

        val avgTime = times.average()
        val maxTime = times.maxOrNull() ?: 0

        println("Average vision analysis time: ${avgTime.toInt()}ms")
        println("Max time: ${maxTime}ms")

        // Assert performance targets
        assertTrue(avgTime < 3000, "Average time should be <3s")
        assertTrue(maxTime < 5000, "Max time should be <5s")
    }

    @Test
    fun objectDetection_accuracy() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val visionAnalyzer = VisionAnalyzer(context)

        // Test with known objects
        val catImage = loadTestImage("cat.jpg")
        val analysis = visionAnalyzer.analyzeImage(catImage)

        // Should detect "cat" or "animal" or "pet"
        val catDetected = analysis.labels.any { label ->
            label.text.lowercase() in listOf("cat", "animal", "pet", "mammal")
        }

        assertTrue(catDetected, "Should detect cat in image")
        assertTrue(analysis.labels.first().confidence > 0.7f, "Confidence should be >70%")
    }

    private fun loadTestImages(): List<Uri> {
        // Load test images from assets
        return emptyList() // Implementation details...
    }

    private fun loadTestImage(filename: String): Uri {
        // Load specific test image
        return Uri.EMPTY // Implementation details...
    }
}
```

**Acceptance Criteria:**
- [ ] Benchmarks vision analysis time (<3s average)
- [ ] Tests object detection accuracy (>70% confidence)
- [ ] Tests OCR with known text
- [ ] Tests label detection with diverse images
- [ ] Reports performance metrics

**Tests:**
- [ ] `MultiModalBenchmarkTest.kt`: `@Test fun visionAnalysis_performance()`
- [ ] `MultiModalBenchmarkTest.kt`: `@Test fun objectDetection_accuracy()`
- [ ] `MultiModalBenchmarkTest.kt`: `@Test fun ocrAccuracy_knownText()`

**Dependencies:** PHASE4-002 (Vision analyzer)

---

### PHASE4-009: Image Cache Management

**Priority:** P2 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Implement image cache cleanup to prevent storage bloat.

**Implementation:**
File: `app/composeApp/src/androidMain/kotlin/ai/ma/storage/ImageCacheManager.kt`

```kotlin
/**
 * Image Cache Manager - Manages captured image storage
 *
 * Cleans up old cached images to prevent storage bloat.
 */
class ImageCacheManager(
    private val context: Context
) {
    private val cacheDir = context.cacheDir
    private val maxCacheSize = 100 * 1024 * 1024 // 100MB
    private val maxImageAge = 7.days

    /**
     * Clean up old or excessive images
     */
    suspend fun cleanupCache() {
        withContext(Dispatchers.IO) {
            val imageFiles = cacheDir.listFiles { file ->
                file.extension in listOf("jpg", "jpeg", "png")
            } ?: return@withContext

            // Delete images older than 7 days
            val now = Clock.System.now()
            imageFiles.forEach { file ->
                val fileAge = now - Instant.fromEpochMilliseconds(file.lastModified())
                if (fileAge > maxImageAge) {
                    file.delete()
                    println("Deleted old image: ${file.name}")
                }
            }

            // If still over size limit, delete oldest first
            val totalSize = imageFiles.filter { it.exists() }.sumOf { it.length() }
            if (totalSize > maxCacheSize) {
                val sorted = imageFiles.filter { it.exists() }
                    .sortedBy { it.lastModified() }

                var currentSize = totalSize
                for (file in sorted) {
                    if (currentSize <= maxCacheSize) break

                    currentSize -= file.length()
                    file.delete()
                    println("Deleted to reduce cache size: ${file.name}")
                }
            }
        }
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val imageFiles = cacheDir.listFiles { file ->
                file.extension in listOf("jpg", "jpeg", "png")
            } ?: emptyArray()

            CacheStats(
                imageCount = imageFiles.size,
                totalSize = imageFiles.sumOf { it.length() },
                oldestImage = imageFiles.minOfOrNull { it.lastModified() }
                    ?.let { Instant.fromEpochMilliseconds(it) }
            )
        }
    }
}

data class CacheStats(
    val imageCount: Int,
    val totalSize: Long,
    val oldestImage: Instant?
)
```

**Acceptance Criteria:**
- [ ] Deletes images older than 7 days
- [ ] Enforces 100MB cache size limit
- [ ] Provides cache statistics
- [ ] Runs efficiently in background
- [ ] Doesn't delete referenced images

**Tests:**
- [ ] `ImageCacheManagerTest.kt`: `@Test fun cleanupCache_deletesOldImages()`
- [ ] `ImageCacheManagerTest.kt`: `@Test fun cleanupCache_enforcesSize Limit()`

**Dependencies:** PHASE4-001 (Camera manager)

---

### PHASE4-010: Phase 4 Week 11 Integration Test

**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Integration test validating multi-modal functionality end-to-end.

**Implementation:**
File: `app/shared/src/commonTest/kotlin/ai/ma/integration/Phase4Week11IntegrationTest.kt`

```kotlin
@Test
fun multiModal_endToEndFlow() = runTest {
    // Setup
    val context = createTestContext()
    val visionAnalyzer = VisionAnalyzer(context)
    val multiModalEngine = createTestMultiModalEngine(visionAnalyzer)
    val chatViewModel = createTestChatViewModel(multiModalEngine)

    // 1. Load test image
    val testImageUri = loadTestImage("sample_document.jpg")

    // 2. Analyze image
    val analysis = visionAnalyzer.analyzeImage(testImageUri)

    // Should detect text (document)
    assertTrue(analysis.text.fullText.isNotBlank())
    assertTrue(analysis.labels.isNotEmpty())

    // 3. Send image message
    chatViewModel.sendMessage(
        content = "What does this document say?",
        imageUri = testImageUri
    )

    // Wait for response
    advanceUntilIdle()

    // 4. Verify response
    val messages = chatViewModel.messages.value
    assertEquals(2, messages.size) // User + Assistant

    val userMessage = messages[0]
    val assistantMessage = messages[1]

    assertEquals(testImageUri.toString(), userMessage.imageUri)
    assertNotNull(assistantMessage.content)
    assertTrue(assistantMessage.content.isNotBlank())

    // 5. Verify vision analysis stored
    assertNotNull(assistantMessage.visionAnalysis)

    println("✅ Multi-modal integration test passed")
}
```

**Acceptance Criteria:**
- [ ] Tests full multi-modal flow (capture → analyze → respond)
- [ ] Validates vision analysis accuracy
- [ ] Verifies AI response generation
- [ ] Confirms data persistence
- [ ] Performance acceptable (<5s end-to-end)

**Tests:**
- [ ] `Phase4Week11IntegrationTest.kt`: `@Test fun multiModal_endToEndFlow()`

**Dependencies:** PHASE4-001 through PHASE4-006

---

## Week 12: Projects (Tickets 011-020)

### PHASE4-011: Project Repository Implementation ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Implement repository for project CRUD operations with SQLDelight.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/data/repository/ProjectRepository.kt`

```kotlin
interface ProjectRepository {
    suspend fun createProject(project: Project): Project
    suspend fun getProjectById(id: String): Project?
    suspend fun getAllProjects(): List<Project>
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun getProjectCount(): Long
}

class ProjectRepositoryImpl(
    private val database: Database
) : ProjectRepository {

    override suspend fun createProject(project: Project): Project {
        return withContext(Dispatchers.IO) {
            database.projectQueries.insert(
                id = project.id,
                name = project.name,
                description = project.description,
                emoji = project.emoji,
                createdAt = project.createdAt,
                updatedAt = project.updatedAt
            )
            project
        }
    }

    override suspend fun getProjectById(id: String): Project? {
        return withContext(Dispatchers.IO) {
            database.projectQueries.getById(id).executeAsOneOrNull()
        }
    }

    override suspend fun getAllProjects(): List<Project> {
        return withContext(Dispatchers.IO) {
            database.projectQueries.getAll().executeAsList()
        }
    }

    override suspend fun updateProject(project: Project) {
        withContext(Dispatchers.IO) {
            database.projectQueries.update(
                id = project.id,
                name = project.name,
                description = project.description,
                emoji = project.emoji,
                updatedAt = Clock.System.now()
            )
        }
    }

    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            database.transaction {
                // Delete associated messages first
                database.messageQueries.deleteByProjectId(id)

                // Delete project
                database.projectQueries.deleteById(id)
            }
        }
    }

    override suspend fun getProjectCount(): Long {
        return withContext(Dispatchers.IO) {
            database.projectQueries.count().executeAsOne()
        }
    }
}
```

**Acceptance Criteria:**
- [ ] ProjectRepository interface defined
- [ ] CRUD operations implemented
- [ ] Cascading delete (messages)
- [ ] Transaction safety
- [ ] Error handling

**Tests:**
- [ ] `ProjectRepositoryTest.kt`: `@Test fun createProject_persistsCorrectly()`
- [ ] `ProjectRepositoryTest.kt`: `@Test fun deleteProject_cascadesMessages()`
- [ ] `ProjectRepositoryTest.kt`: `@Test fun updateProject_updatesTimestamp()`

**Dependencies:** PHASE0-009 (Project table)

**Blocks:** PHASE4-012 (Project ViewModel)

---

### PHASE4-012: Project Management ViewModel

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create ViewModel for managing projects (create, list, switch, delete).

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/viewmodel/ProjectViewModel.kt`

```kotlin
class ProjectViewModel(
    private val projectRepository: ProjectRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            val projects = projectRepository.getAllProjects()
            _projects.value = projects.sortedByDescending { it.updatedAt }

            // Auto-select most recent if none selected
            if (_currentProject.value == null && projects.isNotEmpty()) {
                _currentProject.value = projects.first()
            }
        }
    }

    fun createProject(name: String, description: String, emoji: String) {
        if (name.isBlank()) return

        viewModelScope.launch {
            _isCreating.value = true

            try {
                val project = Project(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    description = description.trim(),
                    emoji = emoji.ifBlank { "💬" },
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now()
                )

                projectRepository.createProject(project)
                loadProjects()
                _currentProject.value = project

            } catch (e: Exception) {
                // Handle error
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch {
            projectRepository.updateProject(project)
            loadProjects()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)

            // Switch to another project if current was deleted
            if (_currentProject.value?.id == projectId) {
                loadProjects()
                _currentProject.value = _projects.value.firstOrNull()
            } else {
                loadProjects()
            }
        }
    }

    fun switchProject(project: Project?) {
        _currentProject.value = project
    }

    suspend fun getProjectMessageCount(projectId: String): Int {
        return messageRepository.getByProjectId(projectId).size
    }
}
```

**Acceptance Criteria:**
- [ ] ProjectViewModel manages project state
- [ ] Create, update, delete operations functional
- [ ] Current project tracking
- [ ] Auto-selects most recent project
- [ ] Handles project switch gracefully

**Tests:**
- [ ] `ProjectViewModelTest.kt`: `@Test fun createProject_addsToList()`
- [ ] `ProjectViewModelTest.kt`: `@Test fun deleteProject_switchesToAnother()`
- [ ] `ProjectViewModelTest.kt`: `@Test fun switchProject_updatesCurrentProject()`

**Dependencies:** PHASE4-011 (Project repository)

**Blocks:** PHASE4-013 (Project browser UI)

---

### PHASE4-013: Project Browser UI

**Priority:** P1 | **Estimated Hours:** 5h | **Status:** [ ]

**Description:**
Create UI for browsing, creating, and managing projects.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/project/ProjectBrowserScreen.kt`

```kotlin
@Composable
fun ProjectBrowserScreen(
    viewModel: ProjectViewModel,
    onProjectSelected: (Project) -> Unit,
    onClose: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "New Project")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(projects) { project ->
                ProjectCard(
                    project = project,
                    isSelected = project.id == currentProject?.id,
                    onClick = {
                        viewModel.switchProject(project)
                        onProjectSelected(project)
                    },
                    onDelete = { viewModel.deleteProject(project.id) },
                    onEdit = { /* TODO: Edit dialog */ }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, emoji ->
                viewModel.createProject(name, description, emoji)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji icon
            Text(
                text = project.emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Project info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium
                )

                if (project.description.isNotBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Updated ${formatRelativeTime(project.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Actions
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, emoji: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💬") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Emoji picker (simplified)
                Text("Choose an emoji:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("💬", "💡", "📱", "🎨", "🚀", "📚").forEach { emojiOption ->
                        FilterChip(
                            selected = emoji == emojiOption,
                            onClick = { emoji = emojiOption },
                            label = { Text(emojiOption, style = MaterialTheme.typography.headlineSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, emoji) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

**Acceptance Criteria:**
- [ ] Project browser lists all projects
- [ ] Create dialog functional with emoji picker
- [ ] Current project highlighted
- [ ] Edit/delete actions work
- [ ] Responsive layout

**Tests:**
- [ ] `ProjectBrowserScreenTest.kt`: `@Composable @Test fun projectList_displays()`
- [ ] `ProjectBrowserScreenTest.kt`: `@Composable @Test fun createDialog_createsProject()`

**Dependencies:** PHASE4-012 (Project ViewModel)

**Blocks:** PHASE4-014 (Project-scoped messages)

---

### PHASE4-014: Project-Scoped Messages ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Filter chat messages by current project so conversations are isolated per project.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/viewmodel/ChatViewModel.kt` (modification)

```kotlin
class ChatViewModel(
    // ... existing dependencies ...
    private val projectViewModel: ProjectViewModel
) : ViewModel() {

    // Watch current project and reload messages
    init {
        viewModelScope.launch {
            projectViewModel.currentProject.collect { project ->
                loadMessagesForProject(project?.id)
            }
        }
    }

    private suspend fun loadMessagesForProject(projectId: String?) {
        val messages = if (projectId != null) {
            messageRepository.getByProjectId(projectId)
        } else {
            // Global project (no filter)
            messageRepository.getAll()
        }

        _messages.value = messages.sortedBy { it.timestamp }
    }

    // sendMessage already assigns projectId from _currentProject.value?.id
}
```

File: `app/shared/src/commonMain/sqldelight/ai/ma/db/Message.sq` (modification)

```sql
-- Add project filtering query
getByProjectId:
SELECT * FROM Message WHERE projectId = ? ORDER BY timestamp ASC;

-- Global messages (null projectId)
getGlobal:
SELECT * FROM Message WHERE projectId IS NULL ORDER BY timestamp ASC;
```

**Acceptance Criteria:**
- [ ] Messages filtered by current project
- [ ] Switch project refreshes message list
- [ ] Global project (null) shows unscoped messages
- [ ] New messages assigned to current project
- [ ] Seamless project switching

**Tests:**
- [ ] `ChatViewModelTest.kt`: `@Test fun switchProject_loadsProjectMessages()`
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_assignsProjectId()`

**Dependencies:**
- PHASE4-012 (Project ViewModel)
- PHASE0-010 (Message table with projectId)

**Blocks:** PHASE4-015 (Project-scoped memory)

---

### PHASE4-015: Project-Scoped Memory Filtering

**Priority:** P1 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Filter memory retrieval by project so AI only recalls context from current project.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/memory/MemoryManager.kt` (modification)

```kotlin
class MemoryManager(
    private val embeddingEngine: CachedEmbeddingEngine,
    private val memoryRepository: MemoryRepository,
    private val semanticChunker: SemanticChunker,
    private val importanceCalculator: ImportanceCalculator
) {
    /**
     * Retrieve memories relevant to query, optionally scoped to project
     *
     * @param query Search query
     * @param topK Number of memories to retrieve
     * @param projectId Optional project scope filter
     * @return Relevant memories sorted by relevance
     */
    suspend fun retrieveMemories(
        query: String,
        topK: Int = 10,
        projectId: String? = null
    ): List<Memory> {
        // 1. Embed query
        val queryEmbedding = embeddingEngine.embed(query)

        // 2. Search vector index
        val candidates = vectorIndex.search(
            query = queryEmbedding,
            k = topK * 3 // Over-retrieve for filtering
        )

        // 3. Load memories from repository
        val memories = memoryRepository.getMemoriesByIds(
            candidates.map { it.id }
        )

        // 4. Filter by project if specified
        val filtered = if (projectId != null) {
            memories.filter { memory ->
                memory.projectId == projectId
            }
        } else {
            memories
        }

        // 5. Re-rank and return top K
        return filtered
            .sortedByDescending { memory ->
                // Combine similarity score with importance
                val similarity = candidates.first { it.id == memory.id }.score
                similarity * memory.importance
            }
            .take(topK)
    }
}
```

File: `app/shared/src/commonMain/kotlin/ai/ma/domain/model/Memory.kt` (modification)

```kotlin
data class Memory(
    val id: String,
    val content: String,
    val embedding: List<Float>,
    val messageId: String,
    val importance: Float,
    val timestamp: Instant,
    val projectId: String? = null // NEW: Project scope
)
```

File: `app/shared/src/commonMain/sqldelight/ai/ma/db/MemoryMetadata.sq` (modification)

```sql
-- Add projectId column
ALTER TABLE MemoryMetadata ADD COLUMN projectId TEXT;

-- Add project filtering
getByProjectId:
SELECT * FROM MemoryMetadata WHERE projectId = ? ORDER BY timestamp DESC;
```

**Acceptance Criteria:**
- [ ] Memory model includes projectId
- [ ] retrieveMemories filters by project
- [ ] Works with global (null) project
- [ ] Vector search respects project scope
- [ ] Existing memories migrated gracefully

**Tests:**
- [ ] `MemoryManagerTest.kt`: `@Test fun retrieveMemories_filtersBy Project()`
- [ ] `MemoryManagerTest.kt`: `@Test fun retrieveMemories_globalProject()`

**Dependencies:**
- PHASE2-011 (Memory manager)
- PHASE4-012 (Project ViewModel)

**Blocks:** PHASE4-016 (RAG project context)

---

### PHASE4-016: RAG with Project Context

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Integrate project-scoped memory retrieval into RAG engine for context-aware responses.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/knowledge/RAGEngine.kt` (modification)

```kotlin
class RAGEngine(
    private val triviaEngine: TriviaEngine,
    private val memoryManager: MemoryManager,
    private val deviceKnowledge: DeviceKnowledgeProvider,
    private val aiEngine: SmolLM2Engine
) {
    suspend fun generateResponse(
        query: String,
        conversationHistory: List<Message>,
        projectId: String? = null // NEW: Project context
    ): RAGResponse {
        val intent = detectIntent(query)

        val retrievedKnowledge = retrieveKnowledge(
            query = query,
            intent = intent,
            projectId = projectId // Pass through to memory retrieval
        )

        // ... rest of implementation ...
    }

    private suspend fun retrieveKnowledge(
        query: String,
        intent: QueryIntent,
        projectId: String? = null
    ): RetrievedKnowledge {
        return when (intent) {
            QueryIntent.QUESTION, QueryIntent.CONVERSATION -> {
                val trivia = triviaEngine.retrieveTrivia(query, topK = 2)
                val memories = memoryManager.retrieveMemories(
                    query = query,
                    topK = 5,
                    projectId = projectId // Project-scoped memory
                )

                RetrievedKnowledge(
                    trivia = trivia,
                    deviceInfo = null,
                    memories = memories
                )
            }

            else -> {
                // ... other intents ...
            }
        }
    }
}
```

**Acceptance Criteria:**
- [ ] RAG accepts projectId parameter
- [ ] Memory retrieval scoped to project
- [ ] Trivia remains global (unscoped)
- [ ] Device knowledge remains global
- [ ] Seamless integration with ChatViewModel

**Tests:**
- [ ] `RAGEngineTest.kt`: `@Test fun generateResponse_usesProjectMemories()`
- [ ] `RAGEngineTest.kt`: `@Test fun generateResponse_globalTrivia()`

**Dependencies:**
- PHASE4-015 (Project-scoped memory)
- PHASE3-006 (RAG engine)

**Blocks:** PHASE4-017 (Project statistics)

---

### PHASE4-017: Project Statistics & Insights

**Priority:** P2 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Calculate project-level statistics (message count, memory count, last activity).

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/domain/model/ProjectStats.kt`

```kotlin
data class ProjectStats(
    val project: Project,
    val messageCount: Int,
    val memoryCount: Int,
    val lastActivity: Instant?,
    val averageResponseTime: Long? = null // ms
)

class ProjectStatsCalculator(
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository
) {
    suspend fun calculateStats(projectId: String): ProjectStats? {
        val project = projectRepository.getProjectById(projectId) ?: return null

        val messages = messageRepository.getByProjectId(projectId)
        val memories = memoryRepository.getByProjectId(projectId)

        return ProjectStats(
            project = project,
            messageCount = messages.size,
            memoryCount = memories.size,
            lastActivity = messages.maxOfOrNull { it.timestamp },
            averageResponseTime = calculateAvgResponseTime(messages)
        )
    }

    private fun calculateAvgResponseTime(messages: List<Message>): Long? {
        if (messages.size < 2) return null

        val responseTimes = mutableListOf<Long>()

        for (i in 0 until messages.size - 1) {
            val userMsg = messages[i]
            val assistantMsg = messages[i + 1]

            if (userMsg.isUser && !assistantMsg.isUser) {
                val diff = (assistantMsg.timestamp - userMsg.timestamp).inWholeMilliseconds
                responseTimes.add(diff)
            }
        }

        return responseTimes.average().toLong()
    }
}
```

**Acceptance Criteria:**
- [ ] ProjectStats data class defined
- [ ] Calculates message/memory counts
- [ ] Tracks last activity
- [ ] Calculates average response time
- [ ] Efficient queries (no N+1)

**Tests:**
- [ ] `ProjectStatsCalculatorTest.kt`: `@Test fun calculateStats_accurate()`
- [ ] `ProjectStatsCalculatorTest.kt`: `@Test fun calculateAvgResponseTime()`

**Dependencies:** PHASE4-011 (Project repository)

---

### PHASE4-018: Export Project Data

**Priority:** P2 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Allow exporting project conversations to JSON for backup/portability.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/export/ProjectExporter.kt`

```kotlin
/**
 * Project Exporter - Exports project data to JSON
 *
 * Exports complete project including messages, memories, and metadata
 * for backup or portability.
 */
class ProjectExporter(
    private val projectRepository: ProjectRepository,
    private val messageRepository: MessageRepository,
    private val memoryRepository: MemoryRepository
) {
    /**
     * Export project to JSON
     *
     * @param projectId Project to export
     * @return JSON string containing complete project data
     */
    suspend fun exportProject(projectId: String): String? {
        val project = projectRepository.getProjectById(projectId) ?: return null
        val messages = messageRepository.getByProjectId(projectId)
        val memories = memoryRepository.getByProjectId(projectId)

        val exportData = ProjectExportData(
            project = project,
            messages = messages,
            memories = memories.map { memory ->
                // Don't export embeddings (too large)
                memory.copy(embedding = emptyList())
            },
            exportedAt = Clock.System.now(),
            version = "1.0"
        )

        return Json.encodeToString(exportData)
    }

    /**
     * Import project from JSON
     *
     * @param json JSON string from previous export
     * @return Imported project ID
     */
    suspend fun importProject(json: String): String {
        val exportData = Json.decodeFromString<ProjectExportData>(json)

        // Generate new IDs to avoid conflicts
        val newProjectId = UUID.randomUUID().toString()
        val idMapping = mutableMapOf<String, String>()

        // Import project
        val newProject = exportData.project.copy(
            id = newProjectId,
            name = "${exportData.project.name} (Imported)",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        projectRepository.createProject(newProject)

        // Import messages with new IDs
        exportData.messages.forEach { message ->
            val newMessageId = UUID.randomUUID().toString()
            idMapping[message.id] = newMessageId

            val newMessage = message.copy(
                id = newMessageId,
                projectId = newProjectId
            )
            messageRepository.createMessage(newMessage)
        }

        // Import memories (will need re-embedding)
        exportData.memories.forEach { memory ->
            val newMemoryId = UUID.randomUUID().toString()
            val newMessageId = idMapping[memory.messageId]

            if (newMessageId != null) {
                // Note: Embeddings will need to be regenerated
                val newMemory = memory.copy(
                    id = newMemoryId,
                    messageId = newMessageId,
                    projectId = newProjectId
                )
                memoryRepository.createMemory(newMemory)
            }
        }

        return newProjectId
    }
}

@Serializable
data class ProjectExportData(
    val project: Project,
    val messages: List<Message>,
    val memories: List<Memory>,
    val exportedAt: Instant,
    val version: String
)
```

**Acceptance Criteria:**
- [ ] Exports complete project to JSON
- [ ] Includes messages and memories
- [ ] Excludes embeddings (size optimization)
- [ ] Import reconstructs project
- [ ] Handles ID remapping

**Tests:**
- [ ] `ProjectExporterTest.kt`: `@Test fun exportProject_validJSON()`
- [ ] `ProjectExporterTest.kt`: `@Test fun importProject_recreatesData()`

**Dependencies:** PHASE4-011 (Project repository)

---

### PHASE4-019: Project UI Navigation

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Add project selector to app navigation bar for quick switching.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/navigation/AppNavigation.kt` (modification)

```kotlin
@Composable
fun ChatScreenWithProjects(
    chatViewModel: ChatViewModel,
    projectViewModel: ProjectViewModel
) {
    val currentProject by projectViewModel.currentProject.collectAsState()
    val projects by projectViewModel.projects.collectAsState()
    var showProjectBrowser by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Project selector chip
                    if (currentProject != null) {
                        FilterChip(
                            selected = false,
                            onClick = { showProjectBrowser = true },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(currentProject!!.emoji)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(currentProject!!.name)
                                    Icon(
                                        Icons.Default.ExpandMore,
                                        contentDescription = "Change project"
                                    )
                                }
                            }
                        )
                    } else {
                        Text("間 AI")
                    }
                },
                actions = {
                    // Menu with project browser option
                    IconButton(onClick = { showProjectBrowser = true }) {
                        Icon(Icons.Default.FolderOpen, "Projects")
                    }
                }
            )
        }
    ) { paddingValues ->
        ChatScreen(
            viewModel = chatViewModel,
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showProjectBrowser) {
        ProjectBrowserScreen(
            viewModel = projectViewModel,
            onProjectSelected = { project ->
                projectViewModel.switchProject(project)
                showProjectBrowser = false
            },
            onClose = { showProjectBrowser = false }
        )
    }
}
```

**Acceptance Criteria:**
- [ ] Current project displayed in top bar
- [ ] Chip opens project browser
- [ ] Project switch updates UI immediately
- [ ] Visual indication of active project
- [ ] Quick access from any screen

**Tests:**
- [ ] `AppNavigationTest.kt`: `@Composable @Test fun projectChip_opensBrowser()`
- [ ] `AppNavigationTest.kt`: `@Composable @Test fun projectSwitch_updatesTopBar()`

**Dependencies:**
- PHASE4-013 (Project browser UI)
- PHASE4-014 (Project-scoped messages)

---

### PHASE4-020: Phase 4 Integration Test ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Comprehensive integration test validating multi-modal and projects working together.

**Implementation:**
File: `app/shared/src/commonTest/kotlin/ai/ma/integration/Phase4IntegrationTest.kt`

```kotlin
@Test
fun phase4Integration_multiModalAndProjects() = runTest {
    // Setup
    val database = createTestDatabase()
    val projectRepository = ProjectRepositoryImpl(database)
    val messageRepository = MessageRepositoryImpl(database)
    val memoryRepository = MemoryRepositoryImpl(database)

    val projectViewModel = ProjectViewModel(projectRepository, messageRepository)
    val context = createTestContext()
    val visionAnalyzer = VisionAnalyzer(context)
    val multiModalEngine = createTestMultiModalEngine(visionAnalyzer)
    val chatViewModel = createTestChatViewModel(
        multiModalEngine = multiModalEngine,
        projectViewModel = projectViewModel
    )

    // Test 1: Create project
    projectViewModel.createProject(
        name = "Test Project",
        description = "Integration test project",
        emoji = "🧪"
    )
    advanceUntilIdle()

    val projects = projectViewModel.projects.value
    assertEquals(1, projects.size)
    val testProject = projects.first()
    assertEquals("Test Project", testProject.name)

    // Test 2: Send text message in project
    chatViewModel.sendMessage("Hello from test project")
    advanceUntilIdle()

    var messages = chatViewModel.messages.value
    assertTrue(messages.isNotEmpty())
    assertEquals(testProject.id, messages.first().projectId)

    // Test 3: Send image message in project
    val testImageUri = loadTestImage("sample.jpg")
    chatViewModel.sendMessage("What's in this image?", imageUri = testImageUri)
    advanceUntilIdle()

    messages = chatViewModel.messages.value
    assertTrue(messages.any { it.imageUri != null })

    // Test 4: Switch to new project
    projectViewModel.createProject(
        name = "Second Project",
        description = "",
        emoji = "📝"
    )
    advanceUntilIdle()

    val secondProject = projectViewModel.projects.value.first { it.name == "Second Project" }
    projectViewModel.switchProject(secondProject)
    advanceUntilIdle()

    // Messages should be empty (different project)
    messages = chatViewModel.messages.value
    assertTrue(messages.isEmpty(), "Messages should be scoped to project")

    // Test 5: Send message in second project
    chatViewModel.sendMessage("Message in second project")
    advanceUntilIdle()

    messages = chatViewModel.messages.value
    assertEquals(2, messages.size) // User + Assistant
    assertEquals(secondProject.id, messages.first().projectId)

    // Test 6: Switch back to first project
    projectViewModel.switchProject(testProject)
    advanceUntilIdle()

    messages = chatViewModel.messages.value
    assertTrue(messages.size > 2, "First project should have original messages")
    assertTrue(messages.all { it.projectId == testProject.id })

    // Test 7: Memory scoping
    val memories = memoryRepository.getByProjectId(testProject.id)
    assertTrue(memories.isNotEmpty(), "Project should have memories")
    assertTrue(memories.all { it.projectId == testProject.id })

    // Test 8: Delete project
    projectViewModel.deleteProject(secondProject.id)
    advanceUntilIdle()

    val remainingProjects = projectViewModel.projects.value
    assertEquals(1, remainingProjects.size)
    assertFalse(remainingProjects.any { it.id == secondProject.id })

    println("✅ Phase 4 integration test passed")
}

@Test
fun multiModalPerformance_acceptableLimits() = runTest {
    val context = createTestContext()
    val visionAnalyzer = VisionAnalyzer(context)

    val testImageUri = loadTestImage("test_document.jpg")

    // Measure vision analysis time
    val start = System.currentTimeMillis()
    val analysis = visionAnalyzer.analyzeImage(testImageUri)
    val duration = System.currentTimeMillis() - start

    println("Vision analysis time: ${duration}ms")

    // Assert performance targets
    assertTrue(duration < 3000, "Vision analysis should be <3s")
    assertTrue(analysis.labels.isNotEmpty(), "Should detect labels")

    // If text in image, should recognize it
    if (analysis.text.fullText.isNotBlank()) {
        assertTrue(analysis.text.blocks.isNotEmpty())
    }

    println("✅ Multi-modal performance test passed")
}
```

**Acceptance Criteria:**
- [ ] Tests full project lifecycle (create, switch, delete)
- [ ] Validates project-scoped messages
- [ ] Tests multi-modal in project context
- [ ] Verifies memory scoping
- [ ] Performance within targets (<3s vision analysis)
- [ ] All assertions pass

**Tests:**
- [ ] `Phase4IntegrationTest.kt`: `@Test fun phase4Integration_multiModalAndProjects()`
- [ ] `Phase4IntegrationTest.kt`: `@Test fun multiModalPerformance_acceptableLimits()`

**Dependencies:** All Phase 4 tickets

**Blocks:** Phase 5 start

---

## Phase 4 Summary

### Tickets by Priority
- **P0 (Critical):** 6 tickets (001, 002, 003, 006, 011, 012, 020)
- **P1 (Important):** 8 tickets (004, 005, 007, 008, 013, 015, 016, 019)
- **P2 (Enhancement):** 6 tickets (009, 010, 014, 017, 018)

### Key Deliverables
1. ✅ Multi-modal support (CameraX + ML Kit)
2. ✅ Image analysis (OCR, object detection, labels)
3. ✅ AI image understanding with vision context
4. ✅ Project management CRUD
5. ✅ Project-scoped conversations and memories
6. ✅ Project browser UI with emoji picker
7. ✅ Project statistics and export

### Testing Requirements
- **Unit tests:** 40+ tests across multi-modal and project components
- **Integration tests:** Phase 4 end-to-end validation
- **Performance:** Vision analysis <3s, project switching instant

### Documentation
- None required (functionality self-explanatory)

---

**Next Phase:** [Phase 5: Advanced Features & Polish](PHASE5.md) (30 tickets, Weeks 13-15)
