package app.agent.sandbox;

import java.util.Locale;

/**
 *  The host platform the app is running on, used to pick the right bundled binary directory
 *  (see {@link SandboxBinaries}). The bundled binaries live under {@code binaries/<slug>/} (dev)
 *  or {@code <dataDir>/bin/<slug>/} (installed), where {@code <slug>} is {@link #slug()}.
 *  <p>
 *  Per {@code VISION.md} §9.5 the sandbox currently targets <b>Linux x86-64</b> only; the other
 *  constants exist so resolution fails with a clear message rather than silently mis-resolving on
 *  an unsupported host.
 */
public enum OsArch {
    LINUX_X86_64("linux-x86_64"),
    LINUX_ARM64("linux-arm64"),
    MAC_X86_64("mac-x86_64"),
    MAC_ARM64("mac-arm64"),
    WINDOWS_X86_64("windows-x86_64"),
    UNKNOWN("unknown");

    private final String slug;

    OsArch( String slug ) { this.slug = slug; }

    /** @return The directory-name slug for this platform (e.g. {@code "linux-x86_64"}). */
    public String slug() { return slug; }

    /** @return Whether the sandbox is currently supported on this platform (Linux x86-64 only, §9.5). */
    public boolean isSupported() { return this == LINUX_X86_64; }

    /** @return The platform this JVM is running on, or {@link #UNKNOWN} if it can't be classified. */
    public static OsArch current() {
        String os   = System.getProperty("os.name",  "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x86 = arch.equals("amd64") || arch.equals("x86_64");
        boolean arm = arch.equals("aarch64") || arch.equals("arm64");
        if ( os.contains("linux") )                       return x86 ? LINUX_X86_64 : arm ? LINUX_ARM64 : UNKNOWN;
        if ( os.contains("mac") || os.contains("darwin") ) return x86 ? MAC_X86_64   : arm ? MAC_ARM64   : UNKNOWN;
        if ( os.contains("win") )                          return x86 ? WINDOWS_X86_64 : UNKNOWN;
        return UNKNOWN;
    }
}
