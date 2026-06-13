package app.agent.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  A {@link SandboxRuntime} that runs commands directly on the host, in the workspace directory, with
 *  <b>no isolation whatsoever</b>. It exists so the agent loop, the tool dispatch and the
 *  git-snapshot machinery can be unit-tested without a working {@code podman} (per
 *  {@code API_STABILITY.md}: "build behind a … interface; test … against fakes"), and for local
 *  development on machines without the container runtime.
 *  <p>
 *  <b>It is not a security boundary.</b> {@link #isHostBoundary()} returns {@code false}; production
 *  use with an untrusted model must use {@link PodmanSandboxRuntime}. The harness checks this flag
 *  before enabling sandbox tools outside of tests.
 */
public final class LocalSandboxRuntime implements SandboxRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(LocalSandboxRuntime.class);

    private final Path          workspaceDir;
    private final ProcessRunner runner;
    private volatile boolean     running;

    public LocalSandboxRuntime( Path workspaceDir, ProcessRunner runner ) {
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir").toAbsolutePath().normalize();
        this.runner       = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public void start() {
        try {
            Files.createDirectories(workspaceDir);
        } catch ( IOException e ) {
            throw new UncheckedIOException("Could not create workspace " + workspaceDir, e);
        }
        running = true;
        LOG.warn("LocalSandboxRuntime started at {} — NOT isolated; for dev/tests only.", workspaceDir);
    }

    @Override public void stop() { running = false; }

    @Override public boolean isRunning() { return running; }

    @Override public Path workspaceDir() { return workspaceDir; }

    @Override
    public ExecResult exec( List<String> argv, Duration timeout ) {
        if ( !running ) start();
        return runner.run(workspaceDir, Map.of(), timeout, argv);
    }

    @Override public boolean isHostBoundary() { return false; }
}
