package org.agenthud.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns the desktop's flat transcript rows into what a chat reader sees, the way the Claude app
 * does: the user's prompts, the agent's prose, and runs of tool work folded into one collapsible
 * group whose calls are paired with their results.
 */
object Transcript {

    /** What a tool does, which picks its icon and verb. */
    enum class Glyph { READ, EDIT, WRITE, SEARCH, LIST, SHELL, WEB, AGENT, TODO, SKILL, TOOL, NOTE }

    /** One-line description of a step: "Read" + "AgentHudRoot.kt". */
    data class StepSummary(
        val glyph: Glyph,
        val verb: String,
        val target: String?,
        /** Monospace target (commands, patterns). */
        val codeTarget: Boolean = false,
    )

    sealed interface Step {
        val id: String
    }

    /** A tool call and, when it has arrived, its result. Either side may be outside the page. */
    data class ToolStep(
        override val id: String,
        val call: SessionMessageItem?,
        val result: SessionMessageItem?,
    ) : Step {
        val name: String get() = call?.toolCall?.name.orEmpty()
        val summary: StepSummary by lazy { summarize(this) }
        val failed: Boolean by lazy { result?.text?.let(::looksLikeError) == true }
        val output: String get() = result?.text.orEmpty()
    }

    /** Context a client wrote as a user line (system reminders, hook output, command echoes). */
    data class NoteStep(override val id: String, val message: SessionMessageItem) : Step

    sealed interface Block {
        val key: String
    }

    data class UserPrompt(override val key: String, val message: SessionMessageItem) : Block

    data class AssistantText(
        override val key: String,
        val message: SessionMessageItem,
        /** Last prose before the next prompt (or the end): where the Claude app puts its actions. */
        val endsTurn: Boolean,
    ) : Block

    data class Thinking(override val key: String, val message: SessionMessageItem) : Block

    data class Steps(override val key: String, val steps: List<Step>) : Block {
        val toolCount: Int get() = steps.count { it is ToolStep }
        val anyFailed: Boolean get() = steps.any { it is ToolStep && it.failed }
    }

    fun isToolCall(m: SessionMessageItem): Boolean = m.toolCall != null

    fun isToolResult(m: SessionMessageItem): Boolean {
        val role = m.role.lowercase()
        return !isToolCall(m) && (role == "tool" || role == "toolresult" || role == "tool_result" || role == "function")
    }

    fun isThinking(m: SessionMessageItem): Boolean {
        val role = m.role.lowercase()
        val kind = m.kind?.lowercase()
        return kind == "thinking" || kind == "reasoning" || role == "thinking" || role == "reasoning" ||
            // Antigravity names its thinking rows `<id>:thinking`; other clients need the desktop's `kind`.
            (role == "assistant" && m.toolCall == null && m.id.endsWith(":thinking"))
    }

    fun build(messages: List<SessionMessageItem>): List<Block> {
        val blocks = ArrayList<Block>()
        var steps = ArrayList<Step>()
        // Pair every call with its result up front, so prose or narration between the two
        // never splits them.
        val callIds = messages.filter(::isToolCall).mapNotNull { it.toolCallId ?: it.toolCall?.id }.toHashSet()
        val resultsByCall = HashMap<String, SessionMessageItem>()
        messages.filter(::isToolResult).forEach { r ->
            val id = r.toolCallId ?: return@forEach
            if (id in callIds && id !in resultsByCall) resultsByCall[id] = r
        }
        val pairedResults = resultsByCall.values.toHashSet()

        fun flush() {
            if (steps.isNotEmpty()) {
                blocks.add(Steps("steps:${steps.first().id}", steps))
                steps = ArrayList()
            }
        }

        messages.forEachIndexed { index, m ->
            val key = m.id.ifBlank { "row$index" }
            when {
                isToolCall(m) -> {
                    val callId = m.toolCallId ?: m.toolCall?.id
                    steps.add(ToolStep(key, call = m, result = callId?.let { resultsByCall[it] }))
                }
                isToolResult(m) -> {
                    if (m !in pairedResults) steps.add(ToolStep(key, call = null, result = m))
                }
                isThinking(m) -> {
                    if (m.text.isNullOrBlank()) return@forEachIndexed
                    flush()
                    blocks.add(Thinking("think:$key", m))
                }
                m.isPrompt -> {
                    if (m.text.isNullOrBlank()) return@forEachIndexed
                    flush()
                    blocks.add(UserPrompt("user:$key", m))
                }
                m.role.equals("user", ignoreCase = true) || m.role.equals("system", ignoreCase = true) ||
                    m.role.equals("developer", ignoreCase = true) -> {
                    if (!m.text.isNullOrBlank()) steps.add(NoteStep(key, m))
                }
                else -> {
                    if (m.text.isNullOrBlank()) return@forEachIndexed
                    flush()
                    blocks.add(AssistantText("text:$key", m, endsTurn = false))
                }
            }
        }
        flush()

        // Mark the last prose of each turn (followed by a prompt, or the end of the page).
        var turnEndsAfter = true
        for (i in blocks.indices.reversed()) {
            when (val b = blocks[i]) {
                is UserPrompt -> turnEndsAfter = true
                is AssistantText -> {
                    if (turnEndsAfter) blocks[i] = b.copy(endsTurn = true)
                    turnEndsAfter = false
                }
                else -> Unit
            }
        }
        return blocks
    }

