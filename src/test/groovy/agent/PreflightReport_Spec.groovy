package agent

import app.agent.sandbox.PodmanSandboxRuntime.PreflightReport
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("PodmanSandboxRuntime.PreflightReport — the rootless-prerequisite diagnostic")
@Narrative('''

    The harness must fail with an actionable message instead of a cryptic podman error when the host is
    missing a rootless-container prerequisite — above all the un-bundleable setuid newuidmap/newgidmap.
    These pure tests pin that diagnostic logic (no podman needed): readiness is the AND of all prereqs,
    and the remediation is distro-aware guidance naming the package to install.

''')
@CompileDynamic
class PreflightReport_Spec extends Specification {

    def 'a fully-provisioned host is ready and needs no remediation.'() {
        given:
            def r = new PreflightReport(true, true, true, true, true, true, true)
        expect:
            r.canRunContainers()
            r.remediation().isEmpty()
    }

    def 'a missing UID-mapping helper makes it not-ready with an install command.'() {
        given: 'everything present except newuidmap/newgidmap (the classic case)'
            def r = new PreflightReport(true, true, true, true, true, false, false)
        expect:
            !r.canRunContainers()
            r.toString().contains("[MISSING]")
            r.remediation() ==~ /(?s).*(uidmap|shadow).*/    // names the OS package, distro-aware
    }

    def 'readiness is the conjunction of every prerequisite.'() {
        expect:
            new PreflightReport(p, c, cr, u, s, nu, ng).canRunContainers() == expected
        where:
            p     | c     | cr    | u     | s     | nu    | ng    || expected
            true  | true  | true  | true  | true  | true  | true  || true
            false | true  | true  | true  | true  | true  | true  || false
            true  | true  | true  | false | true  | true  | true  || false
            true  | true  | true  | true  | false | true  | true  || false
            true  | true  | true  | true  | true  | true  | false || false
    }

    def 'a missing subordinate-ID range is called out specifically.'() {
        given:
            def r = new PreflightReport(true, true, true, true, false, true, true)
        expect:
            !r.canRunContainers()
            r.remediation().toLowerCase().contains("subuid")
    }
}
