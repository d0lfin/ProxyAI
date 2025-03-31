package ee.carlrobert.codegpt.settings.mcp

import com.intellij.openapi.components.*

@Service
@State(
    name = "CodeGPT_MCPSettings",
    storages = [Storage("CodeGPT_MCPSettings.xml")]
)
class MCPSettings : SimplePersistentStateComponent<MCPSettingsState>(MCPSettingsState())

class MCPSettingsState : BaseState() {
    var configuration by string()
}
