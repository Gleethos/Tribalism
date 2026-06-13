package agent

import app.agent.sandbox.ProcessRunner
import app.agent.sandbox.SandboxBinaries
import app.agent.sandbox.WorkspaceRepo
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path

@Title("WorkspaceRepo — git-versioning the agent workspace (VISION.md §9.5 / §10.2)")
@Narrative('''

    The workspace half of the agent's time-travel: commits are addressable points in the file history
    and checkout rewinds to them exactly (including removing files added later). Driven through the
    resolved git binary so it works with the bundled git or a host git.

''')
@CompileDynamic
class WorkspaceRepo_Spec extends Specification {

    @TempDir Path tmp
    WorkspaceRepo repo
    Path ws

    def setup() {
        ws = tmp.resolve("ws")
        repo = new WorkspaceRepo(SandboxBinaries.discover().gitOrThrow(), ws, new ProcessRunner())
        repo.initIfNeeded()
    }

    def 'init creates a repo with a root commit.'() {
        expect:
            repo.isInitialized()
            repo.head().isPresent()
            repo.log().size() >= 1
    }

    def 'commit records workspace contents and returns an addressable id; log is newest-first.'() {
        when:
            Files.writeString(ws.resolve("a.txt"), "first")
            def c1 = repo.commit("add a")
            Files.writeString(ws.resolve("a.txt"), "second")
            def c2 = repo.commit("change a")
        then:
            c1 != c2
            repo.head().get() == c2
            def log = repo.log().toList()
            log.first().id() == c2          // newest first
            log*.message().contains("add a")
            log*.message().contains("change a")
    }

    def 'checkout rewinds file content to the chosen commit and back.'() {
        given:
            Files.writeString(ws.resolve("v.txt"), "ONE")
            def c1 = repo.commit("one")
            Files.writeString(ws.resolve("v.txt"), "TWO")
            def c2 = repo.commit("two")
        when:
            repo.checkout(c1)
        then:
            Files.readString(ws.resolve("v.txt")) == "ONE"
        when:
            repo.checkout(c2)
        then:
            Files.readString(ws.resolve("v.txt")) == "TWO"
    }

    def 'checkout removes files that did not exist at that commit.'() {
        given:
            Files.writeString(ws.resolve("keep.txt"), "k")
            def c1 = repo.commit("keep only")
            Files.writeString(ws.resolve("later.txt"), "l")
            repo.commit("add later")
        when:
            repo.checkout(c1)
        then:
            Files.exists(ws.resolve("keep.txt"))
            !Files.exists(ws.resolve("later.txt"))
    }
}
