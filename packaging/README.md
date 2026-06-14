# Packaging — OS runtime dependencies

Tribalism ships its **own** podman + git (downloaded & SHA-256-verified by
`./gradlew fetchSandboxBinaries`, see `VISION.md` §9.5/§9.8). Those are plain binaries we can bundle.

There is **one** thing we cannot bundle and that the OS must provide: the **setuid-root** UID-mapping
helpers `newuidmap` / `newgidmap`. They are what lets the AI agent's sandbox run as a *rootless*
container — mapping a range of subordinate UIDs into the container's user namespace — without giving the
container plumbing real root (principle 7). Being setuid-root, the binary file must be owned by root and
carry the setuid bit, a state only a privileged install can establish; an app unpacked into a user
directory cannot grant it. See the class doc on `app.agent.sandbox.PodmanSandboxRuntime` for the full
explanation, and its `preflight()` for the runtime check.

When podman is installed via a distro's own package manager, that package pulls these helpers in
automatically. Because we ship our own podman we bypass that, so **our native installer must declare the
dependency itself**:

| Distro family   | Package        | Provides                     |
|-----------------|----------------|------------------------------|
| Debian / Ubuntu | `uidmap`       | `newuidmap`, `newgidmap`     |
| Fedora / RHEL   | `shadow-utils` | (base; declared for safety)  |
| Arch            | `shadow`       | (base)                       |
| openSUSE        | `shadow`       | (base)                       |
| Alpine          | `shadow-uidmap`| (add-on; busybox by default) |

The templates in this directory carry these declarations:
- `linux/debian/control.in` — Debian/Ubuntu `.deb` (`Depends: uidmap`).
- `linux/rpm/tribalism.spec.in` — Fedora/RHEL `.rpm` (`Requires: shadow-utils`).

The canonical list also lives in `build.gradle` as `ext.osRuntimeDependencies`, so a future packaging
task (jpackage / nebula-ospackage / fpm) has a single source of truth. If the helpers are still missing
at runtime, `PodmanSandboxRuntime.start()` logs a distro-aware `sudo apt install uidmap`-style fix and
refuses to start rather than silently degrading.
