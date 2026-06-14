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
 *  The real, host-isolated {@link SandboxRuntime} (§9.5): it drives the bundled {@code podman} to run a
 *  long-lived <b>rootless</b> container whose only host mount is the agent's workspace, with network
 *  default-denied, and dispatches every sandbox command as a {@code podman exec} into that container.
 *  The agent thus gets a Linux-like playground and <b>never</b> the host shell (principle 7).
 *
 *  <h2>How a Linux container actually works (so this class is maintainable)</h2>
 *  A container is <i>not</i> a virtual machine — there is no second kernel. The agent's commands run as
 *  ordinary processes on the host kernel, which is told to <i>lie</i> to them about what they can see and
 *  do. Several kernel features combine to produce that illusion:
 *  <ul>
 *    <li><b>Namespaces</b> — per-resource "filtered views". A <i>mount</i> namespace gives the container
 *        its own filesystem tree; a <i>PID</i> namespace its own process numbering; a <i>network</i>
 *        namespace its own interfaces; and — central here — a <i>user</i> namespace its own user-ID
 *        mapping (below).</li>
 *    <li><b>cgroups</b> — caps on how much CPU/memory the container may use.</li>
 *    <li><b>An OCI runtime</b> ({@code crun}) — the low-level tool that performs the namespace/cgroup
 *        setup and execs the program. Bundled.</li>
 *    <li><b>{@code conmon}</b> — a tiny monitor process that holds the container's I/O and reaps its exit
 *        code so it survives the {@code podman} command returning. Bundled.</li>
 *    <li><b>overlayfs / {@code fuse-overlayfs}</b> — stacks the read-only image layer and a writable
 *        layer into one filesystem. Bundled (the fuse variant, for rootless).</li>
 *  </ul>
 *  podman is the conductor that orchestrates these per request.
 *
 *  <h2>Rootless containers and the UID-mapping problem (the crux)</h2>
 *  Historically this setup needed <b>root</b>, which is exactly the ambient authority principle 7
 *  forbids for AI-run commands. <b>Rootless</b> mode avoids it via the <i>user namespace</i>: inside the
 *  container a process can be "root", but that fake root is <i>mapped</i> back to the unprivileged host
 *  user (e.g. host uid 1001) the moment it touches anything real — full power inside the box, none
 *  outside.
 *  <p>
 *  A container needs <b>many</b> internal UIDs (its own {@code root}, {@code nobody}, service users…), so
 *  it must map a <i>range</i> of host UIDs. The host reserves such a range per user in
 *  {@code /etc/subuid} / {@code /etc/subgid}. But <b>allocating a range into a namespace is itself
 *  privileged</b> (letting a user map arbitrary host UIDs would be identity theft). Linux delegates just
 *  that one step to two small <b>setuid-root</b> helpers, {@code newuidmap} / {@code newgidmap}: they run
 *  with root's authority only long enough to verify the user's {@code /etc/subuid} entitlement and write
 *  the mapping — the single, audited escape hatch that makes rootless containers safe.
 *
 *  <h2>What we ship, and the one thing we cannot</h2>
 *  We bundle podman + {@code crun} + {@code conmon} + {@code fuse-overlayfs} + networking helpers
 *  (downloaded & SHA-256-verified by {@code ./gradlew fetchSandboxBinaries}). The static build expects
 *  them at compile-time {@code /usr/local} paths, so this class <b>generates</b> a {@code containers.conf}
 *  + {@code storage.conf} pointing at the actual extracted locations, with a self-contained graph/run
 *  root, wired via {@code CONTAINERS_CONF} / {@code CONTAINERS_STORAGE_CONF}.
 *  <p>
 *  <b>{@code newuidmap}/{@code newgidmap} are the exception: they cannot be shipped.</b> Being setuid-root
 *  means the <i>file</i> must be owned by root and carry the setuid bit — a state only root can establish.
 *  An app unpacked into a user directory cannot grant itself that, by design. So they must come from the
 *  OS: the {@code uidmap} package (Debian/Ubuntu) or {@code shadow-utils} (Fedora/RHEL), part of base on
 *  Fedora/Arch/openSUSE and pulled in automatically when podman is installed via a distro package manager
 *  — which is why <b>Tribalism's installer declares {@code uidmap} as a runtime dependency</b> (we ship
 *  our own podman and thus bypass that automatic pull). The host {@code PATH} is appended to podman's
 *  environment so these OS-provided helpers are found.
 *  <p>
 *  {@link #preflight()} checks every prerequisite and, when one is missing, {@link #start()} logs a
 *  precise, distro-aware remediation (e.g. "run: sudo apt install uidmap") and refuses to start rather
 *  than silently degrading.
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
        if ( !report.canRunContainers() ) {
            // Elaborate, actionable logging — the missing piece is almost always the OS uidmap package,
            // which we deliberately do not (and cannot) bundle. Make the fix obvious in the logs.
            LOG.error("""
                The AI agent sandbox cannot start a rootless container. Prerequisite check:
                {}
                {}""", report, report.remediation());
            throw new IllegalStateException(
                "Sandbox cannot start a container — missing rootless prerequisites.\n" + report +
                "\n" + report.remediation());
        }
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

        /**
         *  A human-facing, distro-aware fix for whatever is missing (empty when ready). The common case is
         *  the absent {@code uidmap}/{@code shadow-utils} package — which our installer declares as a
         *  dependency, so this guidance is the safety net for hosts where it is somehow absent.
         */
        public String remediation() {
            if ( canRunContainers() ) return "";
            StringBuilder b = new StringBuilder("To enable the AI agent sandbox on this host:\n");
            if ( !newuidmap || !newgidmap )
                b.append("  • Install the rootless UID-mapping helpers (setuid-root; not bundleable):\n")
                 .append("      ").append(uidmapInstallCommand()).append('\n');
            if ( !subIdConfigured )
                b.append("  • Reserve a subordinate-ID range for this user, e.g.:\n")
                 .append("      sudo usermod --add-subuids 100000-165535 --add-subgids 100000-165535 ")
                 .append(System.getProperty("user.name", "<user>")).append('\n');
            if ( !userNamespaces )
                b.append("  • Enable unprivileged user namespaces:\n")
                 .append("      sudo sysctl -w kernel.unprivileged_userns_clone=1\n");
            if ( !podmanExecutable || !conmonExecutable || !crunExecutable )
                b.append("  • Re-fetch the bundled container binaries:  ./gradlew fetchSandboxBinaries\n");
            return b.toString().stripTrailing();
        }

        /** Picks the right package-manager command for the host's distro family (best-effort). */
        private static String uidmapInstallCommand() {
            String id = osReleaseField("ID");
            String like = osReleaseField("ID_LIKE");
            String fam = (id + " " + like).toLowerCase(java.util.Locale.ROOT);
            if ( fam.contains("debian") || fam.contains("ubuntu") )           return "sudo apt install uidmap";
            if ( fam.contains("fedora") || fam.contains("rhel") || fam.contains("centos") )
                                                                              return "sudo dnf install shadow-utils";
            if ( fam.contains("suse") )                                       return "sudo zypper install shadow";
            if ( fam.contains("arch") )                                       return "sudo pacman -S shadow";
            if ( fam.contains("alpine") )                                     return "sudo apk add shadow-uidmap";
            return "install your distro's 'uidmap' / 'shadow-utils' package (provides newuidmap/newgidmap)";
        }

        private static String osReleaseField( String key ) {
            try {
                Path p = Path.of("/etc/os-release");
                if ( !Files.isReadable(p) ) return "";
                for ( String l : Files.readAllLines(p) )
                    if ( l.startsWith(key + "=") )
                        return l.substring(key.length() + 1).replace("\"", "").trim();
            } catch ( IOException ignored ) { }
            return "";
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
