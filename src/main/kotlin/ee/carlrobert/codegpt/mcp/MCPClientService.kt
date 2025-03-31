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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TOOLS_PLACEHOLDER = "{TOOLS}"

@Service
class MCPClientService private constructor() : Disposable {

    interface Callback {
        fun onResult(callToolResult: CallToolResultBase)
        fun onError()
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val promptTemplate = getResourceContent("/prompts/mcp.txt")
    private val config = service<MCPSettings>().state.configuration?.let {
        try {
            Json.parseToJsonElement(it)
        } catch (_: Exception) {
            null
        }
    }

    private val tools = scope.async { prepareTools() }

    val mcpDescription by lazy {
        val toolsDescription = runBlocking { tools.await().values }
        if (toolsDescription.isEmpty()) {
            ""
        } else {
            promptTemplate.replace(TOOLS_PLACEHOLDER, toolsDescription.joinToString("\n") { it.description })
        }
    }

    fun executeTool(name: String, arguments: Map<String, Any?>, callback: Callback) = scope.launch {
        val client = tools.await()[name]?.client
        client?.callTool(name, arguments)?.let { callback.onResult(it) } ?: run { callback.onError() }
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
                    toolsMap[tool.name] = ToolInfo(client, tool.asDescription())
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
            tools.await().values.map { it.client }.toSet().onEach { it.close() }
        }
    }

    private data class ToolInfo(val client: Client, val description: String)

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
}
