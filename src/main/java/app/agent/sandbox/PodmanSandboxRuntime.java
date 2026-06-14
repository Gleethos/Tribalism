package app.agent.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 *  {@code netavark}, {@code pasta}, {@code fuse-overlayfs}…). They are <b>not</b> at the compile-time
 *  {@code /usr/local} paths podman's defaults expect, so this class <b>generates</b> a
 *  {@code containers.conf} + {@code storage.conf} with absolute paths to the extracted binaries and a
 *  self-contained graph/run root under the app, and points podman at them via {@code CONTAINERS_CONF}/
 *  {@code CONTAINERS_STORAGE_CONF}. The host {@code PATH} is appended so the setuid {@code newuidmap}/
 *  {@code newgidmap} (the one thing that <i>must</i> come from the OS, see {@link #preflight}) are found
 *  when the host provides them.
 */
public final class PodmanSandboxRuntime implements SandboxRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(PodmanSandboxRuntime.class);
    private static final Duration BOOT_TIMEOUT = Duration.ofMinutes(5);

    private final SandboxBinaries binaries;
    private final SandboxConfig   config;
    private final ProcessRunner   runner;
    private final Path            podman;
    private final Path            helpersDir;   // conmon, netavark, aardvark-dns, rootlessport…
    private final Path            binDir;       // crun, runc, pasta, fuse-overlayfs…
    private final Path            stateDir;     // generated configs + isolated storage/runroot
    private final String          containerName;
    private volatile boolean       running;
    private volatile boolean       configsWritten;

    public PodmanSandboxRuntime( SandboxBinaries binaries, SandboxConfig config, ProcessRunner runner ) {
        this.binaries      = Objects.requireNonNull(binaries, "binaries");
        this.config        = Objects.requireNonNull(config, "config");
        this.runner        = Objects.requireNonNull(runner, "runner");
        this.podman        = binaries.podmanOrThrow();
        this.helpersDir    = binaries.podmanHelpersDir().orElseThrow(() ->
                                  new IllegalStateException("bundled podman is missing its helper dir"));
        this.binDir        = binaries.podmanBinDir().orElseThrow(() ->
                                  new IllegalStateException("bundled podman is missing its bin dir"));
        Path ws = config.workspaceDir().toAbsolutePath().normalize();
        Path name = ws.getFileName();
        this.stateDir      = name != null ? ws.resolveSibling(name + ".podman") : ws.resolve(".podman");
        this.containerName = "tribalism-agent-" + Integer.toHexString(ws.hashCode());
    }

    @Override
    public void start() {
        if ( running ) return;
        PreflightReport report = preflight();
        if ( !report.canRunContainers() )
            throw new IllegalStateException("Sandbox cannot start a container:\n" + report);
        try {
            Files.createDirectories(config.workspaceDir());
        } catch ( IOException e ) {
            throw new UncheckedIOException("Could not create workspace " + config.workspaceDir(), e);
        }
        ensureConfigs();
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
        ensureConfigs();
        List<String> cmd = new ArrayList<>();
        cmd.add(podman.toString());
        cmd.addAll(List.of(args));
        return runner.run(podman.getParent(), podmanEnv(), timeout, cmd);
    }

    private Map<String,String> podmanEnv() {
        Map<String,String> env = new HashMap<>();
        env.put("CONTAINERS_CONF", stateDir.resolve("containers.conf").toString());
        env.put("CONTAINERS_STORAGE_CONF", stateDir.resolve("storage.conf").toString());
        env.put("CONTAINERS_HELPER_BINARY_DIR", helpersDir.toString());
        binaries.podmanConfigDir().ifPresent(cfg -> {
            Path reg = cfg.resolve("registries.conf");
            if ( Files.isReadable(reg) ) env.put("CONTAINERS_REGISTRIES_CONF", reg.toString());
        });
        // Bundled binaries first, then the host PATH so the setuid newuidmap/newgidmap (which can only
        // come from the OS, never an unprivileged bundle) are found when present.
        String existing = System.getenv("PATH");
        env.put("PATH", binDir + (existing == null ? "" : File.pathSeparator + existing));
        return env;
    }

    /** Writes the generated {@code containers.conf}/{@code storage.conf} once (idempotent). */
    private synchronized void ensureConfigs() {
        if ( configsWritten ) return;
        try {
            Files.createDirectories(stateDir.resolve("storage"));
            Files.createDirectories(stateDir.resolve("run"));
            Path fuseOverlay = binDir.resolve("fuse-overlayfs");
            Files.writeString(stateDir.resolve("containers.conf"), """
                # generated by PodmanSandboxRuntime — points podman at the bundled helpers
                [engine]
                conmon_path=["%s"]
                helper_binaries_dir=["%s","%s"]
                runtime="crun"

                [engine.runtimes]
                crun=["%s"]
                runc=["%s"]
                """.formatted(helpersDir.resolve("conmon"), helpersDir, binDir,
                              binDir.resolve("crun"), binDir.resolve("runc")));
            Files.writeString(stateDir.resolve("storage.conf"), """
                # generated by PodmanSandboxRuntime — self-contained, bundled fuse-overlayfs
                [storage]
                driver="overlay"
                graphroot="%s"
                runroot="%s"

                [storage.options.overlay]
                %s
                """.formatted(stateDir.resolve("storage"), stateDir.resolve("run"),
                              Files.isExecutable(fuseOverlay) ? "mount_program=\"" + fuseOverlay + "\"" : ""));
            configsWritten = true;
        } catch ( IOException e ) {
            throw new UncheckedIOException("Could not write podman configs under " + stateDir, e);
        }
    }

    /**
     *  Checks the rootless-container prerequisites and returns an actionable report — so the harness can
     *  fail with a precise diagnosis ("install the uidmap package") instead of a cryptic podman error.
     *  This is also what the dev/diagnostics view surfaces.
     */
    public PreflightReport preflight() {
        boolean userns = runner.run(null, Map.of(), Duration.ofSeconds(10),
                            List.of("unshare", "-U", "true")).ok();
        boolean newuidmap = SandboxBinaries.onPath("newuidmap").isPresent();
        boolean newgidmap = SandboxBinaries.onPath("newgidmap").isPresent();
        boolean subid = hasSubIdEntry();
        return new PreflightReport(Files.isExecutable(podman), Files.isExecutable(helpersDir.resolve("conmon")),
                                   Files.isExecutable(binDir.resolve("crun")), userns, subid, newuidmap, newgidmap);
    }

    private static boolean hasSubIdEntry() {
        String user = System.getProperty("user.name", "");
        for ( String f : new String[]{ "/etc/subuid", "/etc/subgid" } ) {
            try {
                Path p = Path.of(f);
                if ( !Files.isReadable(p) ) return false;
                boolean found = Files.readAllLines(p).stream().anyMatch(l -> l.startsWith(user + ":"));
                if ( !found ) return false;
            } catch ( IOException e ) { return false; }
        }
        return true;
    }

    /**
     *  Result of {@link #preflight}. {@link #canRunContainers()} is true only when every prerequisite is
     *  met; otherwise {@link #toString()} explains what's missing and how to fix it.
     */
    public record PreflightReport(
        boolean podmanExecutable, boolean conmonExecutable, boolean crunExecutable,
        boolean userNamespaces, boolean subIdConfigured, boolean newuidmap, boolean newgidmap
    ) {
        public boolean canRunContainers() {
            return podmanExecutable && conmonExecutable && crunExecutable
                && userNamespaces && subIdConfigured && newuidmap && newgidmap;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            line(b, "bundled podman executable", podmanExecutable, null);
            line(b, "bundled conmon executable", conmonExecutable, null);
            line(b, "bundled crun executable",   crunExecutable,   null);
            line(b, "user namespaces enabled",   userNamespaces,   "host kernel must allow unprivileged user namespaces");
            line(b, "subuid/subgid configured",  subIdConfigured,  "add a range for this user to /etc/subuid and /etc/subgid");
            line(b, "newuidmap on PATH",         newuidmap,        "install the OS 'uidmap'/'shadow-utils' package (setuid-root; cannot be shipped unprivileged)");
            line(b, "newgidmap on PATH",         newgidmap,        "install the OS 'uidmap'/'shadow-utils' package");
            return b.toString().stripTrailing();
        }

        private static void line( StringBuilder b, String name, boolean ok, String fix ) {
            b.append(ok ? "  [ok]   " : "  [MISSING] ").append(name);
            if ( !ok && fix != null ) b.append(" — ").append(fix);
            b.append('\n');
        }
    }

    /** @return The container name (stable per workspace) — exposed for diagnostics/tests. */
    public String containerName() { return containerName; }

    /** @return A short banner of the bundled podman version (for the dev/diagnostics view). */
    public String version() {
        ExecResult r = podman(Duration.ofSeconds(20), "--version");
        return r.ok() ? r.stdout().strip() : "podman (version unavailable: " + r.stderr().strip() + ")";
    }

    /** Runs {@code podman info} through the generated config — exercises helper/runtime resolution. */
    public ExecResult info() {
        return podman(Duration.ofSeconds(45), "info");
    }
}
