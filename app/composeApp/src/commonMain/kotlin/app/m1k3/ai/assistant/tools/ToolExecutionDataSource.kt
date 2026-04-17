package app.m1k3.ai.assistant.tools

import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.database.ToolExecution
import app.m1k3.ai.assistant.database.GetToolUsageStats

/**
 * Data source for persistent tool execution history.
 *
 * Every tool call M1K3 makes is logged here — success or failure.
 * Enables "show me all web searches", debug history, and usage analytics.
 */
class ToolExecutionDataSource(
    private val database: MaDatabase
) {

    fun record(
        id: String,
        toolId: String,
        query: String,
        result: String?,
        success: Boolean,
        errorMessage: String?,
        executionTimeMs: Long?,
        timestamp: Long,
        messageId: String?,
        projectId: String
    ) {
        database.toolExecutionQueries.insertToolExecution(
            id = id,
            tool_id = toolId,
            query = query,
            result = result,
            success = if (success) 1L else 0L,
            error_message = errorMessage,
            execution_time_ms = executionTimeMs,
            timestamp = timestamp,
            message_id = messageId,
            project_id = projectId
        )
    }

    fun getForProject(projectId: String, limit: Long): List<ToolExecution> {
        return database.toolExecutionQueries
            .getExecutionsForProject(projectId, limit)
            .executeAsList()
    }

    fun getByToolId(toolId: String, limit: Long): List<ToolExecution> {
        return database.toolExecutionQueries
            .getExecutionsByToolId(toolId, limit)
            .executeAsList()
    }

    fun getByToolIdInProject(projectId: String, toolId: String, limit: Long): List<ToolExecution> {
        return database.toolExecutionQueries
            .getExecutionsByToolIdInProject(projectId, toolId, limit)
            .executeAsList()
    }

    fun getForMessage(messageId: String): List<ToolExecution> {
        return database.toolExecutionQueries
            .getExecutionsForMessage(messageId)
            .executeAsList()
    }

    fun getRecentSuccessful(projectId: String, limit: Long): List<ToolExecution> {
        return database.toolExecutionQueries
            .getRecentSuccessful(projectId, limit)
            .executeAsList()
    }

    fun getToolUsageStats(projectId: String): List<GetToolUsageStats> {
        return database.toolExecutionQueries
            .getToolUsageStats(projectId)
            .executeAsList()
    }

    fun deleteOlderThan(cutoffTimestamp: Long) {
        database.toolExecutionQueries.deleteOlderThan(cutoffTimestamp)
    }

    fun countForProject(projectId: String): Long {
        return database.toolExecutionQueries
            .countExecutionsForProject(projectId)
            .executeAsOne()
    }
}
