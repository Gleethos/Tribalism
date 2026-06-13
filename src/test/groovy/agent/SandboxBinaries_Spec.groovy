package agent

import app.agent.sandbox.OsArch
import app.agent.sandbox.SandboxBinaries
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

@Title("SandboxBinaries — locating the shipped podman + git (VISION.md §9.5)")
@Narrative('''

    The harness ships its own podman/git so it never depends on the host. SandboxBinaries resolves
    them from an explicit bundle, the dev binaries/<platform> dir, or the install dir — and, only for
    git (which runs the workspace repo from outside the container), falls back to a git on PATH. When
    podman is absent it must say so cleanly so the harness can refuse sandbox tools rather than touch
    the host shell.

''')
@CompileDynamic
class SandboxBinaries_Spec extends Specification {

    @TempDir Path tmp

    private static void makeExecutable(Path p) {
        Files.createDirectories(p.parent)
        Files.writeString(p, "#!/bin/sh\necho stub\n")
        def perms = Files.getPosixFilePermissions(p)
        perms.addAll([PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE])
        Files.setPosixFilePermissions(p, perms)
    }

    def 'an explicit bundle with the expected layout resolves both binaries and the helper dirs.'() {
        given: 'a fake bundle mirroring the real podman-static + dugite layout'
            def root = tmp.resolve("bundle")
            makeExecutable(root.resolve("podman/usr/local/bin/podman"))
            makeExecutable(root.resolve("podman/usr/local/lib/podman/conmon"))
            Files.createDirectories(root.resolve("podman/etc/containers"))
            makeExecutable(root.resolve("git/bin/git"))
            def binaries = SandboxBinaries.at(root)
        expect:
            binaries.podman().isPresent()
            binaries.podman().get().fileName.toString() == "podman"
            binaries.git().isPresent()
            binaries.podmanAvailable()
            binaries.podmanHelpersDir().isPresent()
            binaries.podmanBinDir().isPresent()
            binaries.podmanConfigDir().isPresent()
    }

    def 'with no bundle, podman is reported absent (and podmanOrThrow explains how to get it).'() {
        given: 'a discover() pointed at an empty, non-bundle directory via the override property'
            def empty = tmp.resolve("nope")
            Files.createDirectories(empty.resolve(OsArch.current().slug()))
            System.setProperty("tribalism.sandbox.binaries", empty.toString())
            def binaries = SandboxBinaries.discover()
        expect:
            !binaries.podmanAvailable()
            binaries.podman().isEmpty()
        when:
            binaries.podmanOrThrow()
        then:
            def e = thrown(IllegalStateException)
            e.message.contains("fetchSandboxBinaries")
        cleanup:
            System.clearProperty("tribalism.sandbox.binaries")
    }

    def 'git falls back to a git on PATH so the workspace repo always works.'() {
        expect: 'on this dev/CI host a git is on PATH even without a bundle'
            SandboxBinaries.discover().git().isPresent()
    }

    def 'the current platform is detected.'() {
        expect:
            OsArch.current() != null
            OsArch.LINUX_X86_64.slug() == "linux-x86_64"
            OsArch.LINUX_X86_64.isSupported()
    }
}
