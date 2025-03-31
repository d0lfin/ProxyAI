package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import ee.carlrobert.codegpt.completions.*
import ee.carlrobert.codegpt.completions.ChatCompletionParameters.Companion.builder
import ee.carlrobert.codegpt.completions.CompletionRequestFactory.Companion.getFactory
import ee.carlrobert.codegpt.conversations.message.Message
import ee.carlrobert.codegpt.settings.GeneralSettings
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObject.Companion.serializer
import kotlinx.serialization.json.JsonPrimitive
import java.util.regex.Pattern

class MCPCompletionResponseEventListener(
    private val project: Project,
    private val completionResponseEventListener: CompletionResponseEventListener
): CompletionResponseEventListener by completionResponseEventListener {

    private val toolPattern = Pattern.compile("^.*?(\\{.+\"tool\".+\\}).*?$", Pattern.DOTALL)
    private val mcpClientService = service<MCPClientService>()

    override fun handleCompleted(fullMessage: String, callParameters: ChatCompletionParameters) {
        try {
            val tool = parseToolRequest(fullMessage)
            // request permission
            executeTool(tool.first, tool.second, callParameters)
        } catch (_: Exception) {
            completionResponseEventListener.handleCompleted(fullMessage, callParameters)
        }
    }

    private fun parseToolRequest(fullMessage: String): Pair<String, Map<String, Any>> {
        val json = toolPattern.matcher(fullMessage).replaceAll("$1")
        val response = Json.decodeFromString(serializer(), json)

        val arguments = HashMap<String, Any>()
        val argumentsJson = response["arguments"] as JsonObject?
        if (argumentsJson != null) {
            for ((key, value) in argumentsJson) {
                arguments[key] = value
            }
        }

        return (response["tool"] as JsonPrimitive).content to arguments
    }

    private fun executeTool(tool: String, arguments: Map<String, Any>, callParameters: ChatCompletionParameters) {
        mcpClientService.executeTool(tool, arguments, object : MCPClientService.Callback {
            override fun onResult(callToolResult: CallToolResultBase) {
                if (callToolResult.isError == true) {
                    onError()
                } else {
                    val toolResponse = callToolResult.content.map {
                        if (it is TextContent) it.text else it.toString()
                    }.joinToString("\n")
                    handleToolResponse(toolResponse, callParameters)
                }
            }

            override fun onError() {
                completionResponseEventListener.handleError(ErrorDetails.DEFAULT_ERROR, Throwable())
            }
        })
    }

    private fun handleToolResponse(toolResponse: String, callParameters: ChatCompletionParameters) {
        val callWithToolResponseParameters = builder(callParameters.conversation, Message(toolResponse))
            .sessionId(callParameters.sessionId)
            .conversationType(callParameters.conversationType)
            .build()

        val requestWithToolResponse = getFactory(GeneralSettings.getSelectedService())
            .createChatRequest(callWithToolResponseParameters)

        CompletionRequestService.getInstance().getChatCompletionAsync(
            requestWithToolResponse,
            ChatCompletionEventListener(
                project,
                callWithToolResponseParameters,
                this
            )
        )
    }
}
