package agent

import app.agent.AgentEvent
import app.agent.AgentHarness
import app.agent.SnapshotKey
import app.agent.TribalismAgentHarness
import app.agent.provider.ProviderResponse
import app.agent.provider.ScriptedProvider
import app.agent.sandbox.SandboxBinaries
import app.agent.tool.ToolCall
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@Title("The TribalismAgentHarness — end-to-end against the public API")
@Narrative('''

    These tests drive ONLY the public TribalismAgentHarness surface (VISION.md §9.8): send a turn,
    receive AgentEvents through a listener, manage context, and — the headline — snapshot the agent
    state (immutable context value + a real git commit of the workspace) under a date-time key and
    restore it. The model is mocked with a deterministic ScriptedProvider; everything else is real:
    the agent loop, tool dispatch, the LocalSandboxRuntime (a non-isolated dev double, so no podman is
    needed in CI), and the git-versioned workspace via the resolved git binary.

''')
@CompileDynamic
class AgentHarness_Spec extends Specification {

    @TempDir Path tmp
    Path workspace
    Path gitBinary

    def setup() {
        workspace = tmp.resolve("workspace")
        gitBinary = SandboxBinaries.discover().gitOrThrow()   // bundled if fetched, else git on PATH
    }

    private TribalismAgentHarness harnessWith(ScriptedProvider provider, String systemPrompt = "You are the GM.") {
        def h = AgentHarness.local(provider, workspace, gitBinary, systemPrompt)
        h.start()
        return h
    }

    /** Collects every emitted event for assertions on order/content. */
    private static List<AgentEvent> recorder(TribalismAgentHarness h) {
        def events = new CopyOnWriteArrayList<AgentEvent>()
        h.addListener({ AgentEvent e -> events.add(e) })
        return events
    }

    def 'a plain chat turn streams assistant text back and records it in the transcript.'() {
        given:
            def harness = harnessWith(ScriptedProvider.of(ProviderResponse.text("You see a torch-lit hall.")))
            def events = recorder(harness)
        when:
            harness.send("describe the room").join()
        then: 'the assistant text arrived as an event'
            events.find { it instanceof AgentEvent.AssistantText }?.text() == "You see a torch-lit hall."
        and: 'the transcript holds the user turn and the assistant reply'
            def texts = harness.context().transcript().collect { it.text() }
            texts.contains("describe the room")
            texts.contains("You see a torch-lit hall.")
        cleanup:
            harness.close()
    }

