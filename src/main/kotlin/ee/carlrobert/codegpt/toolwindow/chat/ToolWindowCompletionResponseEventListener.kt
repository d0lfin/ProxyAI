package ee.carlrobert.codegpt.toolwindow.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import ee.carlrobert.codegpt.EncodingManager
import ee.carlrobert.codegpt.codecompletions.CompletionProgressNotifier.Companion.update
import ee.carlrobert.codegpt.completions.ChatCompletionParameters
import ee.carlrobert.codegpt.completions.CompletionResponseEventListener
import ee.carlrobert.codegpt.conversations.Conversation
import ee.carlrobert.codegpt.conversations.ConversationService
import ee.carlrobert.codegpt.conversations.message.Message
import ee.carlrobert.codegpt.events.CodeGPTEvent
import ee.carlrobert.codegpt.telemetry.TelemetryAction
import ee.carlrobert.codegpt.toolwindow.chat.ui.ChatMessageResponseBody
import ee.carlrobert.codegpt.toolwindow.chat.ui.textarea.TotalTokensPanel
import ee.carlrobert.codegpt.toolwindow.ui.ResponseMessagePanel
import ee.carlrobert.codegpt.toolwindow.ui.UserMessagePanel
import ee.carlrobert.codegpt.ui.OverlayUtil
import ee.carlrobert.codegpt.ui.textarea.UserInputPanel
import ee.carlrobert.llm.client.openai.completion.ErrorDetails
import java.awt.event.ActionEvent
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.Timer

internal abstract class ToolWindowCompletionResponseEventListener(
    private val project: Project,
    private val userMessagePanel: UserMessagePanel,
    private val responsePanel: ResponseMessagePanel,
    private val totalTokensPanel: TotalTokensPanel,
    private val textArea: UserInputPanel
) : CompletionResponseEventListener {
    private val messageBuilder = StringBuilder()
    private val encodingManager: EncodingManager = EncodingManager.getInstance()
    private val responseContainer = responsePanel.getContent() as ChatMessageResponseBody

    private val updateTimer = Timer(
        UPDATE_INTERVAL_MS
    ) { _: ActionEvent? -> processBufferedMessages() }
    private val messageBuffer = ConcurrentLinkedQueue<String>()
    private var stopped = false
    private var streamResponseReceived = false

    abstract fun handleTokensExceededPolicyAccepted()

    override fun handleRequestOpen() {
        updateTimer.start()
    }

    override fun handleMessage(partialMessage: String) {
        streamResponseReceived = true

        try {
            messageBuilder.append(partialMessage)
            val ongoingTokens = encodingManager.countTokens(messageBuilder.toString())
            messageBuffer.offer(partialMessage)
            ApplicationManager.getApplication().invokeLater {
                totalTokensPanel.update(
                    totalTokensPanel.tokenDetails.total + ongoingTokens
                )
            }
        } catch (e: Exception) {
            responseContainer.displayError("Something went wrong.")
            throw RuntimeException("Error while updating the content", e)
        }
    }

    override fun handleError(error: ErrorDetails, ex: Throwable) {
        ApplicationManager.getApplication().invokeLater {
            try {
                if ("insufficient_quota" == error.code) {
                    responseContainer.displayQuotaExceeded()
                } else {
                    responseContainer.displayError(error.message)
                }
            } finally {
                stopStreaming(responseContainer)
            }
        }
    }

    override fun handleTokensExceeded(conversation: Conversation, message: Message) {
        ApplicationManager.getApplication().invokeLater {
            val answer = OverlayUtil.showTokenLimitExceededDialog()
            if (answer == Messages.OK) {
                TelemetryAction.IDE_ACTION.createActionMessage()
                    .property("action", "DISCARD_TOKEN_LIMIT")
                    .property("model", conversation.model)
                    .send()

                ConversationService.getInstance().discardTokenLimits(conversation)
                handleTokensExceededPolicyAccepted()
            } else {
                stopStreaming(responseContainer)
            }
        }
    }

    override fun handleCompleted(fullMessage: String, callParameters: ChatCompletionParameters) {
        ConversationService.getInstance().saveMessage(fullMessage, callParameters)

        ApplicationManager.getApplication().invokeLater {
            try {
                responsePanel.enableAllActions(true)
                if (!streamResponseReceived && !fullMessage.isEmpty()) {
                    responseContainer.withResponse(fullMessage)
                }
                totalTokensPanel.updateUserPromptTokens(textArea.text)
                totalTokensPanel.updateConversationTokens(callParameters.conversation)
            } finally {
                stopStreaming(responseContainer)
            }
        }
    }

    override fun handleCodeGPTEvent(event: CodeGPTEvent) {
        responseContainer.handleCodeGPTEvent(event)
    }

    private fun processBufferedMessages() {
        if (messageBuffer.isEmpty()) {
            if (stopped) {
                updateTimer.stop()
            }
            return
        }

        val accumulatedMessage = StringBuilder()
        var message: String?
        while ((messageBuffer.poll().also { message = it }) != null) {
            accumulatedMessage.append(message)
        }

        responseContainer.updateMessage(accumulatedMessage.toString())
    }

    private fun stopStreaming(responseContainer: ChatMessageResponseBody) {
        stopped = true
        textArea.setSubmitEnabled(true)
        userMessagePanel.enableAllActions(true)
        responsePanel.enableAllActions(true)
        responseContainer.hideCaret()
        update(project, false)
    }

    companion object {
        private val LOG = Logger.getInstance(
            ToolWindowCompletionResponseEventListener::class.java
        )
        private const val UPDATE_INTERVAL_MS = 8
    }
}
