package app.agent.sandbox;

import sprouts.Tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 *  Git version control over the agent's workspace directory, driven through the bundled {@code git}
 *  binary "from outside" the container (§9.5, §10.2). This is the workspace half of the unified
 *  time-travel: a {@link #commit} is one addressable point in the agent's file history, and
 *  {@link #checkout} rewinds the files to it. The harness pairs each commit with an immutable
 *  {@code AgentContext} snapshot so context and workspace move together (§9.6, §10.2).
 *  <p>
 *  Automatic gc is disabled on this repo ({@code gc.auto 0}) so commits stay reachable after a
 *  rewind even while detached — every snapshot id the harness stored remains checkout-able.
 */
public final class WorkspaceRepo {

    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(60);
    private static final String   AGENT_NAME  = "Tribalism Agent";
    private static final String   AGENT_EMAIL = "agent@tribalism.local";

    private final Path          git;
    private final Path          workspaceDir;
    private final ProcessRunner runner;

    public WorkspaceRepo( Path git, Path workspaceDir, ProcessRunner runner ) {
        this.git          = Objects.requireNonNull(git, "git");
        this.workspaceDir = Objects.requireNonNull(workspaceDir, "workspaceDir").toAbsolutePath().normalize();
        this.runner       = Objects.requireNonNull(runner, "runner");
    }

    /** One commit in the workspace history. */
    public record Commit( String id, String shortId, String message, Instant time ) {
        public Commit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(shortId, "shortId");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(time, "time");
        }
    }

    public Path workspaceDir() { return workspaceDir; }

    public boolean isInitialized() { return Files.isDirectory(workspaceDir.resolve(".git")); }

    /** Initializes the repo (idempotent): {@code git init}, a pinned identity, gc off, and a root commit. */
    public WorkspaceRepo initIfNeeded() {
        if ( isInitialized() ) return this;
        try { Files.createDirectories(workspaceDir); } catch ( java.io.IOException e ) {
            throw new java.io.UncheckedIOException("Could not create workspace " + workspaceDir, e);
        }
        run("init", "-b", "main").stdoutOrThrow();
        run("config", "user.name",  AGENT_NAME).stdoutOrThrow();
        run("config", "user.email", AGENT_EMAIL).stdoutOrThrow();
        run("config", "gc.auto",    "0").stdoutOrThrow();
        run("config", "commit.gpgsign", "false").stdoutOrThrow();
        run("commit", "--allow-empty", "-q", "-m", "root: empty workspace").stdoutOrThrow();
        return this;
    }

    /**
     *  Stages everything in the workspace and records a commit (empty allowed, so a snapshot can be
     *  taken even when nothing changed since the last one).
     *
     * @return the new commit's full id.
     */
    public String commit( String message ) {
        Objects.requireNonNull(message, "message");
        initIfNeeded();
        run("add", "-A").stdoutOrThrow();
        run("commit", "--allow-empty", "-q", "-m", message).stdoutOrThrow();
        return head().orElseThrow(() -> new IllegalStateException("commit produced no HEAD"));
    }

    /**
     *  Rewinds the working tree to the given commit (force; discards uncommitted changes) and removes
     *  files/dirs that did not exist then, so the workspace is an <i>exact</i> match for the snapshot
     *  (faithful time-travel, §10.2). {@code .git} itself is never touched by {@code clean}.
     */
    public void checkout( String commitId ) {
        Objects.requireNonNull(commitId, "commitId");
        run("checkout", "-f", commitId).stdoutOrThrow();
        run("clean", "-fd").stdoutOrThrow();
    }

    /** @return The current {@code HEAD} commit id, if the repo has any commits. */
    public Optional<String> head() {
        ExecResult r = run("rev-parse", "HEAD");
        return r.ok() ? Optional.of(r.stdout().strip()) : Optional.empty();
    }

    /** @return The commit history, newest first. */
    public Tuple<Commit> log() {
        ExecResult r = run("log", "--pretty=format:%H%x1f%h%x1f%ct%x1f%s");
        if ( !r.ok() || r.stdout().isBlank() ) return Tuple.of(Commit.class);
        List<Commit> commits = new ArrayList<>();
        for ( String line : r.stdout().split("\n") ) {
            String[] f = line.split("\u001f", -1);
            if ( f.length < 4 ) continue;
            commits.add(new Commit(f[0], f[1], f[3], Instant.ofEpochSecond(Long.parseLong(f[2]))));
        }
        return Tuple.of(Commit.class, commits);
    }

    private ExecResult run( String... args ) {
        List<String> cmd = new ArrayList<>();
        cmd.add(git.toString());
        cmd.addAll(List.of(args));
        return runner.run(workspaceDir, java.util.Map.of(
            "GIT_TERMINAL_PROMPT", "0",
            "GIT_AUTHOR_NAME", AGENT_NAME, "GIT_AUTHOR_EMAIL", AGENT_EMAIL,
            "GIT_COMMITTER_NAME", AGENT_NAME, "GIT_COMMITTER_EMAIL", AGENT_EMAIL),
            GIT_TIMEOUT, cmd);
    }
}
