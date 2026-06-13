package agent

import app.agent.sandbox.PodmanSandboxRuntime
import app.agent.sandbox.ProcessRunner
import app.agent.sandbox.SandboxBinaries
import app.agent.sandbox.SandboxConfig
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.time.Duration
import java.nio.file.Path

@Title("PodmanSandboxRuntime — the real isolation boundary (VISION.md §9.5)")
@Narrative('''

    The genuine sandbox: a podman container whose only host mount is the workspace, dispatched to via
    'podman exec'. Running a real rootless container needs more than the binary — it needs a capable
    host (subuid/subgid maps, cgroups, a working conmon/OCI-runtime exec, an image to pull). So this
    heavyweight integration test is opt-in: it runs only when the bundle is present AND
    -Dtribalism.podman.it=true is set (./gradlew test -Dtribalism.podman.it=true after
    fetchSandboxBinaries on a provisioned host). Otherwise it skips, keeping CI green while the rest of
    the harness is fully exercised against the LocalSandboxRuntime double.

''')
@Requires({ System.getProperty('tribalism.podman.it') == 'true' && SandboxBinaries.discover().podmanAvailable() })
@CompileDynamic
class PodmanSandboxRuntime_Spec extends Specification {

    @TempDir Path tmp

    def 'a command runs inside the container, not on the host, and the workspace is the cwd.'() {
        given:
            def binaries = SandboxBinaries.discover()
            def config = SandboxConfig.defaults(tmp.resolve("ws"))
            def runtime = new PodmanSandboxRuntime(binaries, config, new ProcessRunner())
        when:
            runtime.start()
            def whoami = runtime.bash("pwd && echo isolated-\$\$", Duration.ofMinutes(2))
        then:
            runtime.isRunning()
            runtime.isHostBoundary()
            whoami.ok()
            whoami.stdout().contains("/workspace")
        cleanup:
            runtime?.close()
    }
}
