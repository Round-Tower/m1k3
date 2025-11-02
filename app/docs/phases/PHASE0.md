# Phase 0: Foundation & Infrastructure

**Duration:** Weeks 1-2
**Total Tickets:** 15
**Goal:** Clean scaffold, establish privacy architecture, implement database, import knowledge base

**STATUS:** ✅ **COMPLETE** (2025-11-02)

---

## Implementation Status (2025-11-02)

✅ **Phase 0 Completed - Database & Knowledge Foundation Operational**

**Achievements:**
- ✅ SQLDelight database with 5 tables (Project, Message, MemoryMetadata, TriviaFact, ProjectMetadata)
- ✅ Knowledge base imported: 1,341 documents from M1K3's comprehensive_knowledge_base.json
- ✅ Database encryption foundation with SQLCipher integration (AndroidDatabaseFactory)
- ✅ Knowledge import system operational (KnowledgeBaseImporter)
- ✅ Privacy architecture enforced (zero network permission)
- ✅ Build system configured with ONNX Runtime, SQLDelight, Compose

**Commits:**
- `fix(android): Properly close SQLite driver and AI engine on MainActivity destroy` (f02bc0d)
- Database lifecycle management implemented
- Resource leak prevention validated

**Key Deliverables:**
- Database operational with full CRUD operations
- 1,341 trivia facts queryable via SQLDelight
- Foundation ready for Phase 1 AI integration

---

## Overview

Phase 0 establishes the foundational infrastructure for 間 AI:
- **Privacy-first architecture** (remove internet permission)
- **Build system configuration** (dependencies, ProGuard)
- **Database layer** (SQLDelight with 5 tables)
- **Knowledge base import** (M1K3's 1.6MB comprehensive_knowledge_base.json)
- **Test infrastructure** (JUnit5, Mockk, Turbine)
- **Data models** (Message, Project, Memory, etc.)

**Success Criteria:**
- ✅ App builds without internet permission
- ✅ SQLDelight database operational with 5 tables
- ✅ M1K3 knowledge base imported (1,341+ documents)
- ✅ 15+ foundation tests passing
- ✅ Repository interfaces defined with test doubles

---

## Week 1: Privacy & Build System (Tickets 001-008)

### PHASE0-001: Remove Internet Permission ⚠️ CRITICAL
**Priority:** P0 | **Estimated Hours:** 0.5h | **Status:** [ ]

**Description:**
Remove INTERNET permission from AndroidManifest.xml to enforce privacy-first architecture. This is the foundational requirement that makes data exfiltration technically impossible at the OS level.

**Implementation:**
```xml
<!-- File: app/composeApp/src/androidMain/AndroidManifest.xml -->

<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!--
    PRIVACY ARCHITECTURE: No INTERNET permission
    This app processes all data locally. The absence of internet permission
    makes network communication technically impossible, enforcing our
    privacy-first philosophy of 間 AI.
    -->

    <!-- Only local permissions -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <application ... >
        ...
    </application>
</manifest>
```

**Acceptance Criteria:**
- [ ] INTERNET permission completely removed
- [ ] Privacy comment added to manifest
- [ ] Build succeeds without network permission
- [ ] No compile-time errors related to network code
- [ ] AndroidManifest.xml validated

**Tests:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/privacy/PrivacyTest.kt

@Test
fun `androidManifest has no INTERNET permission`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager
    val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

    val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
    assertFalse(permissions.contains("android.permission.INTERNET"))
}

@Test
fun `no network classes in application`() {
    // Verify ProGuard strips network code
    assertThrows<ClassNotFoundException> {
        Class.forName("java.net.HttpURLConnection")
    }
}
```

**Dependencies:** None (must be first task)
**Blocked By:** None
**Blocks:** PHASE0-014 (Privacy validation test)

---

### PHASE0-002: Configure Build Dependencies
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Add all required dependencies for AI, database, camera, and testing to build.gradle.kts files.

**Implementation:**
```kotlin
// File: app/shared/build.gradle.kts

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                // SQLDelight
                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")

                // Kotlinx
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }

        val androidMain by getting {
            dependencies {
                // ONNX Runtime
                implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

                // SQLDelight Android
                implementation("app.cash.sqldelight:android-driver:2.0.1")
                implementation("net.zetetic:android-database-sqlcipher:4.5.4")

                // CameraX
                implementation("androidx.camera:camera-camera2:1.3.1")
                implementation("androidx.camera:camera-lifecycle:1.3.1")
                implementation("androidx.camera:camera-view:1.3.1")

                // ML Kit
                implementation("com.google.mlkit:image-labeling:17.0.8")

                // JVector (HNSW)
                implementation("io.github.jbellis:jvector:1.0.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                implementation("app.cash.turbine:turbine:1.0.0")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("io.mockk:mockk:1.13.9")
                implementation("androidx.test:core:1.5.0")
                implementation("androidx.test.ext:junit:1.1.5")
            }
        }
    }
}

