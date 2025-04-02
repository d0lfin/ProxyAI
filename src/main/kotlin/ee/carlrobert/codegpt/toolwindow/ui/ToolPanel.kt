package ee.carlrobert.codegpt.toolwindow.ui

import com.intellij.icons.AllIcons.General
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import ee.carlrobert.codegpt.util.EditorUtil.createEditor
import ee.carlrobert.codegpt.util.EditorUtil.updateEditorDocument
import java.awt.BorderLayout
import javax.swing.*

/**
 * A collapsible panel for displaying formatted JSON in IntelliJ IDEA plugins.
 */
class ToolPanel(
    project: Project,
    disposableParent: Disposable,
) : JPanel(), Disposable {

    private val title: ActionLink

    private val editor = (createEditor(project, "json", "") as EditorEx).apply {
        colorsScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
        setVerticalScrollbarVisible(false)

        settings.apply {
            additionalColumnsCount = 0
            additionalLinesCount = 0
            isAdditionalPageAtBottom = false
            isVirtualSpace = false
            isLineMarkerAreaShown = false
            isLineNumbersShown = false
        }
    }
    private val currentIcon
        get() = if (expanded) General.ArrowDown else General.ArrowRight

    private var serverName: String? = null
    private var toolName: String? = null
    private var expanded = false
        set(value) {
            field = value
            editor.component.isVisible = value
            revalidate()
            repaint()
            updateIcon()
        }

    init {
        layout = BorderLayout()
        border = JBUI.Borders.empty(5, 0)

        title = ActionLink("") {
            expanded = !expanded
        }

        add(title, BorderLayout.NORTH)
        add(editor.component, BorderLayout.CENTER)

        expanded = false

        Disposer.register(disposableParent, this)
    }

    fun setToolRequest(text: String) {
        val toolRegex = "\"tool\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val matchResult = toolRegex.find(text)
        if (matchResult != null) {
            val extractedToolName = matchResult.groupValues[1]
            toolName = extractedToolName
        }

        updateEditorDocument(editor, text)
    }

    fun setToolInfo(toolName: String, serverName: String? = null) {
        this.serverName = serverName
        this.toolName = toolName

        title.text = "<html>Running <b>$toolName</b>" +
                if (serverName == null) "</html>" else " from <b>$serverName</b></html>"

        updateIcon()
    }

    fun setToolResult(text: String) {
        title.text = "<html>View result from <b>$toolName</b> from <b>$serverName</b></html>"
        updateEditorDocument(editor, "${editor.document.text}\n\n$text")
    }

    private fun updateIcon() {
        title.setIcon(currentIcon)
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}