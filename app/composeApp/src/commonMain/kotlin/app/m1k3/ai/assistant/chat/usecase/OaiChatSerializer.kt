package app.m1k3.ai.assistant.chat.usecase

import app.m1k3.ai.domain.tools.ParameterType
import app.m1k3.ai.domain.tools.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val json = Json { encodeDefaults = false }

/**
 * Serialize messages + tools to the OAI-compatible JSON shapes that
 * llama.cpp's `common_chat_msgs_parse_oaicompat` and
 * `common_chat_tools_parse_oaicompat` expect.
 *
 * The spike keeps this small: a system message, a single user turn, and
 * an optional tool list. Multi-turn history will land here later.
 */
internal object OaiChatSerializer {
    fun messagesJson(
        systemPrompt: String?,
        userContent: String,
    ): String {
        val arr: JsonArray =
            buildJsonArray {
                if (!systemPrompt.isNullOrBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userContent)
                }
            }
        return json.encodeToString(JsonArray.serializer(), arr)
    }

    fun toolsJson(tools: List<Tool>): String {
        if (tools.isEmpty()) return "[]"
        val arr: JsonArray =
            buildJsonArray {
                tools.forEach { tool -> add(toolJson(tool)) }
            }
        return json.encodeToString(JsonArray.serializer(), arr)
    }

    private fun toolJson(tool: Tool): JsonObject =
        buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", tool.id)
                put("description", tool.description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        tool.parameters.forEach { param ->
                            putJsonObject(param.name) {
                                put("type", jsonSchemaType(param.type))
                                if (param.description.isNotBlank()) {
                                    put("description", param.description)
                                }
                                param.enumValues?.takeIf { it.isNotEmpty() }?.let { values ->
                                    putJsonArray("enum") { values.forEach { add(it) } }
                                }
                            }
                        }
                    }
                    val required = tool.parameters.filter { it.required }.map { it.name }
                    if (required.isNotEmpty()) {
                        putJsonArray("required") { required.forEach { add(it) } }
                    }
                }
            }
        }

    private fun jsonSchemaType(type: ParameterType): String =
        when (type) {
            ParameterType.STRING -> "string"
            ParameterType.NUMBER -> "number"
            ParameterType.BOOLEAN -> "boolean"
            ParameterType.ENUM -> "string"
        }
}
