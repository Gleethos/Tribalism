package app.agent.sandbox;

import java.nio.file.Path;
import java.util.Objects;

/**
 *  How the agent's sandbox is provisioned (§9.5): where its <b>workspace</b> lives on the host (the
 *  one explicit volume mounted into the container, and the directory the {@link WorkspaceRepo}
 *  git-versions), which container <b>image</b> backs it, and whether <b>network</b> is permitted
 *  (default-deny per principle 7).
 */
public record SandboxConfig(
    Path    workspaceDir,
    String  image,
    boolean networkEnabled
) {
    public SandboxConfig {
        Objects.requireNonNull(workspaceDir, "workspaceDir");
        Objects.requireNonNull(image, "image");
    }

    /** A locked-down default: the given workspace, a small base image, and no network. */
    public static SandboxConfig defaults( Path workspaceDir ) {
        return new SandboxConfig(workspaceDir, "docker.io/library/alpine:3.20", false);
    }

    public SandboxConfig withNetwork( boolean enabled ) {
        return new SandboxConfig(workspaceDir, image, enabled);
    }

    public SandboxConfig withImage( String image ) {
        return new SandboxConfig(workspaceDir, image, networkEnabled);
    }
}
