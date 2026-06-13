package app.agent.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 *  The agent's command-execution environment — the thing the sandbox tools (§9.3) dispatch into. It
 *  is an SPI so the harness loop can be exercised against a fake: {@link PodmanSandboxRuntime} is the
 *  real, host-isolated implementation; {@link LocalSandboxRuntime} is a dev/test double that runs on
 *  the host and is <b>explicitly not a security boundary</b>.
 *  <p>
 *  Whichever the implementation, the <b>workspace directory</b> ({@link #workspaceDir()}) is a host
 *  path that the {@link WorkspaceRepo} version-controls with git "from outside" (§9.5) — so snapshots
 *  of the agent's files work identically regardless of runtime.
 */
public interface SandboxRuntime extends AutoCloseable {

    /** Brings the environment up (pulls/creates the container, ensures the workspace exists). Idempotent. */
    void start();

    /** Tears the environment down. Idempotent; the workspace directory and its git history survive. */
    void stop();

    boolean isRunning();

    /** @return The host path of the agent's workspace (mounted into the container; git-versioned). */
    Path workspaceDir();

    /**
     *  Runs a command inside the sandbox with the workspace as the working directory and returns its
     *  result. For {@link PodmanSandboxRuntime} this is {@code podman exec} into the container; for the
     *  local double it is a plain host process in the workspace dir.
     */
    ExecResult exec( List<String> argv, Duration timeout );

    /** Convenience: run a shell script (sandbox tool {@code bash}, §9.3). */
    default ExecResult bash( String script, Duration timeout ) {
        return exec(List.of("/bin/sh", "-c", script), timeout);
    }

    /**
     *  @return Whether this runtime is a real host isolation boundary. {@code false} means it is the
     *  dev/test double and must never be used to run untrusted agent output in production.
     */
    boolean isHostBoundary();

    @Override
    default void close() { stop(); }
}