    def 'the loop dispatches a sandbox tool call and feeds the result back to the model.'() {
        given: 'the model first writes a file, then (seeing the tool result) replies'
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("noting that down",
                    ToolCall.of("c1", "write_file").with("path", "notes/plan.md").with("content", "ambush at the bridge")),
                ProviderResponse.text("Saved your plan."))
            def harness = harnessWith(provider)
            def events = recorder(harness)
        when:
            harness.send("remember: ambush at the bridge").join()
        then: 'the file really exists in the workspace'
            Files.readString(workspace.resolve("notes/plan.md")) == "ambush at the bridge"
        and: 'a dispatch + completion event pair was emitted for the tool'
            events.any { it instanceof AgentEvent.ToolCallDispatched && it.call().name() == "write_file" }
            def done = events.find { it instanceof AgentEvent.ToolCompleted }
            done.result().ok()
        and: 'the tool output was fed back into the transcript (a TOOL entry) and the model replied'
            harness.context().transcript().any { it.author().name() == "TOOL" }
            harness.context().transcript().last().text() == "Saved your plan."
        cleanup:
            harness.close()
    }

    def 'the bash tool runs inside the sandbox workspace.'() {
        given:
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("", ToolCall.of("b1", "bash").with("command", "echo hello-sandbox")),
                ProviderResponse.text("ok"))
            def harness = harnessWith(provider)
            def events = recorder(harness)
        when:
            harness.send("run it").join()
        then:
            def done = events.find { it instanceof AgentEvent.ToolCompleted }
            done.result().output().contains("hello-sandbox")
        cleanup:
            harness.close()
    }

    def 'a turn emits TurnStarted ... TurnEnded around its activity, in order.'() {
        given:
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("", ToolCall.of("b1", "bash").with("command", "true")),
                ProviderResponse.text("done"))
            def harness = harnessWith(provider)
            def events = recorder(harness)
        when:
            harness.send("go").join()
        then:
            def names = events.collect { it.class.simpleName }
            names.first() == "TurnStarted"
            names.last()  == "TurnEnded"
            names.indexOf("ToolCallDispatched") < names.indexOf("ToolCompleted")
        cleanup:
            harness.close()
    }

    def 'an unknown tool name yields an error result but the turn still completes.'() {
        given:
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("", ToolCall.of("x1", "no_such_tool")),
                ProviderResponse.text("recovered"))
            def harness = harnessWith(provider)
            def events = recorder(harness)
        when:
            harness.send("oops").join()
        then:
            def done = events.find { it instanceof AgentEvent.ToolCompleted }
            !done.result().ok()
            done.result().output().contains("unknown tool")
            events.any { it instanceof AgentEvent.TurnEnded }
        cleanup:
            harness.close()
    }

    def 'a model that never stops calling tools is bounded by the iteration guard.'() {
        given: 'a provider that always asks for another tool call'
            def provider = ScriptedProvider.from({ ctx, tools ->
                ProviderResponse.withTools("", ToolCall.of("loop", "bash").with("command", "true"))
            })
            def harness = AgentHarness.builder()
                                .provider(provider)
                                .sandbox(new app.agent.sandbox.LocalSandboxRuntime(workspace, new app.agent.sandbox.ProcessRunner()))
                                .workspaceRepo(new app.agent.sandbox.WorkspaceRepo(gitBinary, workspace, new app.agent.sandbox.ProcessRunner()))
                                .maxIterations(3)
                                .build()
            harness.start()
            def events = recorder(harness)
        when:
            harness.send("spin").join()
        then: 'it aborts with an error rather than hanging'
            events.any { it instanceof AgentEvent.ErrorOccurred && it.message().contains("iterations") }
            events.any { it instanceof AgentEvent.TurnEnded }
        cleanup:
            harness.close()
    }

    def 'context management: clear drops the transcript but keeps the system prompt; compact trims it.'() {
        given:
            def harness = harnessWith(ScriptedProvider.echo(), "SYS")
        when: 'a few turns build up history'
            harness.send("one").join()
            harness.send("two").join()
        then:
            harness.context().size() >= 4
        when:
            harness.compactContext(2)
        then:
            harness.context().size() == 2
            harness.context().systemPrompt() == "SYS"
        when:
            harness.clearContext()
        then:
            harness.context().isEmpty()
            harness.context().systemPrompt() == "SYS"
        cleanup:
            harness.close()
    }

    def 'snapshot returns a date-time key; snapshots are listed oldest-first.'() {
        given:
            def harness = harnessWith(ScriptedProvider.echo())
        when:
            def k1 = harness.snapshot("first")
            def k2 = harness.snapshot("second")
        then: 'each key carries a real timestamp and they are ordered in time'
            k1.timestamp() != null
            k1 < k2
            harness.snapshots().toList() == [k1, k2]
            harness.snapshotAt(k1).get().label() == "first"
        cleanup:
            harness.close()
    }

    def 'restore switches BOTH the workspace files and the conversation context back in time.'() {
        given: 'a provider whose write_file content we vary per turn'
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("", ToolCall.of("w1", "write_file").with("path", "state.txt").with("content", "VERSION-1")),
                ProviderResponse.text("v1 stored"),
                ProviderResponse.withTools("", ToolCall.of("w2", "write_file").with("path", "state.txt").with("content", "VERSION-2")),
                ProviderResponse.text("v2 stored"))
            def harness = harnessWith(provider)
        when: 'turn 1 writes v1, we snapshot it, then turn 2 writes v2 and we snapshot that'
            harness.send("store v1").join()
            def k1 = harness.snapshot("after-v1")
            def sizeAtK1 = harness.context().size()
            harness.send("store v2").join()
            def k2 = harness.snapshot("after-v2")
        then: 'right now the live state is v2'
            Files.readString(workspace.resolve("state.txt")) == "VERSION-2"
            harness.context().size() > sizeAtK1
        when: 'we rewind to the first snapshot'
            harness.restore(k1)
        then: 'the workspace file is back to v1 (real git checkout) ...'
            Files.readString(workspace.resolve("state.txt")) == "VERSION-1"
        and: '... and the conversation context is back to its v1 size, with no v2 content'
            harness.context().size() == sizeAtK1
            !harness.context().transcript().collect { it.text() }.contains("v2 stored")
        and: 'k2 is still addressable — we can roll forward again'
            harness.restore(k2)
            Files.readString(workspace.resolve("state.txt")) == "VERSION-2"
        cleanup:
            harness.close()
    }

    def 'restore removes files that were created after the snapshot (faithful rewind).'() {
        given:
            def provider = ScriptedProvider.of(
                ProviderResponse.withTools("", ToolCall.of("a", "write_file").with("path", "a.txt").with("content", "A")),
                ProviderResponse.text("a"),
                ProviderResponse.withTools("", ToolCall.of("b", "write_file").with("path", "b.txt").with("content", "B")),
                ProviderResponse.text("b"))
            def harness = harnessWith(provider)
        when:
            harness.send("make a").join()
            def k1 = harness.snapshot("only-a")
            harness.send("make b").join()
            harness.snapshot("a-and-b")
        then:
            Files.exists(workspace.resolve("b.txt"))
        when:
            harness.restore(k1)
        then:
            Files.exists(workspace.resolve("a.txt"))
            !Files.exists(workspace.resolve("b.txt"))
        cleanup:
            harness.close()
    }

    def 'restoring an unknown key is rejected.'() {
        given:
            def harness = harnessWith(ScriptedProvider.echo())
        when:
            harness.restore(SnapshotKey.at(java.time.Instant.now(), 999))
        then:
            thrown(IllegalArgumentException)
        cleanup:
            harness.close()
    }

    def 'the local dev sandbox reports that it is NOT an isolation boundary.'() {
        given:
            def harness = harnessWith(ScriptedProvider.echo())
        expect:
            !harness.isSandboxIsolated()
        cleanup:
            harness.close()
    }
}
