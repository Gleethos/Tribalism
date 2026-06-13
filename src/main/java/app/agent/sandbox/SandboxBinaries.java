package app.agent.sandbox;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

/**
 *  Locates the {@code podman} and {@code git} executables the harness shells out to (§9.5). The app
 *  <b>ships its own</b> copies so the agent never depends on — and is never confused by — whatever is
 *  on the host. The Gradle {@code fetchSandboxBinaries} task downloads and SHA-256-verifies them into
 *  a per-platform bundle directory; this class finds that directory.
 *  <p>
 *  Resolution order for the bundle root (first hit wins):
 *  <ol>
 *    <li>the {@code tribalism.sandbox.binaries} system property (explicit override / tests),</li>
 *    <li>{@code ./binaries/<platform>} relative to the working dir (the dev layout the build writes),</li>
 *    <li>{@code <user.home>/.tribalism/bin/<platform>} (the installed layout).</li>
 *  </ol>
 *  When a binary is absent this reports it cleanly (via {@link #podman()} returning empty) so the
 *  harness can <b>refuse sandbox tools rather than silently fall back to the host shell</b> — the
 *  security posture of principle 7. {@code git}, which runs on the host workspace dir from outside the
 *  container, additionally falls back to a {@code git} on {@code PATH} (see {@link #git()}).
 */
public final class SandboxBinaries {

    private static final Logger LOG = LoggerFactory.getLogger(SandboxBinaries.class);

    private final OsArch        platform;
    private final @Nullable Path bundleRoot;   // <root>/<platform>, or null if none was found

    private SandboxBinaries( OsArch platform, @Nullable Path bundleRoot ) {
        this.platform   = platform;
        this.bundleRoot = bundleRoot;
    }

    /** Discovers the bundle for the current platform using the standard search order. */
    public static SandboxBinaries discover() {
        return discover(OsArch.current());
    }

    static SandboxBinaries discover( OsArch platform ) {
        for ( Path candidate : new Path[]{
                propertyRoot(platform), Paths.get("binaries", platform.slug()),
                Paths.get(System.getProperty("user.home", "."), ".tribalism", "bin", platform.slug()) } ) {
            if ( candidate != null && Files.isDirectory(candidate) ) {
                LOG.debug("sandbox binaries bundle for {} found at {}", platform.slug(), candidate);
                return new SandboxBinaries(platform, candidate.toAbsolutePath().normalize());
            }
        }
        LOG.info("no bundled sandbox binaries for {} (run ./gradlew fetchSandboxBinaries)", platform.slug());
        return new SandboxBinaries(platform, null);
    }

    /** Uses an explicit bundle root (the directory that directly contains {@code podman/} and {@code git/}). */
    public static SandboxBinaries at( Path bundleRoot ) {
        return new SandboxBinaries(OsArch.current(), Objects.requireNonNull(bundleRoot).toAbsolutePath().normalize());
    }

    public OsArch platform() { return platform; }

    /** @return The bundled {@code podman} executable, if the bundle is present. Never falls back to the host. */
    public Optional<Path> podman() {
        return executable(bundleRoot, "podman", "usr", "local", "bin", "podman");
    }

    /**
     *  The bundled podman's helper-binary directory ({@code conmon}, {@code netavark}, {@code aardvark-dns},
     *  {@code rootlessport}…). The static podman build expects these at a fixed install path; we point it
     *  here via {@code CONTAINERS_HELPER_BINARY_DIR} (see {@link PodmanSandboxRuntime}).
     */
    public Optional<Path> podmanHelpersDir() {
        if ( bundleRoot == null ) return Optional.empty();
        Path dir = bundleRoot.resolve(Paths.get("podman", "usr", "local", "lib", "podman"));
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    /** The directory holding podman's own OCI-runtime helpers ({@code crun}, {@code runc}, {@code pasta}…). */
    public Optional<Path> podmanBinDir() {
        if ( bundleRoot == null ) return Optional.empty();
        Path dir = bundleRoot.resolve(Paths.get("podman", "usr", "local", "bin"));
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    /** The bundled containers config dir ({@code policy.json}, {@code registries.conf}, {@code storage.conf}…). */
    public Optional<Path> podmanConfigDir() {
        if ( bundleRoot == null ) return Optional.empty();
        Path dir = bundleRoot.resolve(Paths.get("podman", "etc", "containers"));
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    /**
     *  @return The {@code git} executable: the bundled copy if present, else a {@code git} on {@code PATH}.
     *  Git is allowed a host fallback because it operates on the workspace dir <i>from outside</i> the
     *  container (§9.5) — it is version control, not the sandbox boundary.
     */
    public Optional<Path> git() {
        return executable(bundleRoot, "git", "bin", "git").or(() -> onPath("git"));
    }

    public boolean podmanAvailable() { return podman().isPresent(); }

    public Path podmanOrThrow() {
        return podman().orElseThrow(() -> new IllegalStateException(
            "No bundled podman for " + platform.slug() + ". Run ./gradlew fetchSandboxBinaries " +
            "(sandbox tools are disabled without it; the agent never uses the host shell)."));
    }

    public Path gitOrThrow() {
        return git().orElseThrow(() -> new IllegalStateException(
            "No git found (neither bundled for " + platform.slug() + " nor on PATH)."));
    }

    private static Optional<Path> executable( @Nullable Path bundleRoot, String tool, String... relativeParts ) {
        if ( bundleRoot == null ) return Optional.empty();
        Path exe = bundleRoot.resolve(Path.of(tool, relativeParts));
        return Files.isExecutable(exe) ? Optional.of(exe) : Optional.empty();
    }

    private static @Nullable Path propertyRoot( OsArch platform ) {
        String prop = System.getProperty("tribalism.sandbox.binaries");
        return prop == null || prop.isBlank() ? null : Paths.get(prop, platform.slug());
    }

    /** Scans {@code PATH} for an executable named {@code name}. */
    static Optional<Path> onPath( String name ) {
        String path = System.getenv("PATH");
        if ( path == null ) return Optional.empty();
        for ( String dir : path.split(java.io.File.pathSeparator) ) {
            if ( dir.isBlank() ) continue;
            Path exe = Paths.get(dir, name);
            if ( Files.isExecutable(exe) ) return Optional.of(exe);
        }
        return Optional.empty();
    }
}
