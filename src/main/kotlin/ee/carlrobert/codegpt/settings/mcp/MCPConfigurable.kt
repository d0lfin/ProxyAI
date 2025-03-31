package ee.carlrobert.codegpt.settings.mcp

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class MCPConfigurable : Configurable {

    private lateinit var component: MCPSettingsForm

    override fun getDisplayName(): String {
        return "ProxyAI: MCP"
    }

    override fun createComponent(): JComponent {
        component = MCPSettingsForm()
        return component.createPanel()
    }

    override fun isModified(): Boolean = component.isModified()

    override fun apply() {
        component.applyChanges()
    }

    override fun reset() {
        component.resetChanges()
    }

    override fun cancel() {
        component.resetChanges()
    }
}