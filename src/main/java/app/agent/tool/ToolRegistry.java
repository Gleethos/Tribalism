package app.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sprouts.Tuple;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  The set of tools available to the agent loop, keyed by name (§9.3). The loop asks it for
 *  {@link #specs()} to advertise to the provider, then routes each {@link ToolCall} through
 *  {@link #dispatch}. An unknown tool name yields an error {@link ToolResult} rather than throwing,
 *  so a hallucinated call can't crash a turn.
 */
public final class ToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String,AgentTool> tools = new LinkedHashMap<>();

    /** An empty registry. */
    public static ToolRegistry empty() { return new ToolRegistry(); }

    /** A registry pre-populated with the given tools (later wins on name clash). */
    public static ToolRegistry of( AgentTool... tools ) {
        ToolRegistry registry = new ToolRegistry();
        for ( AgentTool t : tools ) registry.register(t);
        return registry;
    }

    public ToolRegistry register( AgentTool tool ) {
        Objects.requireNonNull(tool, "tool");
        if ( tools.put(tool.name(), tool) != null )
            LOG.warn("tool '{}' was registered twice; the later one wins", tool.name());
        return this;
    }

    public boolean has( String name ) { return tools.containsKey(name); }

    public int size() { return tools.size(); }

    /** @return The specs of all registered tools, in registration order. */
    public Tuple<ToolSpec> specs() {
        List<ToolSpec> specs = new ArrayList<>();
        for ( AgentTool t : tools.values() ) specs.add(t.spec());
        return Tuple.of(ToolSpec.class, specs);
    }

    /** Routes a call to its tool, returning an error result if the name is unknown or the tool throws. */
    public ToolResult dispatch( ToolCall call ) {
        Objects.requireNonNull(call, "call");
        AgentTool tool = tools.get(call.name());
        if ( tool == null )
            return ToolResult.error(call.id(), "unknown tool: '" + call.name() + "'");
        try {
            return tool.invoke(call);
        } catch ( RuntimeException e ) {
            LOG.warn("tool '{}' threw", call.name(), e);
            return ToolResult.error(call.id(), "tool '" + call.name() + "' failed: " + e.getMessage());
        }
    }
}
