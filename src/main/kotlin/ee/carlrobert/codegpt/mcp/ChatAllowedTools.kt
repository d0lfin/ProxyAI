package ee.carlrobert.codegpt.mcp

class ChatAllowedTools {

    private val allowedTools = mutableMapOf<String, MutableSet<String>>()

    fun add(toolRequest: MCPClientService.ToolRequest) {
        allowedTools.getOrPut(toolRequest.serverName) { mutableSetOf() }.add(toolRequest.toolName)
    }

    fun allowed(
        toolRequest: MCPClientService.ToolRequest
    ) = allowedTools[toolRequest.serverName]?.contains(toolRequest.toolName) == true
}
