package ee.carlrobert.codegpt.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import ee.carlrobert.codegpt.settings.mcp.MCPSettings
import ee.carlrobert.codegpt.util.file.FileUtil.getResourceContent
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonObject.Companion.serializer
import java.util.regex.Pattern

private const val TOOLS_PLACEHOLDER = "{TOOLS}"

@Service
class MCPClientService private constructor() : Disposable {

    interface Callback {
        fun onResult(callToolResult: CallToolResultBase)
        fun onError()
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val toolPattern = Pattern.compile("^.*?(\\{.+\"tool\".+\\}).*?$", Pattern.DOTALL)
    private val promptTemplate = getResourceContent("/prompts/mcp.txt")
    private val config = service<MCPSettings>().state.configuration?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (_: Exception) {
            null
        }
    }

    private val deferredTools = scope.async { prepareTools() }
    private val tools
        get() = runBlocking { deferredTools.await() }

    val mcpDescription by lazy {
        if (tools.values.isEmpty()) {
            ""
        } else {
            promptTemplate.replace(TOOLS_PLACEHOLDER, tools.values.joinToString("\n") { it.description })
        }
    }

    fun getTool(text: String): ToolRequest? = try {
        val json = toolPattern.matcher(text).replaceAll("$1")
        val response = Json.decodeFromString(serializer(), json)
        val toolName = (response["tool"] as JsonPrimitive).content

        val arguments = HashMap<String, Any>()
        val argumentsJson = response["arguments"] as JsonObject?
        if (argumentsJson != null) {
            for ((key, value) in argumentsJson) {
                arguments[key] = value
            }
        }

        ToolRequest(tools.getValue(toolName).serverName, toolName, arguments)
    } catch (_: Exception) {
        null
    }

    fun executeTool(toolRequest: ToolRequest, callback: Callback) = scope.launch {
        tools[toolRequest.toolName]?.client?.callTool(toolRequest.toolName, toolRequest.arguments)?.let {
            callback.onResult(it)
        } ?: run {
            callback.onError()
        }
    }

    private suspend fun prepareTools(): Map<String, ToolInfo> {
        config ?: return emptyMap()

        val mcpServers = config.jsonObject["mcpServers"]?.jsonObject ?: return emptyMap()

        val toolsMap = mutableMapOf<String, ToolInfo>()

        for (serverName in mcpServers.keys) {
            val server = mcpServers[serverName]?.jsonObject ?: continue
            val command = server["command"]?.jsonPrimitive?.content ?: continue
            val commandArguments = server["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            var process: Process? = null
            try {
                process = ProcessBuilder(listOf(command) + commandArguments).start()

                val transport = StdioClientTransport(
                    process.inputStream.asSource().buffered(),
                    process.outputStream.asSink().buffered()
                )

                val client = Client(
                    clientInfo = Implementation(
                        name = "proxyai-client",
                        version = "1.0.0"
                    )
                )
                client.connect(transport)

                client.listTools()?.tools?.forEach { tool ->
                    toolsMap[tool.name] = ToolInfo(client, serverName, tool.asDescription())
                }
            } catch (e: Exception) {
                process?.destroy()
                thisLogger().error(e)
            }
        }

        return toolsMap
    }

    override fun dispose() {
        runBlocking {
            tools.values.map { it.client }.toSet().onEach { it.close() }
        }
    }

    private data class ToolInfo(val client: Client, val serverName: String, val description: String)

    private fun Tool.asDescription(): String {
        val argumentsDescription = mutableListOf<String>()
        val arguments = inputSchema.properties
        for (argument in arguments.keys) {
            val description = arguments[argument]?.jsonObject?.get("description")?.jsonPrimitive
            val required = inputSchema.required?.contains(argument) == true

            argumentsDescription.add(
                "- $argument: ${description ?: "No description"}${if (required) " (required)" else ""}"
            )
        }
        return """
            Tool: $name
            Description: $description
            Arguments:
            ${argumentsDescription.joinToString("\n")}
        """.trimIndent()
    }

    data class ToolRequest(
        val serverName: String,
        val toolName: String,
        val arguments: Map<String, Any?>
    )
}