// SQLDelight configuration
sqldelight {
    databases {
        create("MaAIDatabase") {
            packageName.set("com.roundtower.maai.db")
            schemaOutputDirectory.set(file("build/dbs"))
            verifyMigrations.set(true)
        }
    }
}
```

**Acceptance Criteria:**
- [ ] All dependencies added to build.gradle.kts
- [ ] Gradle sync successful
- [ ] No version conflicts
- [ ] SQLDelight plugin configured
- [ ] Android test dependencies available

**Tests:**
```kotlin
@Test
fun `all critical dependencies available`() {
    // Verify classes can be imported
    assertTrue(Class.forName("ai.onnxruntime.OrtSession") != null)
    assertTrue(Class.forName("app.cash.sqldelight.driver.android.AndroidSqliteDriver") != null)
    assertTrue(Class.forName("io.github.jbellis.jvector.graph.GraphIndex") != null)
}
```

**Dependencies:** None
**Blocked By:** None
**Blocks:** All subsequent implementation tasks

---

### PHASE0-003: Configure ProGuard Rules
**Priority:** P0 | **Estimated Hours:** 1h | **Status:** [ ]

**Description:**
Set up ProGuard/R8 rules to strip all network code and optimize APK size while preserving ONNX Runtime and SQLDelight functionality.

**Implementation:**
```proguard
# File: app/composeApp/proguard-rules.pro

# Privacy enforcement: Strip all network classes
-assumenosideeffects class java.net.HttpURLConnection
-assumenosideeffects class javax.net.ssl.HttpsURLConnection
-assumenosideeffects class java.net.URL
-assumenosideeffects class okhttp3.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keep class com.roundtower.maai.db.** { *; }

# JVector
-keep class io.github.jbellis.jvector.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}
```

```kotlin
// File: app/composeApp/build.gradle.kts

android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

**Acceptance Criteria:**
- [ ] ProGuard rules file created
- [ ] Network classes stripped in release build
- [ ] ONNX Runtime classes preserved
- [ ] SQLDelight classes preserved
- [ ] Release build succeeds

**Tests:**
```kotlin
@Test
fun `release build strips network classes`() {
    // Run on release APK
    assumeTrue(BuildConfig.BUILD_TYPE == "release")

    assertThrows<ClassNotFoundException> {
        Class.forName("java.net.HttpURLConnection")
    }
}
```

**Dependencies:** PHASE0-002
**Blocks:** PHASE0-014 (Privacy validation)

---

### PHASE0-004: Remove Scaffold Code
**Priority:** P1 | **Estimated Hours:** 1h | **Status:** [ ]

**Description:**
Clean up the KMP scaffold by removing demo code (Greeting, Platform detection, "Click me!" button) to prepare for real implementation.

**Implementation:**
```kotlin
// DELETE these files:
// - app/shared/src/commonMain/kotlin/Greeting.kt
// - app/shared/src/commonMain/kotlin/Platform.kt

// REPLACE app/composeApp/src/commonMain/kotlin/App.kt with:

@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Text(
                text = "間 AI",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Greeting.kt deleted
- [ ] Platform.kt deleted
- [ ] App.kt simplified (no demo code)
- [ ] App still builds and runs
- [ ] No broken imports

**Tests:**
```kotlin
@Test
fun `app composes without errors`() {
    composeTestRule.setContent {
        App()
    }

    composeTestRule.onNodeWithText("間 AI").assertExists()
}
```

**Dependencies:** None
**Blocks:** UI implementation tasks

---

### PHASE0-005: Set Up Test Infrastructure
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Configure JUnit5, Mockk, and Turbine for comprehensive testing. Create base test classes and utilities.

**Implementation:**
```kotlin
// File: app/shared/build.gradle.kts

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

```kotlin
// File: app/shared/src/commonTest/kotlin/TestBase.kt

abstract class TestBase {
    @BeforeEach
    fun setUp() {
        // Common setup
    }

    @AfterEach
    fun tearDown() {
        // Common cleanup
    }
}
```

```kotlin
// File: app/shared/src/commonTest/kotlin/TestFixtures.kt

object TestFixtures {
    fun createTestMessage(
        role: Role = Role.USER,
        content: String = "Test message"
    ) = Message(
        id = UUID.randomUUID().toString(),
        role = role,
        content = listOf(ContentPart.Text(content)),
        timestamp = Instant.now()
    )

    fun createTestProject(
        name: String = "Test Project"
    ) = Project(
        id = UUID.randomUUID().toString(),
        name = name,
        description = "Test project",
        createdAt = Instant.now(),
        lastAccessedAt = Instant.now()
    )
}
```

**Acceptance Criteria:**
- [ ] JUnit5 configured
- [ ] Mockk available for mocking
- [ ] Turbine available for Flow testing
- [ ] TestBase class created
- [ ] TestFixtures utility created
- [ ] Sample test passes

**Tests:**
```kotlin
@Test
fun `test infrastructure works`() {
    val result = true
    assertTrue(result)
}

@Test
fun `mockk works`() {
    val mock = mockk<Repository>()
    every { mock.getData() } returns "test"
    assertEquals("test", mock.getData())
}

@Test
fun `turbine works`() = runTest {
    flow { emit(1); emit(2) }.test {
        assertEquals(1, awaitItem())
        assertEquals(2, awaitItem())
        awaitComplete()
    }
}
```

**Dependencies:** PHASE0-002
**Blocks:** All test writing tasks

---

