package app.agent.provider;

import app.agent.AgentContext;
import app.agent.tool.ToolSpec;
import sprouts.Tuple;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 *  A deterministic {@link AgentProvider} for tests and offline development. Construct it either with a
 *  fixed sequence of {@link ProviderResponse}s (played one per provider call, §9.4) or with a function
 *  of the context — so a test can drive a full multi-step tool-using turn with no network or model.
 *  <p>
 *  When a scripted sequence is exhausted it returns a terminal "(no further response)" text, which
 *  ends the turn safely rather than looping forever.
 */
public final class ScriptedProvider implements AgentProvider {

    private final BiFunction<AgentContext, Tuple<ToolSpec>, ProviderResponse> behavior;

    private ScriptedProvider( BiFunction<AgentContext, Tuple<ToolSpec>, ProviderResponse> behavior ) {
        this.behavior = Objects.requireNonNull(behavior, "behavior");
    }

    /** Plays the given responses in order, one per call; falls back to a terminal text when exhausted. */
    public static ScriptedProvider of( ProviderResponse... responses ) {
        Deque<ProviderResponse> queue = new ArrayDeque<>(List.of(responses));
        return new ScriptedProvider((ctx, tools) ->
            queue.isEmpty() ? ProviderResponse.text("(no further response)") : queue.poll());
    }

    /** Computes each reply from the current context (lets a test branch on transcript contents). */
    public static ScriptedProvider from( BiFunction<AgentContext, Tuple<ToolSpec>, ProviderResponse> behavior ) {
        return new ScriptedProvider(behavior);
    }

    /** Always echoes the last user message back as assistant text (the simplest possible agent). */
    public static ScriptedProvider echo() {
        return new ScriptedProvider((ctx, tools) -> {
            String last = ctx.transcript().isEmpty() ? "" : ctx.transcript().last().text();
            return ProviderResponse.text("echo: " + last);
        });
    }

    @Override
    public ProviderResponse respond( AgentContext context, Tuple<ToolSpec> tools ) {
        return behavior.apply(context, tools);
    }
}
