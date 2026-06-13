package app.agent.sandbox;

import java.util.Objects;

/**
 *  The outcome of running one command — either on the host (a {@code podman}/{@code git} invocation)
 *  or inside the container (a sandbox tool, §9.3). An immutable value: the exit code, the captured
 *  stdout/stderr, and whether the run was killed for exceeding its timeout.
 */
public record ExecResult(
    int     exitCode,
    String  stdout,
    String  stderr,
    boolean timedOut
) {
    public ExecResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    /** @return Whether the command finished normally with a zero exit code. */
    public boolean ok() { return exitCode == 0 && !timedOut; }

    /** @return stdout if the command succeeded; otherwise an {@link IllegalStateException} describing the failure. */
    public String stdoutOrThrow() {
        if ( ok() ) return stdout;
        throw new IllegalStateException(
            (timedOut ? "command timed out" : "command failed (exit " + exitCode + ")") +
            (stderr.isBlank() ? "" : ": " + stderr.strip()));
    }
}