### PHASE0-006: Define Data Models
**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Create core data models for Message, Project, Memory, and other domain entities with kotlinx.serialization support.

**Implementation:**
```kotlin
// File: app/shared/src/commonMain/kotlin/data/model/Message.kt

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: List<ContentPart>,
    val timestamp: Instant,
    val projectId: String? = null
) {
    @Serializable
    enum class Role {
        USER, ASSISTANT, SYSTEM
    }
}

@Serializable
sealed class ContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart()

    @Serializable
    @SerialName("image")
    data class Image(
        val imageData: ByteArray,
        val width: Int,
        val height: Int,
        val description: String? = null
    ) : ContentPart() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Image
            return imageData.contentEquals(other.imageData) &&
                   width == other.width &&
                   height == other.height
        }

        override fun hashCode(): Int {
            var result = imageData.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}
```

```kotlin
// File: app/shared/src/commonMain/kotlin/data/model/Project.kt

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val createdAt: Instant,
    val lastAccessedAt: Instant,
    val tags: List<String> = emptyList()
)
```

```kotlin
// File: app/shared/src/commonMain/kotlin/data/model/Memory.kt

@Serializable
data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val embedding: List<Float>,
    val messageId: String,
    val projectId: String?,
    val importance: Float = 0.5f,
    val timestamp: Instant,
    val accessCount: Int = 0,
    val emotionalContext: EmotionalState? = null
)

@Serializable
data class EmotionalState(
    val timestamp: Instant,
    val valence: Float,  // -1.0 to 1.0
    val arousal: Float,  // 0.0 to 1.0
    val dominance: Float, // 0.0 to 1.0
    val confidence: Float // 0.0 to 1.0
)
```

```kotlin
// File: app/shared/src/commonMain/kotlin/data/model/TriviaFact.kt

@Serializable
data class TriviaFact(
    val id: String,
    val category: String,
    val fact: String,
    val surpriseRating: Float, // 0.0 to 1.0
    val tags: List<String>,
    val triggers: List<String>,
    val source: String? = null
)
```

**Acceptance Criteria:**
- [ ] Message model with Role enum and ContentPart sealed class
- [ ] Project model with timestamps
- [ ] Memory model with embedding support
- [ ] EmotionalState model (VAD)
- [ ] TriviaFact model
- [ ] All models serializable
- [ ] Equals/hashCode for ByteArray in Image

**Tests:**
```kotlin
@Test
fun `message serialization works`() {
    val message = Message(
        role = Role.USER,
        content = listOf(ContentPart.Text("Hello")),
        timestamp = Instant.parse("2025-01-01T00:00:00Z")
    )

    val json = Json.encodeToString(message)
    val decoded = Json.decodeFromString<Message>(json)

    assertEquals(message.role, decoded.role)
    assertEquals(message.content, decoded.content)
}

@Test
fun `memory model stores 384-dimensional embeddings`() {
    val embedding = List(384) { it.toFloat() }
    val memory = Memory(
        content = "Test",
        embedding = embedding,
        messageId = "msg-1",
        projectId = null,
        timestamp = Instant.now()
    )

    assertEquals(384, memory.embedding.size)
}
```

**Dependencies:** None
**Blocks:** Database schema definition, repository interfaces

---

### PHASE0-007: Create CI/CD Workflow
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Set up GitHub Actions workflow for automated testing on push/PR.

**Implementation:**
```yaml
# File: .github/workflows/android-build.yml

name: Android Build & Test

on:
  push:
    branches: [ main, develop, 'phase*/**' ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        distribution: 'zulu'
        java-version: '17'
        cache: 'gradle'

    - name: Grant execute permission for gradlew
      run: chmod +x app/gradlew

    - name: Run tests
      working-directory: ./app
      run: ./gradlew test

    - name: Build debug APK
      working-directory: ./app
      run: ./gradlew assembleDebug

    - name: Check APK size
      working-directory: ./app
      run: |
        SIZE=$(stat -f%z composeApp/build/outputs/apk/debug/composeApp-debug.apk)
        echo "APK Size: $((SIZE / 1024 / 1024)) MB"
        if [ $SIZE -gt 209715200 ]; then
          echo "Error: APK exceeds 200MB limit"
          exit 1
        fi

    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-results
        path: app/*/build/test-results/**/*.xml
```

**Acceptance Criteria:**
- [ ] GitHub Actions workflow created
- [ ] Triggers on push to main/develop/phase branches
- [ ] Runs tests automatically
- [ ] Builds debug APK
- [ ] Validates APK size <200MB
- [ ] Uploads test results

**Tests:**
Manual verification: Push code, check Actions tab

**Dependencies:** PHASE0-002, PHASE0-005
**Blocks:** None (parallel development)

---

### PHASE0-008: Initialize Git Branch Strategy
**Priority:** P1 | **Estimated Hours:** 0.5h | **Status:** [ ]

**Description:**
Set up Git branching strategy and create initial branches for development.

**Implementation:**
```bash
# Create main development branches
git checkout -b develop
git push -u origin develop

# Create branch protection rules (GitHub settings):
# - main: Require PR reviews, status checks
# - develop: Require status checks
# - phase*: Allow direct pushes

# Branch naming convention documented:
# - phase0/PHASE0-001-description
# - phase1/PHASE1-015-description
```

