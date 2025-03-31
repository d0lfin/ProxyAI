package ee.carlrobert.codegpt.toolwindow.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.Icons
import javax.swing.SwingConstants
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.BorderLayout
import java.awt.GridLayout

open class ResponseMessagePanel : BaseMessagePanel() {

    private val allowOnceButton = JButton(
        CodeGPTBundle.get("toolwindow.chat.response.action.mcp.allowOnce.text")
    )
    private val allowForThisChatButton = JButton(
        CodeGPTBundle.get("toolwindow.chat.response.action.mcp.allowForThisChat.text")
    )
    private val denyButton = JButton(CodeGPTBundle.get("toolwindow.chat.response.action.mcp.deny.text"))
    private val buttonsPanel = JPanel(GridLayout(1, 2))

    init {
        buttonsPanel.add(allowForThisChatButton)
        buttonsPanel.add(allowOnceButton)
        buttonsPanel.add(denyButton)
        add(buttonsPanel, BorderLayout.SOUTH)
        buttonsPanel.isVisible = false
    }

    override fun createDisplayNameLabel(): JBLabel {
        return JBLabel(
            CodeGPTBundle.get("project.label"),
            Icons.Default,
            SwingConstants.LEADING
        )
            .setAllowAutoWrapping(true)
            .withFont(JBFont.label().asBold())
            .apply {
                iconTextGap = 6
            }
    }

    fun showPermissionsButtons(onAllowForThisChat: Runnable, onAllowOnce: Runnable, onDeny: Runnable) {
        removeAllButtonListeners()

        allowForThisChatButton.addActionListener { onAllowForThisChat.run() }
        allowOnceButton.addActionListener { onAllowOnce.run() }
        denyButton.addActionListener { onDeny.run() }

        buttonsPanel.isVisible = true
    }

    fun hidePermissionsButtons() {
        removeAllButtonListeners()
        buttonsPanel.isVisible = false
    }

    private fun removeAllButtonListeners() {
        allowOnceButton.actionListeners.forEach { allowOnceButton.removeActionListener(it) }
        allowForThisChatButton.actionListeners.forEach { allowForThisChatButton.removeActionListener(it) }
        denyButton.actionListeners.forEach { denyButton.removeActionListener(it) }
    }
}
