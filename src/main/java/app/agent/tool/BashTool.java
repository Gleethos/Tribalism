package app.agent.tool;

import app.agent.sandbox.ExecResult;
import app.agent.sandbox.SandboxRuntime;

import java.time.Duration;
import java.util.Objects;

/**
 *  The {@code bash} sandbox tool (§9.3): runs a shell command <b>inside the container</b> via the
 *  {@link SandboxRuntime}, never on the host. Its output (stdout, plus stderr and a non-zero exit code
 *  when relevant) is returned to the model so it can iterate.
 */
public final class BashTool implements AgentTool {

    public static final String NAME = "bash";

    private final SandboxRuntime sandbox;
    private final Duration       timeout;

    public BashTool( SandboxRuntime sandbox, Duration timeout ) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public BashTool( SandboxRuntime sandbox ) { this(sandbox, Duration.ofMinutes(2)); }

    @Override
    public ToolSpec spec() {
        return ToolSpec.sandbox(NAME, "Run a shell command in the agent's sandboxed Linux workspace. " +
                                      "Argument: 'command' (the shell script). Returns combined output.");
    }

    @Override
    public ToolResult invoke( ToolCall call ) {
        String command = call.arg("command").or(() -> call.arg("script")).orElse("").strip();
        if ( command.isEmpty() )
            return ToolResult.error(call.id(), "bash requires a 'command' argument");

        ExecResult r = sandbox.bash(command, timeout);
        StringBuilder out = new StringBuilder(r.stdout());
        if ( !r.stderr().isBlank() ) out.append(out.isEmpty() ? "" : "\n").append("[stderr] ").append(r.stderr().strip());
        if ( !r.ok() && !r.timedOut() ) out.append("\n[exit ").append(r.exitCode()).append("]");
        String text = out.toString().strip();
        return r.timedOut()
            ? ToolResult.error(call.id(), (text.isEmpty() ? "" : text + "\n") + "[command timed out after " + timeout + "]")
            : new ToolResult(call.id(), r.ok(), text.isEmpty() ? "(no output)" : text);
    }
}