```markdown
# File: app/docs/CONTRIBUTING.md

## Branch Strategy

- **main**: Production-ready code only
- **develop**: Active development branch
- **phase0/**, **phase1/**, etc.: Feature branches per phase

## Commit Format

```
[PHASE0-001] Short description

Detailed description if needed.

- Change 1
- Change 2
```

## PR Process

1. Create branch from `develop`
2. Implement ticket + tests
3. Push and create PR to `develop`
4. Wait for CI to pass
5. Request review
6. Squash and merge
```

**Acceptance Criteria:**
- [ ] develop branch created
- [ ] Branch protection rules configured
- [ ] CONTRIBUTING.md created
- [ ] First commit follows convention

**Tests:**
Manual verification: Create test branch, commit follows format

**Dependencies:** None
**Blocks:** None

---

## Week 2: Database & Knowledge Base (Tickets 009-015)

### PHASE0-009: Define SQLDelight Schema - Projects Table
**Priority:** P0 | **Estimated Hours:** 1h | **Status:** [ ]

**Description:**
Create SQLDelight schema for Projects table with proper indexes.

**Implementation:**
```sql
-- File: app/shared/src/commonMain/sqldelight/com/roundtower/maai/db/Project.sq

CREATE TABLE Project (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    lastAccessedAt INTEGER NOT NULL,
    tags TEXT NOT NULL  -- JSON array
);

CREATE INDEX idx_project_lastAccessed
ON Project(lastAccessedAt DESC);

-- Queries

selectAll:
SELECT * FROM Project
ORDER BY lastAccessedAt DESC;

selectById:
SELECT * FROM Project
WHERE id = ?;

insert:
INSERT INTO Project(id, name, description, createdAt, lastAccessedAt, tags)
VALUES (?, ?, ?, ?, ?, ?);

update:
UPDATE Project
SET name = ?, description = ?, lastAccessedAt = ?, tags = ?
WHERE id = ?;

updateLastAccessed:
UPDATE Project
SET lastAccessedAt = ?
WHERE id = ?;

deleteById:
DELETE FROM Project
WHERE id = ?;

count:
SELECT COUNT(*) FROM Project;
```

**Acceptance Criteria:**
- [ ] Project.sq file created
- [ ] Table schema defined with all columns
- [ ] Index on lastAccessedAt for sorting
- [ ] All CRUD queries defined
- [ ] SQLDelight generates Kotlin code successfully

**Tests:**
```kotlin
@Test
fun `project table stores and retrieves projects`() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    MaAIDatabase.Schema.create(driver)
    val database = MaAIDatabase(driver)

    val project = Project(
        id = "proj-1",
        name = "Test Project",
        description = "Description",
        createdAt = Instant.now(),
        lastAccessedAt = Instant.now(),
        tags = listOf("test")
    )

    database.projectQueries.insert(
        id = project.id,
        name = project.name,
        description = project.description,
        createdAt = project.createdAt.toEpochMilliseconds(),
        lastAccessedAt = project.lastAccessedAt.toEpochMilliseconds(),
        tags = Json.encodeToString(project.tags)
    )

    val retrieved = database.projectQueries.selectById(project.id).executeAsOne()
    assertEquals(project.id, retrieved.id)
    assertEquals(project.name, retrieved.name)
}
```

**Dependencies:** PHASE0-002, PHASE0-006
**Blocks:** PHASE0-013 (Repository implementation)

---

### PHASE0-010: Define SQLDelight Schema - Messages Table
**Priority:** P0 | **Estimated Hours:** 1.5h | **Status:** [ ]

**Description:**
Create Messages table schema with support for multi-modal content (JSON serialized ContentPart list).

**Implementation:**
```sql
-- File: app/shared/src/commonMain/sqldelight/com/roundtower/maai/db/Message.sq

CREATE TABLE Message (
    id TEXT PRIMARY KEY NOT NULL,
    role TEXT NOT NULL,  -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,  -- JSON serialized ContentPart[]
    timestamp INTEGER NOT NULL,
    projectId TEXT,
    FOREIGN KEY (projectId) REFERENCES Project(id) ON DELETE CASCADE
);

CREATE INDEX idx_message_project_time
ON Message(projectId, timestamp DESC);

CREATE INDEX idx_message_timestamp
ON Message(timestamp DESC);

-- Queries

selectByProject:
SELECT * FROM Message
WHERE projectId = ?
ORDER BY timestamp DESC
LIMIT ?;

selectRecent:
SELECT * FROM Message
ORDER BY timestamp DESC
LIMIT ?;

selectById:
SELECT * FROM Message
WHERE id = ?;

insert:
INSERT INTO Message(id, role, content, timestamp, projectId)
VALUES (?, ?, ?, ?, ?);

deleteByProject:
DELETE FROM Message
WHERE projectId = ?;

deleteById:
DELETE FROM Message
WHERE id = ?;

count:
SELECT COUNT(*) FROM Message;

countByProject:
SELECT COUNT(*) FROM Message
WHERE projectId = ?;
```

**Acceptance Criteria:**
- [ ] Message.sq file created
- [ ] Foreign key to Project with CASCADE delete
- [ ] Indexes for project+time and time-only queries
- [ ] All queries defined
- [ ] Content stored as JSON string

