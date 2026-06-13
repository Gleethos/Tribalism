package app.agent.tool;

import sprouts.Association;

import java.util.Objects;
import java.util.Optional;

/**
 *  A structured request from the model to run a tool (§9.3): a stable call id (so its
 *  {@link ToolResult} can be matched back), the tool {@code name}, and string-keyed {@code arguments}.
 *  Arguments are a {@link Association} (a persistent map) so a {@code ToolCall} is an immutable value
 *  like everything else the harness records.
 */
public record ToolCall(
    String                       id,
    String                       name,
    Association<String,String>   arguments
) {
    public ToolCall {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(arguments, "arguments");
    }

    /** A call with no arguments. */
    public static ToolCall of( String id, String name ) {
        return new ToolCall(id, name, Association.between(String.class, String.class));
    }

    /** A single-argument call (the common case: a bash command, a file path). */
    public static ToolCall of( String id, String name, String key, String value ) {
        return new ToolCall(id, name, Association.between(String.class, String.class).put(key, value));
    }

    public ToolCall with( String key, String value ) {
        return new ToolCall(id, name, arguments.put(key, value));
    }

    public Optional<String> arg( String key ) { return arguments.get(key); }

    /** @return The named argument, or {@code fallback} if absent. */
    public String argOr( String key, String fallback ) { return arguments.get(key).orElse(fallback); }

    /** @return The named argument or an {@link IllegalArgumentException} naming what's missing. */
    public String requireArg( String key ) {
        return arguments.get(key).orElseThrow(() ->
            new IllegalArgumentException("tool '" + name + "' requires argument '" + key + "'"));
    }
}
