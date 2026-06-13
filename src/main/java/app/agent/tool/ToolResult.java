package app.agent.tool;

import java.util.Objects;

/**
 *  The outcome of a {@link ToolCall} (§9.3), addressed back to it by {@code callId}. The {@code output}
 *  is fed into the agent's transcript as the tool's reply so the model can react on the next turn.
 */
public record ToolResult(
    String  callId,
    boolean ok,
    String  output
) {
    public ToolResult {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(output, "output");
    }

    public static ToolResult ok( String callId, String output )    { return new ToolResult(callId, true, output); }
    public static ToolResult error( String callId, String message ) { return new ToolResult(callId, false, message); }
}