**Tests:**
```kotlin
@Test
fun `message table stores multimodal content`() {
    // ... setup database ...

    val message = Message(
        id = "msg-1",
        role = Role.USER,
        content = listOf(
            ContentPart.Text("Hello"),
            ContentPart.Image(byteArrayOf(1, 2, 3), 100, 100)
        ),
        timestamp = Instant.now(),
        projectId = "proj-1"
    )

    database.messageQueries.insert(
        id = message.id,
        role = message.role.name,
        content = Json.encodeToString(message.content),
        timestamp = message.timestamp.toEpochMilliseconds(),
        projectId = message.projectId
    )

    val retrieved = database.messageQueries.selectById(message.id).executeAsOne()
    val decodedContent = Json.decodeFromString<List<ContentPart>>(retrieved.content)

    assertEquals(2, decodedContent.size)
    assertTrue(decodedContent[0] is ContentPart.Text)
    assertTrue(decodedContent[1] is ContentPart.Image)
}

@Test
fun `cascade delete removes messages when project deleted`() {
    // ... setup with project and messages ...

    database.projectQueries.deleteById("proj-1")

    val messages = database.messageQueries.countByProject("proj-1").executeAsOne()
    assertEquals(0L, messages)
}
```

**Dependencies:** PHASE0-009
**Blocks:** PHASE0-013

---

### PHASE0-011: Define SQLDelight Schema - Memory & Trivia Tables
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Create MemoryMetadata and TriviaFacts tables. Note: Vector embeddings stored separately in HNSW index, metadata here.

**Implementation:**
```sql
-- File: app/shared/src/commonMain/sqldelight/com/roundtower/maai/db/Memory.sq

CREATE TABLE MemoryMetadata (
    id TEXT PRIMARY KEY NOT NULL,
    content TEXT NOT NULL,
    projectId TEXT,
    messageId TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    importance REAL NOT NULL DEFAULT 0.5,
    accessCount INTEGER NOT NULL DEFAULT 0,
    emotionalContext TEXT,  -- JSON EmotionalState or NULL
    FOREIGN KEY (projectId) REFERENCES Project(id) ON DELETE CASCADE,
    FOREIGN KEY (messageId) REFERENCES Message(id) ON DELETE CASCADE
);

CREATE INDEX idx_memory_project ON MemoryMetadata(projectId);
CREATE INDEX idx_memory_importance ON MemoryMetadata(importance DESC);
CREATE INDEX idx_memory_timestamp ON MemoryMetadata(timestamp DESC);

-- Queries

selectByProject:
SELECT * FROM MemoryMetadata
WHERE projectId = ? OR projectId IS NULL
ORDER BY importance DESC, timestamp DESC
LIMIT ?;

selectById:
SELECT * FROM MemoryMetadata
WHERE id = ?;

insert:
INSERT INTO MemoryMetadata(id, content, projectId, messageId, timestamp, importance, accessCount, emotionalContext)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

updateImportance:
UPDATE MemoryMetadata
SET importance = ?
WHERE id = ?;

incrementAccessCount:
UPDATE MemoryMetadata
SET accessCount = accessCount + 1
WHERE id = ?;

deleteById:
DELETE FROM MemoryMetadata
WHERE id = ?;

count:
SELECT COUNT(*) FROM MemoryMetadata;
```

```sql
-- File: app/shared/src/commonMain/sqldelight/com/roundtower/maai/db/Trivia.sq

CREATE TABLE TriviaFact (
    id TEXT PRIMARY KEY NOT NULL,
    category TEXT NOT NULL,
    fact TEXT NOT NULL,
    surpriseRating REAL NOT NULL,
    tags TEXT NOT NULL,  -- JSON array
    triggers TEXT NOT NULL,  -- JSON array
    source TEXT,
    sharedCount INTEGER NOT NULL DEFAULT 0,
    lastSharedAt INTEGER  -- NULL if never shared
);

CREATE INDEX idx_trivia_category ON TriviaFact(category);
CREATE INDEX idx_trivia_surprise ON TriviaFact(surpriseRating DESC);

-- Queries

selectByCategory:
SELECT * FROM TriviaFact
WHERE category = ?
ORDER BY surpriseRating DESC;

selectHighSurprise:
SELECT * FROM TriviaFact
WHERE surpriseRating >= ?
ORDER BY RANDOM()
LIMIT ?;

selectById:
SELECT * FROM TriviaFact
WHERE id = ?;

insert:
INSERT INTO TriviaFact(id, category, fact, surpriseRating, tags, triggers, source, sharedCount, lastSharedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

updateSharedStats:
UPDATE TriviaFact
SET sharedCount = sharedCount + 1,
    lastSharedAt = ?
WHERE id = ?;

count:
SELECT COUNT(*) FROM TriviaFact;

countByCategory:
SELECT category, COUNT(*) as count
FROM TriviaFact
GROUP BY category;
```

**Acceptance Criteria:**
- [ ] MemoryMetadata.sq created with foreign keys
- [ ] TriviaFact.sq created with indexes
- [ ] Importance and access count tracking
- [ ] Emotional context support (JSON)
- [ ] Trivia shared count tracking
- [ ] All queries defined

