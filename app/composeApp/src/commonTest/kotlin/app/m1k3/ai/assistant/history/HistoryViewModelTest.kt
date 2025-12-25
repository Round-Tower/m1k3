package app.m1k3.ai.assistant.history

import app.m1k3.ai.assistant.test.TestDatabaseFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.*

/**
 * HistoryViewModel Tests
 *
 * Basic sanity checks for HistoryViewModel state management.
 * Since all underlying repositories are extensively tested,
 * these tests focus on simple state verification.
 *
 * Note: Async operations (loadConversations, searchConversations, deleteConversation)
 * are integration-tested manually in the app, as proper async ViewModel testing
 * requires more complex test infrastructure.
 */
class HistoryViewModelTest {

    @Test
    fun `initial state is empty`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Assert
        val state = viewModel.state.value
        assertTrue(state.conversations.isEmpty(), "Should start with empty conversations")
        assertEquals("", state.searchQuery, "Should start with empty search query")
        assertNull(state.searchResults, "Should start with no search results")
        assertFalse(state.isLoading, "Should not be loading initially")
        assertNull(state.error, "Should have no error initially")
        assertNull(state.selectedConversation, "Should have no selected conversation")
    }

    @Test
    fun `exportConversation returns JSON string`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        val projectId = "project_001"
        val convId = conversationRepo.createConversation(projectId, "Test Export")

        // Act
        val json = viewModel.exportConversation(convId, ExportFormat.JSON)

        // Assert
        assertNotNull(json, "Should return JSON string")
        assertTrue(json.contains("Test Export"), "Should include conversation title")
        assertTrue(json.contains("\"messages\""), "Should include messages array")
    }

    @Test
    fun `exportConversation returns Markdown string`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        val projectId = "project_001"
        val convId = conversationRepo.createConversation(projectId, "Test Export")

        // Act
        val markdown = viewModel.exportConversation(convId, ExportFormat.MARKDOWN)

        // Assert
        assertNotNull(markdown, "Should return Markdown string")
        assertTrue(markdown.contains("# Test Export"), "Should include conversation title as heading")
        assertTrue(markdown.contains("---"), "Should include metadata separator")
    }

    @Test
    fun `selectConversation updates selectedConversation`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        val projectId = "project_001"
        val convId = conversationRepo.createConversation(projectId, "Selected Conversation")
        val conversation = conversationRepo.getConversationById(convId)!!

        // Act
        viewModel.selectConversation(conversation)

        // Assert
        val state = viewModel.state.value
        assertNotNull(state.selectedConversation, "Should have selected conversation")
        assertEquals("Selected Conversation", state.selectedConversation?.title, "Should select correct conversation")
    }

    @Test
    fun `clearSearch resets search state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Manually set search state
        viewModel.searchConversations("test query")

        // Act
        viewModel.clearSearch()

        // Assert
        val state = viewModel.state.value
        assertEquals("", state.searchQuery, "Should clear search query")
        assertNull(state.searchResults, "Should clear search results")
    }

    @Test
    fun `clearError resets error state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.clearError()

        // Assert
        assertNull(viewModel.state.value.error, "Should clear error")
    }

    @Test
    fun `reset clears all state`() {
        // Arrange
        val database = TestDatabaseFactory.createInMemoryDatabase()
        val conversationRepo = ConversationRepository(database)
        val searchRepo = SearchRepository(database)
        val exportManager = ExportManager(database)
        val viewModel = HistoryViewModel(
            conversationRepository = conversationRepo,
            searchRepository = searchRepo,
            exportManager = exportManager,
            scope = CoroutineScope(Dispatchers.Default)
        )

        // Act
        viewModel.reset()

        // Assert
        val state = viewModel.state.value
        assertTrue(state.conversations.isEmpty(), "Should clear conversations")
        assertEquals("", state.searchQuery, "Should clear search query")
        assertNull(state.searchResults, "Should clear search results")
        assertNull(state.selectedConversation, "Should clear selected conversation")
        assertFalse(state.isLoading, "Should not be loading")
        assertNull(state.error, "Should clear error")
    }
}
