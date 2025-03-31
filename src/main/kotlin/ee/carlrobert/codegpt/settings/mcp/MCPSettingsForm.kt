package ee.carlrobert.codegpt.settings.mcp

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import ee.carlrobert.codegpt.CodeGPTBundle
import java.awt.Dimension

class MCPSettingsForm {

    private val mcpSettings = service<MCPSettings>()
    private val editor = service<EditorFactory>().let { factory ->
        factory.createEditor(factory.createDocument(mcpSettings.state.configuration ?: ""))
    }.apply {
        settings.additionalLinesCount = 0
        settings.isWhitespacesShown = true
        settings.isLineMarkerAreaShown = false
        settings.isIndentGuidesShown = false
        settings.isLineNumbersShown = false
        settings.isFoldingOutlineShown = false
        settings.isUseSoftWraps = false
        settings.isAdditionalPageAtBottom = false
        settings.isVirtualSpace = false

        component.preferredSize = Dimension(0, lineHeight * 16)
    }

    fun createPanel() = panel {
        row {
            text(CodeGPTBundle.get("configurationConfigurable.mcp.configuration.label"))
        }
        row {
            cell(editor.component).align(Align.FILL)
        }
    }

    fun applyChanges() {
        mcpSettings.state.configuration = editor.document.text
    }

    fun isModified() = editor.document.text != (mcpSettings.state.configuration ?: "")

    fun resetChanges() {
        editor.document.setText(mcpSettings.state.configuration ?: "")
    }
}
