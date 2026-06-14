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

import java.nio.file.Path

@Title("PodmanSandboxRuntime wiring — bundled-helper resolution & preflight (VISION.md §9.5)")
@Narrative('''

    These run whenever the podman BUNDLE is present (./gradlew fetchSandboxBinaries) — they drive the
    real bundled podman but do NOT need a launchable container, so they validate the Java→podman wiring
    even on hosts that lack the rootless prerequisites. They assert two things our code is responsible
    for: (1) the generated containers.conf/storage.conf make podman find the bundled conmon + crun (no
    "helper not found" errors), and (2) preflight() accurately diagnoses the host's rootless readiness.
    The one prerequisite we cannot ship — the setuid newuidmap/newgidmap — is surfaced, not hidden.

''')
@Requires({ SandboxBinaries.discover().podmanAvailable() })
@CompileDynamic
class PodmanWiring_Spec extends Specification {

    @TempDir Path tmp

    private PodmanSandboxRuntime runtime() {
        new PodmanSandboxRuntime(SandboxBinaries.discover(), SandboxConfig.defaults(tmp.resolve("ws")), new ProcessRunner())
    }

    def 'the generated config makes podman resolve the bundled conmon and crun (no helper-not-found errors).'() {
        when:
            def info = runtime().info()
        then: 'whatever the outcome, podman no longer fails to FIND its helpers/runtime'
            !info.stderr().toLowerCase().contains("working conmon binary")
            !info.stderr().toLowerCase().contains('runtime "crun" not found')
    }

    def 'preflight reports the bundled binaries as runnable and computes container-readiness from all prereqs.'() {
        when:
            def r = runtime().preflight()
        then: 'the parts we ship are present and executable'
            r.podmanExecutable()
            r.conmonExecutable()
            r.crunExecutable()
        and: 'readiness is exactly the conjunction of every prerequisite (incl. the un-shippable newuidmap)'
            r.canRunContainers() == (r.podmanExecutable() && r.conmonExecutable() && r.crunExecutable()
                                     && r.userNamespaces() && r.subIdConfigured() && r.newuidmap() && r.newgidmap())
        and: 'the report is human-readable and names any missing prerequisite'
            r.toString().contains("conmon")
            r.canRunContainers() || r.toString().toLowerCase().contains("missing")
    }
}