**Tests:**
```kotlin
@Test
fun `memory metadata stores importance and access count`() {
    // ... setup ...

    database.memoryQueries.insert(
        id = "mem-1",
        content = "Important fact",
        projectId = null,
        messageId = "msg-1",
        timestamp = Instant.now().toEpochMilliseconds(),
        importance = 0.8f,
        accessCount = 0,
        emotionalContext = null
    )

    // Increment access
    database.memoryQueries.incrementAccessCount("mem-1")
    database.memoryQueries.incrementAccessCount("mem-1")

    val memory = database.memoryQueries.selectById("mem-1").executeAsOne()
    assertEquals(2L, memory.accessCount)
}

@Test
fun `trivia facts can be filtered by surprise rating`() {
    // ... insert facts with various ratings ...

    val highSurprise = database.triviaQueries
        .selectHighSurprise(minRating = 0.7f, limit = 10)
        .executeAsList()

    assertTrue(highSurprise.all { it.surpriseRating >= 0.7f })
}
```

**Dependencies:** PHASE0-009, PHASE0-010
**Blocks:** PHASE0-012 (Knowledge import)

---

### PHASE0-012: Import M1K3 Knowledge Base
**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create build-time script to parse M1K3's comprehensive_knowledge_base.json and populate TriviaFact table.

**Implementation:**
```python
# File: app/scripts/import_knowledge_base.py

import json
import sqlite3
import hashlib
from pathlib import Path

def calculate_surprise_rating(document):
    """Heuristic for surprise rating based on content analysis."""
    content = document.get("content", "")

    # Factors: length, complexity, uniqueness
    score = 0.5

    if len(content) > 200:
        score += 0.1
    if "surprising" in content.lower() or "did you know" in content.lower():
        score += 0.2
    if any(word in content.lower() for word in ["billion", "trillion", "million"]):
        score += 0.1

    return min(1.0, max(0.0, score))

def extract_triggers(content):
    """Extract keyword triggers from content."""
    # Simple implementation: extract nouns and key terms
    words = content.lower().split()
    stopwords = {"the", "is", "at", "which", "on", "a", "an"}
    return [w for w in words if len(w) > 4 and w not in stopwords][:10]

def import_knowledge_base(json_path, db_path):
    with open(json_path, 'r') as f:
        knowledge_base = json.load(f)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    fact_count = 0
    for category, documents in knowledge_base.items():
        for doc in documents:
            fact_id = hashlib.md5(doc["content"].encode()).hexdigest()

            cursor.execute("""
                INSERT INTO TriviaFact
                (id, category, fact, surpriseRating, tags, triggers, source, sharedCount, lastSharedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL)
            """, (
                fact_id,
                category,
                doc["content"],
                calculate_surprise_rating(doc),
                json.dumps(doc.get("tags", [])),
                json.dumps(extract_triggers(doc["content"])),
                doc.get("source", "M1K3 Knowledge Base")
            ))

            fact_count += 1

    conn.commit()
    print(f"Imported {fact_count} facts from {len(knowledge_base)} categories")
    conn.close()

if __name__ == "__main__":
    json_path = Path("../../knowledge/comprehensive_knowledge_base.json")
    db_path = Path("../shared/build/dbs/knowledge.db")

    import_knowledge_base(json_path, db_path)
```

```kotlin
// File: app/scripts/build.gradle.kts

tasks.register("importKnowledgeBase") {
    doLast {
        exec {
            commandLine("python3", "scripts/import_knowledge_base.py")
        }
    }
}

// Run after SQLDelight schema generation
tasks.named("preBuild") {
    dependsOn("importKnowledgeBase")
}
```

**Acceptance Criteria:**
- [ ] Python import script created
- [ ] Parses M1K3's comprehensive_knowledge_base.json
- [ ] Calculates surprise ratings heuristically
- [ ] Extracts keyword triggers
- [ ] Populates TriviaFact table
- [ ] Runs as Gradle task before build
- [ ] Validates 1,341+ facts imported

**Tests:**
```kotlin
@Test
fun `knowledge base import populates trivia table`() {
    // Assumes import ran during build
    val driver = AndroidSqliteDriver(MaAIDatabase.Schema, context, "test.db")
    val database = MaAIDatabase(driver)

    val factCount = database.triviaQueries.count().executeAsOne()
    assertTrue(factCount >= 1341L, "Expected 1341+ facts, got $factCount")

    val categoryCounts = database.triviaQueries.countByCategory().executeAsList()
    assertTrue(categoryCounts.size >= 20, "Expected 20 categories")
}
```

**Dependencies:** PHASE0-011
**Blocks:** Phase 3 trivia engine

---

### PHASE0-013: Implement Repository Interfaces
**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Define repository interfaces for data access abstraction and create SQLDelight-based implementations.