    // ---- Step summaries ------------------------------------------------------------------

    fun summarize(step: ToolStep): StepSummary {
        val call = step.call?.toolCall
        if (call == null) {
            val lines = lineCount(step.output)
            return StepSummary(Glyph.TOOL, "Tool output", if (lines > 0) plural(lines, "line") else null)
        }
        val raw = call.name.orEmpty()
        val name = raw.substringAfterLast("__").lowercase()
        val args = argumentsOf(step)
        fun arg(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            (args?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        val file = arg("file_path", "path", "notebook_path", "filePath", "AbsolutePath", "TargetFile", "target_file", "file")
            ?.let(::fileName)

        return when (name) {
            "read", "read_file", "view_file", "view", "readfile", "open_file" ->
                StepSummary(Glyph.READ, "Read", file ?: arg("pattern"))
            "edit", "multiedit", "str_replace", "str_replace_editor", "str_replace_based_edit_tool",
            "replace_file_content", "multi_replace_file_content", "apply_patch", "notebookedit", "edit_file" ->
                StepSummary(Glyph.EDIT, "Edited", file ?: patchFile(arg("input", "patch")))
            "write", "write_to_file", "create_file", "writefile" ->
                StepSummary(Glyph.WRITE, "Wrote", file)
            "grep", "grep_search", "search", "codebase_search", "search_files", "rg" ->
                StepSummary(Glyph.SEARCH, "Searched", arg("pattern", "query", "Query", "regex"), codeTarget = true)
            "glob", "list_dir", "ls", "find_by_name", "list_files", "find" ->
                StepSummary(Glyph.LIST, "Listed", arg("pattern", "path", "DirectoryPath", "SearchDirectory")?.let(::shortPath), codeTarget = true)
            "bash", "powershell", "shell", "exec_command", "run_command", "local_shell", "terminal", "run_terminal_cmd" -> {
                val description = arg("description")
                val command = arg("command", "cmd", "CommandLine") ?: (args?.get("command") as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(" ")
                if (description != null) {
                    StepSummary(Glyph.SHELL, "Ran", description)
                } else {
                    StepSummary(Glyph.SHELL, "Ran", command?.lineSequence()?.firstOrNull()?.take(80), codeTarget = true)
                }
            }
            "webfetch", "fetch", "read_url_content", "web_fetch" ->
                StepSummary(Glyph.WEB, "Fetched", arg("url", "Url")?.let(::hostOf))
            "websearch", "web_search", "search_web" ->
                StepSummary(Glyph.WEB, "Searched the web", arg("query", "Query"))
            "task", "agent", "spawn_agent", "subagent", "invoke_subagent" ->
                StepSummary(Glyph.AGENT, "Agent", arg("description", "prompt")?.lineSequence()?.firstOrNull()?.take(80))
            "todowrite", "update_plan", "todo_write", "todoread" ->
                StepSummary(Glyph.TODO, "Updated to-dos", null)
            "skill" -> StepSummary(Glyph.SKILL, "Skill", arg("skill", "name"))
            else -> StepSummary(
                Glyph.TOOL,
                raw.substringAfterLast("__").ifBlank { "Tool" },
                file ?: arg("description", "query", "pattern", "url", "command", "name")?.lineSequence()?.firstOrNull()?.take(80),
            )
        }
    }

    /** "Read 3 files, edited 2 files, ran a command". */
    fun groupSummary(steps: List<Step>): String {
        val tools = steps.filterIsInstance<ToolStep>().map { it.summary.glyph }
        if (tools.isEmpty()) return if (steps.size == 1) "System note" else "${steps.size} system notes"
        val counts = LinkedHashMap<Glyph, Int>()
        tools.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val phrases = counts.map { (g, n) ->
            when (g) {
                Glyph.READ -> "read ${plural(n, "file")}"
                Glyph.EDIT -> "edited ${plural(n, "file")}"
                Glyph.WRITE -> "wrote ${plural(n, "file")}"
                Glyph.SEARCH -> if (n == 1) "searched once" else "searched $n times"
                Glyph.LIST -> "listed ${plural(n, "folder")}"
                Glyph.SHELL -> if (n == 1) "ran a command" else "ran $n commands"
                Glyph.WEB -> if (n == 1) "used the web" else "used the web $n times"
                Glyph.AGENT -> if (n == 1) "ran an agent" else "ran $n agents"
                Glyph.TODO -> "updated to-dos"
                Glyph.SKILL -> plural(n, "skill").let { "used $it" }
                Glyph.TOOL, Glyph.NOTE -> "used ${plural(n, "tool")}"
            }
        }
        return phrases.distinct().joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    // ---- Expanded content -----------------------------------------------------------------

    /** What an expanded step shows: a diff for edits, the command + output for shells, else the output. */
    fun detailText(step: ToolStep): String {
        val args = argumentsOf(step)
        fun s(k: String) = (args?.get(k) as? JsonPrimitive)?.contentOrNull
        val glyph = step.summary.glyph
        return when {
            glyph == Glyph.EDIT && (s("old_string") != null || s("new_string") != null) ->
                diffOf(s("old_string").orEmpty(), s("new_string").orEmpty())
            glyph == Glyph.EDIT && args?.get("edits") is JsonArray -> (args["edits"] as JsonArray)
                .mapNotNull { it as? JsonObject }
                .joinToString("\n···\n") { e ->
                    diffOf(
                        (e["old_string"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        (e["new_string"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    )
                }
            glyph == Glyph.EDIT && s("input") != null -> s("input").orEmpty()
            glyph == Glyph.WRITE && s("content") != null ->
                s("content").orEmpty().lineSequence().joinToString("\n") { "+ $it" }
            glyph == Glyph.SHELL -> {
                val cmd = s("command") ?: s("cmd") ?: s("CommandLine")
                listOfNotNull(cmd?.let { "$ $it" }, cleanOutput(step.output).ifBlank { null }).joinToString("\n")
            }
            else -> cleanOutput(step.output).ifBlank {
                args?.let { prettyArgs(it) }.orEmpty()
            }
        }
    }

    /**
     * A call's arguments as an object. Codex and Buddy send them JSON-encoded in a string, and
     * apply_patch sends the raw patch text, which is kept as `input`.
     */
    fun argumentsOf(step: ToolStep): JsonObject? {
        val raw = step.call?.toolCall?.arguments ?: return null
        if (raw is JsonObject) return raw
        val text = (raw as? JsonPrimitive)?.contentOrNull ?: return null
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        return parsed as? JsonObject ?: JsonObject(mapOf("input" to JsonPrimitive(text)))
    }

    fun cleanOutput(text: String): String = text
        .replace("<tool_use_error>", "")
        .replace("</tool_use_error>", "")
        .trimEnd()

    fun looksLikeError(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("<tool_use_error>") || t.startsWith("Error:") || t.startsWith("error:")
    }

    fun diffOf(old: String, new: String): String {
        val removed = if (old.isEmpty()) emptyList() else old.lines().map { "- $it" }
        val added = if (new.isEmpty()) emptyList() else new.lines().map { "+ $it" }
        return (removed + added).joinToString("\n")
    }

    private fun prettyArgs(obj: JsonObject): String = obj.entries.joinToString("\n") { (k, v) ->
        val value = when (v) {
            is JsonPrimitive -> v.contentOrNull ?: "null"
            else -> v.toString()
        }
        "$k: ${value.take(400)}"
    }

    // ---- Files -----------------------------------------------------------------------------

    data class FileLink(val label: String, val uri: String)

    private val MD_LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")

    /** Local-file links in a message (`[name](file:///…)` or Windows paths), first mention wins. */
    fun fileLinks(markdown: String): List<FileLink> = MD_LINK.findAll(markdown)
        .map { FileLink(it.groupValues[1], it.groupValues[2]) }
        .filter { isFileUri(it.uri) }
        .distinctBy { it.uri }
        .toList()

    fun isFileUri(url: String): Boolean =
        url.startsWith("file://", ignoreCase = true) ||
            (url.length >= 3 && url[1] == ':' && (url[2] == '\\' || url[2] == '/'))

    fun fileName(path: String): String = path
        .removePrefix("""\\?\UNC\""")
        .removePrefix("""\\?\""")
        .trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBefore('#')
        .ifBlank { path }

    fun fileExtension(name: String): String = name.substringAfterLast('.', "").lowercase()

    private fun shortPath(path: String): String =
        if (path.contains('/') || path.contains('\\')) fileName(path) else path

    private fun patchFile(patch: String?): String? = patch?.lineSequence()
        ?.firstOrNull { it.startsWith("*** Update File:") || it.startsWith("*** Add File:") || it.startsWith("+++ ") }
        ?.substringAfter(':')?.removePrefix("+++ ")?.trim()?.let(::fileName)

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').ifBlank { url }

    fun lineCount(text: String): Int = if (text.isBlank()) 0 else text.trimEnd().count { it == '\n' } + 1

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
