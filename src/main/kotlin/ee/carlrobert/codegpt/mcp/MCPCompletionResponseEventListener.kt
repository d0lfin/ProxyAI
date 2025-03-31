package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import ee.carlrobert.codegpt.completions.*
import ee.carlrobert.codegpt.completions.ChatCompletionParameters.Companion.builder
import ee.carlrobert.codegpt.completions.CompletionRequestFactory.Companion.getFactory
import ee.carlrobert.codegpt.conversations.ConversationService
import ee.carlrobert.codegpt.conversations.message.Message
import ee.carlrobert.codegpt.settings.GeneralSettings
import ee.carlrobert.codegpt.toolwindow.chat.ui.ChatMessageResponseBody
import ee.carlrobert.codegpt.toolwindow.ui.ResponseMessagePanel
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.TextContent

class MCPCompletionResponseEventListener(
    private val project: Project,
    private val responseMessagePanel: ResponseMessagePanel,
    private val chatAllowedTools: ChatAllowedTools,
    private val completionResponseEventListener: CompletionResponseEventListener
): CompletionResponseEventListener by completionResponseEventListener {

    private val mcpClientService = service<MCPClientService>()
    private val responseContainer = responseMessagePanel.getContent() as ChatMessageResponseBody

    override fun handleCompleted(fullMessage: String, callParameters: ChatCompletionParameters) {
        mcpClientService.getTool(fullMessage)?.let { tool ->
            ConversationService.getInstance().saveMessage(fullMessage, callParameters)

            responseContainer.updateToolInfo(tool.serverName, tool.toolName)

            if (chatAllowedTools.allowed(tool)) {
                executeTool(tool, callParameters)
            } else {
                responseMessagePanel.showPermissionsButtons(
                    onAllowForThisChat = {
                        chatAllowedTools.add(tool)
                        executeTool(tool, callParameters)
                        responseMessagePanel.hidePermissionsButtons()
                    },
                    onAllowOnce = {
                        executeTool(tool, callParameters)
                        responseMessagePanel.hidePermissionsButtons()
                    },
                    onDeny = {
                        completionResponseEventListener.handleCompleted(fullMessage, callParameters)
                        responseMessagePanel.hidePermissionsButtons()
                    }
                )
            }
        } ?: run {
            completionResponseEventListener.handleCompleted(fullMessage, callParameters)
        }
    }

    private fun executeTool(tool: MCPClientService.ToolRequest, callParameters: ChatCompletionParameters) {
        mcpClientService.executeTool(tool, object : MCPClientService.Callback {
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
        responseContainer.updateToolResponse(toolResponse)

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