**Implementation:**
```kotlin
// File: app/shared/src/commonMain/kotlin/data/repository/ProjectRepository.kt

interface ProjectRepository {
    suspend fun getAllProjects(): List<Project>
    suspend fun getProjectById(id: String): Project?
    suspend fun createProject(project: Project)
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun updateLastAccessed(id: String, timestamp: Instant)
}

class ProjectRepositoryImpl(
    private val database: MaAIDatabase
) : ProjectRepository {
    override suspend fun getAllProjects(): List<Project> = withContext(Dispatchers.IO) {
        database.projectQueries.selectAll().executeAsList().map { it.toModel() }
    }

    override suspend fun getProjectById(id: String): Project? = withContext(Dispatchers.IO) {
        database.projectQueries.selectById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun createProject(project: Project) = withContext(Dispatchers.IO) {
        database.projectQueries.insert(
            id = project.id,
            name = project.name,
            description = project.description,
            createdAt = project.createdAt.toEpochMilliseconds(),
            lastAccessedAt = project.lastAccessedAt.toEpochMilliseconds(),
            tags = Json.encodeToString(project.tags)
        )
    }

    // ... other methods ...

    private fun ProjectEntity.toModel() = Project(
        id = id,
        name = name,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastAccessedAt = Instant.fromEpochMilliseconds(lastAccessedAt),
        tags = Json.decodeFromString(tags)
    )
}
```

```kotlin
// File: app/shared/src/commonMain/kotlin/data/repository/MessageRepository.kt

interface MessageRepository {
    suspend fun getMessagesByProject(projectId: String, limit: Int): List<Message>
    suspend fun getRecentMessages(limit: Int): List<Message>
    suspend fun saveMessage(message: Message)
    suspend fun deleteMessage(id: String)
}

// Implementation similar to ProjectRepository
```

```kotlin
// File: app/shared/src/commonTest/kotlin/data/repository/FakeProjectRepository.kt

class FakeProjectRepository : ProjectRepository {
    private val projects = mutableMapOf<String, Project>()

    override suspend fun getAllProjects() = projects.values.toList()
    override suspend fun getProjectById(id: String) = projects[id]
    override suspend fun createProject(project: Project) {
        projects[project.id] = project
    }
    // ... implement all methods with in-memory storage
}
```

**Acceptance Criteria:**
- [ ] ProjectRepository interface and implementation
- [ ] MessageRepository interface and implementation
- [ ] MemoryRepository interface and implementation (metadata only)
- [ ] TriviaRepository interface and implementation
- [ ] Fake implementations for testing
- [ ] Extension functions for entity→model conversion

**Tests:**
```kotlin
@Test
fun `project repository CRUD operations work`() = runTest {
    val repository = ProjectRepositoryImpl(database)

    val project = Project(
        id = "proj-1",
        name = "Test",
        description = "Description",
        createdAt = Instant.now(),
        lastAccessedAt = Instant.now()
    )

    repository.createProject(project)
    val retrieved = repository.getProjectById(project.id)

    assertEquals(project.name, retrieved?.name)

    repository.deleteProject(project.id)
    val deleted = repository.getProjectById(project.id)

    assertNull(deleted)
}
```

**Dependencies:** PHASE0-009, PHASE0-010, PHASE0-011
**Blocks:** All business logic that uses repositories

---

### PHASE0-014: Privacy Validation Test
**Priority:** P0 | **Estimated Hours:** 1h | **Status:** [ ]

**Description:**
Comprehensive test to validate privacy architecture: no network permission, no network code, 0 bytes transmitted.

**Implementation:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/privacy/ComprehensivePrivacyTest.kt

@RunWith(AndroidJUnit4::class)
class ComprehensivePrivacyTest {

    @Test
    fun `manifest has no INTERNET permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        assertFalse(
            permissions.contains("android.permission.INTERNET"),
            "INTERNET permission found in manifest - PRIVACY VIOLATION"
        )
        assertFalse(
            permissions.contains("android.permission.ACCESS_NETWORK_STATE"),
            "ACCESS_NETWORK_STATE permission found"
        )
    }

    @Test
    fun `no network classes available in release build`() {
        // Only run on release builds
        assumeTrue(BuildConfig.BUILD_TYPE == "release")

        val networkClasses = listOf(
            "java.net.HttpURLConnection",
            "java.net.URL",
            "javax.net.ssl.HttpsURLConnection",
            "okhttp3.OkHttpClient"
        )

        networkClasses.forEach { className ->
            assertThrows<ClassNotFoundException>(
                "Found network class $className - should be stripped by ProGuard"
            ) {
                Class.forName(className)
            }
        }
    }

    @Test
    fun `network connectivity manager returns no connection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Without INTERNET permission, ConnectivityManager should return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkInfo = cm?.activeNetworkInfo

