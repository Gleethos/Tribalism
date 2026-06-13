package app.agent.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  The real, host-isolated {@link SandboxRuntime} (§9.5): it drives the bundled {@code podman} to run
 *  a long-lived container whose only host mount is the agent's workspace, with network default-denied,
 *  and dispatches every sandbox command as a {@code podman exec} into that container. The agent thus
 *  gets a Linux-like playground and <b>never</b> the host shell (principle 7).
 *  <p>
 *  The static podman build ships its own OCI runtime and helpers ({@code crun}, {@code conmon},
 *  {@code netavark}, {@code pasta}…); since it is not installed at the usual system paths, we point
 *  podman at the bundled copies via {@code PATH} and {@code CONTAINERS_HELPER_BINARY_DIR} and at the
 *  bundled {@code containers.conf}/{@code policy.json} via {@code CONTAINERS_CONF}/registries env.
 */
public final class PodmanSandboxRuntime implements SandboxRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(PodmanSandboxRuntime.class);
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(5);

    private final SandboxBinaries binaries;
    private final SandboxConfig   config;
    private final ProcessRunner   runner;
    private final Path            podman;
    private final String          containerName;
    private volatile boolean       running;

    public PodmanSandboxRuntime( SandboxBinaries binaries, SandboxConfig config, ProcessRunner runner ) {
        this.binaries      = Objects.requireNonNull(binaries, "binaries");
        this.config        = Objects.requireNonNull(config, "config");
        this.runner        = Objects.requireNonNull(runner, "runner");
        this.podman        = binaries.podmanOrThrow();
        this.containerName = "tribalism-agent-" + Integer.toHexString(
                                  config.workspaceDir().toAbsolutePath().normalize().hashCode());
    }

    @Override
    public void start() {
        if ( running ) return;
        try {
            Files.createDirectories(config.workspaceDir());
        } catch ( IOException e ) {
            throw new UncheckedIOException("Could not create workspace " + config.workspaceDir(), e);
        }
        // A previous run may have left the container; remove it so the volume mount is fresh.
        podman("rm", "-f", "-i", containerName);

        List<String> run = new ArrayList<>(List.of(
            "run", "-d", "--name", containerName,
            "--network", config.networkEnabled() ? "slirp4netns" : "none",
            "-v", config.workspaceDir().toAbsolutePath() + ":/workspace:Z",
            "-w", "/workspace",
            config.image(),
            "tail", "-f", "/dev/null"));
        ExecResult r = podman(run.toArray(String[]::new));
        if ( !r.ok() )
            throw new IllegalStateException("Failed to start sandbox container: " + r.stderr().strip());
        running = true;
        LOG.info("sandbox container {} up (image={}, network={})", containerName, config.image(), config.networkEnabled());
    }

    @Override
    public void stop() {
        if ( !running ) return;
        podman("rm", "-f", "-i", containerName);
        running = false;
    }

    @Override public boolean isRunning() { return running; }

    @Override public Path workspaceDir() { return config.workspaceDir(); }

    @Override
    public ExecResult exec( List<String> argv, Duration timeout ) {
        if ( !running ) start();
        List<String> cmd = new ArrayList<>(List.of("exec", "-w", "/workspace", containerName));
        cmd.addAll(argv);
        return podman(timeout, cmd.toArray(String[]::new));
    }

    @Override public boolean isHostBoundary() { return true; }

    /* ---- podman invocation with bundled helpers wired in ---- */

    private ExecResult podman( String... args ) {
        return podman(BOOT_TIMEOUT, args);
    }

    private ExecResult podman( Duration timeout, String... args ) {
        List<String> cmd = new ArrayList<>();
        cmd.add(podman.toString());
        cmd.addAll(List.of(args));
        return runner.run(podman.getParent(), podmanEnv(), timeout, cmd);
    }

    private Map<String,String> podmanEnv() {
        Map<String,String> env = new HashMap<>();
        binaries.podmanHelpersDir().ifPresent(d -> env.put("CONTAINERS_HELPER_BINARY_DIR", d.toString()));
        binaries.podmanBinDir().ifPresent(bin -> {
            String existing = System.getenv("PATH");
            env.put("PATH", bin + (existing == null ? "" : File.pathSeparator + existing));
        });
        binaries.podmanConfigDir().ifPresent(cfg -> {
            Path conf = cfg.resolve("containers.conf");
            if ( Files.isReadable(conf) ) env.put("CONTAINERS_CONF", conf.toString());
            Path reg = cfg.resolve("registries.conf");
            if ( Files.isReadable(reg) ) env.put("CONTAINERS_REGISTRIES_CONF", reg.toString());
            writePolicyHint(cfg);
        });
        return env;
    }

    /** podman reads {@code policy.json} from a config path, not an env var; surface the bundled one for diagnostics. */
    private void writePolicyHint( Path configDir ) {
        Path policy = configDir.resolve("policy.json");
        if ( !Files.isReadable(policy) )
            LOG.debug("no bundled policy.json under {}", configDir);
    }

    /** @return The container name (stable per workspace) — exposed for diagnostics/tests. */
    public String containerName() { return containerName; }

    /** @return A short banner of the bundled podman version (for the dev/diagnostics view). */
    public String version() {
        ExecResult r = podman(Duration.ofSeconds(20), "--version");
        return r.ok() ? r.stdout().strip() : "podman (version unavailable: " + r.stderr().strip() + ")";
    }

    @SuppressWarnings("unused") // kept for symmetry with future stdin-bearing execs
    private static byte[] utf8( String s ) { return s.getBytes(StandardCharsets.UTF_8); }
}
