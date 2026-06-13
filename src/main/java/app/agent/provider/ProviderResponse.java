package app.agent.provider;

import app.agent.tool.ToolCall;
import sprouts.Tuple;

import java.util.Objects;

/**
 *  One reply from the model (§9.4): some assistant {@code text} (possibly empty) and zero or more
 *  {@link ToolCall}s it wants run. The agent loop keeps iterating while a response carries tool calls
 *  and ends the turn on the first response with none.
 */
public record ProviderResponse(
    String           text,
    Tuple<ToolCall>  toolCalls
) {
    public ProviderResponse {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(toolCalls, "toolCalls");
    }

    /** A terminal reply: assistant text, no tool calls (ends the turn). */
    public static ProviderResponse text( String text ) {
        return new ProviderResponse(text, Tuple.of(ToolCall.class));
    }

    /** A reply that requests tool calls (optionally with accompanying text). */
    public static ProviderResponse withTools( String text, ToolCall... calls ) {
        return new ProviderResponse(text, Tuple.of(ToolCall.class, java.util.List.of(calls)));
    }

    public boolean hasToolCalls() { return !toolCalls.isEmpty(); }
}
