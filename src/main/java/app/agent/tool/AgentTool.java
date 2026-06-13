package app.agent.tool;

/**
 *  One callable tool the agent loop can dispatch (§9.3). Implementations hold whatever they need
 *  (a {@code SandboxRuntime} for sandbox tools; a domain service for domain tools) and turn a
 *  {@link ToolCall} into a {@link ToolResult}. They should be side-effecting only through their
 *  injected collaborators and must never throw — wrap failures as {@link ToolResult#error}.
 */
public interface AgentTool {

    ToolSpec spec();

    /** @return The tool's name (its dispatch key); defaults to {@code spec().name()}. */
    default String name() { return spec().name(); }

    ToolResult invoke( ToolCall call );
}
