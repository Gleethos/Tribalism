package app.agent.tool;

import app.agent.sandbox.SandboxRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 *  The {@code read_file} / {@code write_file} sandbox tools (§9.3). They operate on the workspace
 *  directory — the single host mount shared with the container — through {@code java.nio}, with a hard
 *  <b>containment check</b> so a path can never escape the workspace (no traversal out via {@code ..}
 *  or an absolute path). This keeps file scratch work confined to the agent's playground without going
 *  through the shell, complementing {@link BashTool}.
 */
public final class WorkspaceFileTools {

    private WorkspaceFileTools() {}

    public static final String READ  = "read_file";
    public static final String WRITE = "write_file";

    /** @return both file tools, bound to the given sandbox's workspace. */
    public static AgentTool[] forSandbox( SandboxRuntime sandbox ) {
        Objects.requireNonNull(sandbox, "sandbox");
        return new AgentTool[]{ new Read(sandbox), new Write(sandbox) };
    }

    /** Resolves {@code relativePath} inside {@code root}, refusing anything that escapes it. */
    static Path resolveInside( Path root, String relativePath ) {
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if ( !resolved.startsWith(base) )
            throw new IllegalArgumentException("path escapes the workspace: '" + relativePath + "'");
        return resolved;
    }

    /** Reads a UTF-8 file from the workspace. */
    public static final class Read implements AgentTool {
        private final SandboxRuntime sandbox;
        Read( SandboxRuntime sandbox ) { this.sandbox = sandbox; }

        @Override public ToolSpec spec() {
            return ToolSpec.sandbox(READ, "Read a UTF-8 text file from the agent workspace. Argument: 'path' (relative).");
        }

        @Override public ToolResult invoke( ToolCall call ) {
            String path = call.requireArg("path");
            try {
                Path file = resolveInside(sandbox.workspaceDir(), path);
                if ( !Files.isRegularFile(file) ) return ToolResult.error(call.id(), "no such file: " + path);
                return ToolResult.ok(call.id(), Files.readString(file, StandardCharsets.UTF_8));
            } catch ( IOException e ) {
                return ToolResult.error(call.id(), "could not read '" + path + "': " + e.getMessage());
            }
        }
    }

    /** Writes a UTF-8 file into the workspace, creating parent directories. */
    public static final class Write implements AgentTool {
        private final SandboxRuntime sandbox;
        Write( SandboxRuntime sandbox ) { this.sandbox = sandbox; }

        @Override public ToolSpec spec() {
            return ToolSpec.sandbox(WRITE, "Write a UTF-8 text file into the agent workspace. " +
                                           "Arguments: 'path' (relative), 'content'.");
        }

        @Override public ToolResult invoke( ToolCall call ) {
            String path    = call.requireArg("path");
            String content = call.argOr("content", "");
            try {
                Path file = resolveInside(sandbox.workspaceDir(), path);
                Path parent = file.getParent();
                if ( parent != null ) Files.createDirectories(parent);
                Files.writeString(file, content, StandardCharsets.UTF_8);
                return ToolResult.ok(call.id(), "wrote " + content.length() + " chars to " + path);
            } catch ( IOException e ) {
                return ToolResult.error(call.id(), "could not write '" + path + "': " + e.getMessage());
            }
        }
    }
}