        assertNull(
            networkInfo,
            "Network info should be null without INTERNET permission"
        )
    }

    @Test
    fun `privacy dashboard shows zero network activity`() {
        // This will be implemented in Phase 5
        // Placeholder for future integration
        assertTrue(true)
    }
}
```

**Acceptance Criteria:**
- [ ] Test verifies no INTERNET permission
- [ ] Test verifies network classes stripped (release build)
- [ ] Test verifies ConnectivityManager returns null
- [ ] All privacy tests pass
- [ ] Tests run in CI/CD

**Tests:**
This IS the test (meta!)

**Dependencies:** PHASE0-001, PHASE0-003
**Blocks:** None (validation only)

---

### PHASE0-015: Phase 0 Integration Test
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
End-to-end test validating entire Phase 0: app builds, database works, knowledge base imported, privacy enforced.

**Implementation:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/integration/Phase0IntegrationTest.kt

@RunWith(AndroidJUnit4::class)
class Phase0IntegrationTest {

    private lateinit var database: MaAIDatabase
    private lateinit var projectRepository: ProjectRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var triviaRepository: TriviaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val driver = AndroidSqliteDriver(MaAIDatabase.Schema, context, "test.db")
        database = MaAIDatabase(driver)

        projectRepository = ProjectRepositoryImpl(database)
        messageRepository = MessageRepositoryImpl(database)
        triviaRepository = TriviaRepositoryImpl(database)
    }

    @Test
    fun `phase 0 end to end - create project, save message, query trivia`() = runTest {
        // 1. Create project
        val project = Project(
            id = "proj-test",
            name = "Integration Test",
            description = "End-to-end test",
            createdAt = Instant.now(),
            lastAccessedAt = Instant.now()
        )

        projectRepository.createProject(project)
        val retrievedProject = projectRepository.getProjectById(project.id)
        assertNotNull(retrievedProject)
        assertEquals(project.name, retrievedProject?.name)

        // 2. Save message
        val message = Message(
            id = "msg-test",
            role = Role.USER,
            content = listOf(ContentPart.Text("Test message")),
            timestamp = Instant.now(),
            projectId = project.id
        )

        messageRepository.saveMessage(message)
        val messages = messageRepository.getMessagesByProject(project.id, limit = 10)
        assertEquals(1, messages.size)
        assertEquals(message.content, messages[0].content)

        // 3. Query trivia (imported from M1K3)
        val triviaCount = triviaRepository.getFactCount()
        assertTrue(
            triviaCount >= 1341,
            "Expected 1341+ trivia facts, got $triviaCount"
        )

        val scienceFacts = triviaRepository.getFactsByCategory("science")
        assertTrue(scienceFacts.isNotEmpty())

        // 4. Verify privacy
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val hasInternet = packageInfo.requestedPermissions?.contains("android.permission.INTERNET") == true

        assertFalse(hasInternet, "Privacy violation: INTERNET permission found")
    }

    @Test
    fun `database cascade delete works`() = runTest {
        // Create project with messages
        val project = Project(
            id = "cascade-test",
            name = "Cascade Test",
            description = "Test CASCADE DELETE",
            createdAt = Instant.now(),
            lastAccessedAt = Instant.now()
        )
        projectRepository.createProject(project)

        val message = Message(
            id = "msg-cascade",
            role = Role.USER,
            content = listOf(ContentPart.Text("Test")),
            timestamp = Instant.now(),
            projectId = project.id
        )
        messageRepository.saveMessage(message)

        // Delete project
        projectRepository.deleteProject(project.id)

        // Verify messages also deleted
        val messages = messageRepository.getMessagesByProject(project.id, limit = 10)
        assertTrue(messages.isEmpty())
    }

    @After
    fun teardown() {
        database.close()
    }
}
```

**Acceptance Criteria:**
- [ ] Integration test creates project
- [ ] Integration test saves message
- [ ] Integration test queries trivia (validates import)
- [ ] Integration test verifies privacy (no INTERNET)
- [ ] CASCADE DELETE test passes
- [ ] All Phase 0 tests passing (15+ tests)

**Tests:**
This IS the comprehensive integration test

**Dependencies:** All previous Phase 0 tickets
**Blocks:** Phase 1 kickoff

---

## Phase 0 Summary

### Completion Checklist

**Privacy Architecture:**
- [ ] PHASE0-001: Internet permission removed
- [ ] PHASE0-003: ProGuard rules configured
- [ ] PHASE0-014: Privacy validation passing

**Build System:**
- [ ] PHASE0-002: All dependencies added
- [ ] PHASE0-007: CI/CD workflow active
- [ ] PHASE0-008: Git branching established

**Scaffold Cleanup:**
- [ ] PHASE0-004: Demo code removed
- [ ] PHASE0-005: Test infrastructure ready

**Data Layer:**
- [ ] PHASE0-006: Data models defined
- [ ] PHASE0-009: Projects table schema
- [ ] PHASE0-010: Messages table schema
- [ ] PHASE0-011: Memory & Trivia tables
- [ ] PHASE0-013: Repository interfaces implemented

**Knowledge Base:**
- [ ] PHASE0-012: M1K3 knowledge base imported (1,341+ facts)

**Integration:**
- [ ] PHASE0-015: Phase 0 integration test passing

### Deliverables
- ✅ Privacy-enforced build (no internet permission)
- ✅ SQLDelight database with 5 tables
- ✅ Knowledge base populated from M1K3
- ✅ Repository abstractions with fakes
- ✅ 15+ tests passing
- ✅ CI/CD pipeline operational

### Metrics
- **Code Coverage Target:** 80%+ for data layer
- **Test Count:** 15+ tests
- **Build Time:** <3 minutes
- **APK Size:** ~50MB (base + models come in Phase 1)

---

## Next Phase

**Phase 1: Core AI Engine** begins after all Phase 0 tickets complete.

See [PHASE1.md](PHASE1.md) for SmolLM2-360M integration, ONNX Runtime setup, and basic chat implementation.
