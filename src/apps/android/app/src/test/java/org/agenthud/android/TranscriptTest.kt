package org.agenthud.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.agenthud.android.data.SessionDetailResponse
import org.agenthud.android.data.SessionMessageItem
import org.agenthud.android.data.SessionToolCall
import org.agenthud.android.data.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    private fun call(id: String, name: String, vararg args: Pair<String, String>) = SessionMessageItem(
        id = id,
        role = "assistant",
        toolCallId = "c$id",
        toolCall = SessionToolCall(
            id = "c$id",
            name = name,
            arguments = buildJsonObject { args.forEach { (k, v) -> put(k, v) } },
        ),
    )

    private fun result(id: String, forCall: String, text: String) =
        SessionMessageItem(id = id, role = "tool", toolCallId = "c$forCall", text = text)

    @Test
    fun groupsToolRunsBetweenProseAndPairsResults() {
        val rows = listOf(
            SessionMessageItem(id = "u1", role = "user", text = "Fix the dock", isPrompt = true),
            SessionMessageItem(id = "a1", role = "assistant", text = "Looking now."),
            call("1", "Read", "file_path" to "C:\\repo\\ui\\AgentHudRoot.kt"),
            call("2", "Grep", "pattern" to "NavTab"),
            result("r1", "1", "1\tpackage x"),
            result("r2", "2", "AgentHudRoot.kt:71: enum class NavTab"),
            SessionMessageItem(id = "n1", role = "user", text = "<system-reminder>…</system-reminder>"),
            call("3", "Edit", "file_path" to "/repo/ui/AgentHudRoot.kt", "old_string" to "a", "new_string" to "b"),
            result("r3", "3", "The file has been updated successfully."),
            SessionMessageItem(id = "a2", role = "assistant", text = "Done."),
        )
        val blocks = Transcript.build(rows)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is Transcript.UserPrompt)
        val first = blocks[1] as Transcript.AssistantText
        assertFalse(first.endsTurn)
        val steps = blocks[2] as Transcript.Steps
        assertEquals(3, steps.toolCount)
        assertEquals(4, steps.steps.size) // the system note rides along inside the group
        val read = steps.steps[0] as Transcript.ToolStep
        assertEquals("1\tpackage x", read.output)
        assertEquals("Read", read.summary.verb)
        assertEquals("AgentHudRoot.kt", read.summary.target)
        assertEquals("Read 1 file, searched once, edited 1 file", Transcript.groupSummary(steps.steps))
        val edit = steps.steps[3] as Transcript.ToolStep
        assertEquals("- a\n+ b", Transcript.detailText(edit))
        assertTrue((blocks[3] as Transcript.AssistantText).endsTurn)
    }

    @Test
    fun orphanResultsAndErrorsAreFlagged() {
        val blocks = Transcript.build(listOf(result("r9", "missing", "<tool_use_error>File not found</tool_use_error>")))
        val step = (blocks.single() as Transcript.Steps).steps.single() as Transcript.ToolStep
        assertTrue(step.failed)
        assertEquals("Tool output", step.summary.verb)
        assertEquals("File not found", Transcript.cleanOutput(step.output))
    }

    @Test
    fun shellStepsPreferDescriptionAndShowCommand() {
        val step = Transcript.ToolStep(
            "s",
            call("4", "Bash", "command" to "./gradlew test", "description" to "Run unit tests"),
            result("r4", "4", "BUILD SUCCESSFUL"),
        )
        assertEquals("Ran", step.summary.verb)
        assertEquals("Run unit tests", step.summary.target)
        assertEquals("$ ./gradlew test\nBUILD SUCCESSFUL", Transcript.detailText(step))
    }

    @Test
    fun findsLocalFileLinksOnly() {
        val links = Transcript.fileLinks(
            "See [Theme.kt](file:///C:/repo/Theme.kt), [docs](https://x.dev) and [Theme.kt](file:///C:/repo/Theme.kt).",
        )
        assertEquals(listOf(Transcript.FileLink("Theme.kt", "file:///C:/repo/Theme.kt")), links)
        assertEquals("Theme.kt", Transcript.fileName("""\\?\C:\repo\Theme.kt"""))
    }

    @Test
    fun decodesDesktopRowsWithToolCalls() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val detail = json.decodeFromString(
            SessionDetailResponse.serializer(),
            """{"messages":[{"id":"x","parentId":null,"role":"assistant","text":null,"timestamp":"2026-11-22T10:00:00Z",
               "toolCallId":"t1","toolCall":{"id":"t1","name":"Bash","arguments":{"command":"ls"}},"isPrompt":false,"turnId":"turn"}]}""",
        )
        val step = (Transcript.build(detail.messages).single() as Transcript.Steps).steps.single() as Transcript.ToolStep
        assertEquals("ls", step.summary.target)
    }

    @Test
    fun parsesJsonEncodedCodexArguments() {
        val codex = SessionMessageItem(
            id = "x",
            role = "assistant",
            toolCallId = "c1",
            toolCall = SessionToolCall(
                id = "c1",
                name = "shell",
                arguments = kotlinx.serialization.json.JsonPrimitive("""{"command":["bash","-lc","ls -la"]}"""),
            ),
        )
        val patch = SessionMessageItem(
            id = "y",
            role = "assistant",
            toolCall = SessionToolCall(
                name = "apply_patch",
                arguments = kotlinx.serialization.json.JsonPrimitive("*** Begin Patch\n*** Update File: src/Main.kt\n+x"),
            ),
        )
        val steps = (Transcript.build(listOf(codex, patch)).single() as Transcript.Steps).steps.map { it as Transcript.ToolStep }
        assertEquals("bash -lc ls -la", steps[0].summary.target)
        assertEquals("Main.kt", steps[1].summary.target)
    }

    @Test
    fun keepsCallAndResultTogetherAcrossNarration() {
        val blocks = Transcript.build(
            listOf(
                call("1", "Read", "file_path" to "a.kt"),
                SessionMessageItem(id = "n", role = "assistant", text = "Reading the file first."),
                result("r1", "1", "contents"),
            ),
        )
        val step = (blocks.first() as Transcript.Steps).steps.single() as Transcript.ToolStep
        assertEquals("contents", step.output)
        assertEquals(2, blocks.size) // the paired result adds no orphan step
    }

    @Test
    fun antigravityThinkingRowsFold() {
        val blocks = Transcript.build(
            listOf(SessionMessageItem(id = "turn7:thinking", role = "assistant", text = "Considering options")),
        )
        assertTrue(blocks.single() is Transcript.Thinking)
    }
}
