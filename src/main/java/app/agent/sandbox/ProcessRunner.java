package app.agent.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 *  The single place the agent harness shells out to an external process. Everything that runs a
 *  {@code podman} or {@code git} binary — and every sandbox-tool exec — goes through here, so
 *  process handling (stream draining, timeouts, environment) is uniform and auditable.
 *  <p>
 *  This is deliberately small and synchronous: it starts a process, drains stdout and stderr on
 *  separate threads (so a full pipe can never deadlock the child), waits up to a timeout, and
 *  returns an {@link ExecResult}. The harness's own threading (the agent loop, §9.4) sits above it.
 */
public final class ProcessRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessRunner.class);

    private final ExecutorService streamPump = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-process-pump");
        t.setDaemon(true);
        return t;
    });

    /**
     *  Runs {@code command} and returns its result.
     *
     * @param workingDir the working directory, or {@code null} for the JVM's.
     * @param env        extra environment entries layered onto the inherited environment.
     * @param timeout    the wall-clock budget; on expiry the process is destroyed and
     *                   {@link ExecResult#timedOut()} is {@code true}.
     * @param command    the argv (program + args); never empty.
     */
    public ExecResult run( Path workingDir, Map<String,String> env, Duration timeout, List<String> command ) {
        Objects.requireNonNull(env, "env");
        Objects.requireNonNull(timeout, "timeout");
        if ( Objects.requireNonNull(command, "command").isEmpty() )
            throw new IllegalArgumentException("command must not be empty");

        ProcessBuilder pb = new ProcessBuilder(command);
        if ( workingDir != null ) pb.directory(workingDir.toFile());
        pb.environment().putAll(env);

        LOG.debug("exec {} (cwd={}, timeout={})", command, workingDir, timeout);
        Process process;
        try {
            process = pb.start();
        } catch ( IOException e ) {
            return new ExecResult(-1, "", "failed to start " + command.get(0) + ": " + e.getMessage(), false);
        }

        Future<String> out = streamPump.submit(() -> drain(process.getInputStream()));
        Future<String> err = streamPump.submit(() -> drain(process.getErrorStream()));

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new ExecResult(-1, "", "interrupted while waiting for " + command.get(0), false);
        }

        if ( !finished ) {
            process.destroyForcibly();
            return new ExecResult(-1, safeGet(out), safeGet(err) + "\n[timed out after " + timeout + "]", true);
        }
        return new ExecResult(process.exitValue(), safeGet(out), safeGet(err), false);
    }

    private static String drain( InputStream in ) {
        try ( in ) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch ( IOException e ) {
            return "";
        }
    }

    private static String safeGet( Future<String> f ) {
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch ( Exception e ) {
            return "";
        }
    }
}
