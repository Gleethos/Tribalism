package app.agent;

import app.agent.provider.AgentProvider;
import app.agent.provider.ProviderResponse;
import app.agent.sandbox.LocalSandboxRuntime;
import app.agent.sandbox.ProcessRunner;
import app.agent.sandbox.SandboxBinaries;
import app.agent.sandbox.SandboxConfig;
import app.agent.sandbox.PodmanSandboxRuntime;
import app.agent.sandbox.SandboxRuntime;
import app.agent.sandbox.WorkspaceRepo;
import app.agent.tool.BashTool;
import app.agent.tool.ToolCall;
import app.agent.tool.ToolRegistry;
import app.agent.tool.ToolResult;
import app.agent.tool.WorkspaceFileTools;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sprouts.Tuple;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 *  The default {@link TribalismAgentHarness}: it wires a {@link AgentProvider}, a {@link ToolRegistry},
 *  a {@link SandboxRuntime} + {@link WorkspaceRepo}, and a {@link SnapshotStore} into the §9.4 loop, and
 *  serializes every turn and every state change on a single thread so the agent is never mid-turn while
 *  a snapshot or restore happens.
 *
 *  <p>Build it with {@link #builder()}, or use a convenience factory: {@link #local} (a host-isolation-
 *  free dev/test stack) or {@link #podman} (the real sandbox).
 */
public final class AgentHarness implements TribalismAgentHarness {

    private static final Logger LOG = LoggerFactory.getLogger(AgentHarness.class);

    private final AgentProvider  provider;
    private final ToolRegistry   tools;
    private final SandboxRuntime sandbox;
    private final WorkspaceRepo  repo;
    private final SnapshotStore  snapshots;
    private final int            maxIterations;

    private final CopyOnWriteArrayList<AgentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tribalism-agent-loop");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong turnCounter = new AtomicLong();
    private final AtomicLong snapshotSeq = new AtomicLong();

    private volatile AgentContext context;
    private volatile boolean      started;
    private volatile boolean      closed;

    private AgentHarness( Builder b ) {
        this.provider      = Objects.requireNonNull(b.provider, "provider");
        this.sandbox       = Objects.requireNonNull(b.sandbox, "sandbox");
        this.repo          = Objects.requireNonNull(b.repo, "repo");
        this.tools         = b.tools != null ? b.tools : defaultTools(sandbox);
        this.snapshots     = b.snapshots != null ? b.snapshots : new InMemorySnapshotStore();
        this.maxIterations = b.maxIterations;
        this.context       = AgentContext.of(b.systemPrompt);
    }

    private static ToolRegistry defaultTools( SandboxRuntime sandbox ) {
        ToolRegistry registry = ToolRegistry.of(new BashTool(sandbox));
        for ( var t : WorkspaceFileTools.forSandbox(sandbox) ) registry.register(t);
        return registry;
    }

    /* ---- lifecycle ---- */

    @Override
    public synchronized void start() {
        if ( started ) return;
        sandbox.start();
        repo.initIfNeeded();
        started = true;
        LOG.info("agent harness started (sandbox isolated={}, tools={})", sandbox.isHostBoundary(), tools.size());
    }

    @Override
    public boolean isSandboxIsolated() { return sandbox.isHostBoundary(); }

    private void ensureStarted() { if ( !started ) start(); }

    /* ---- 1. messaging + the loop ---- */

    @Override
    public CompletableFuture<Void> send( String userMessage ) {
        return send(AgentMessage.user(userMessage));
    }

    @Override
    public CompletableFuture<Void> send( AgentMessage message ) {
        Objects.requireNonNull(message, "message");
        if ( closed ) throw new IllegalStateException("harness is closed");
        return CompletableFuture.runAsync(() -> runTurn(message), loop);
    }

    private void runTurn( AgentMessage input ) {
        ensureStarted();
        long turnId = turnCounter.incrementAndGet();
        emit(new AgentEvent.TurnStarted(turnId));
        try {
            context = context.add(input);
            int iterations = 0;
            while ( true ) {
                if ( ++iterations > maxIterations ) {
                    emit(new AgentEvent.ErrorOccurred(turnId, "turn exceeded " + maxIterations + " iterations"));
                    break;
                }
                ProviderResponse response = provider.respond(context, tools.specs());
                if ( !response.text().isBlank() ) {
                    context = context.addAssistant(response.text());
                    emit(new AgentEvent.AssistantText(turnId, response.text()));
                }
                if ( !response.hasToolCalls() ) break;
                for ( ToolCall call : response.toolCalls() ) {
                    emit(new AgentEvent.ToolCallDispatched(turnId, call));
                    ToolResult result = tools.dispatch(call);
                    context = context.addToolResult(call.id(), result.output());
                    emit(new AgentEvent.ToolCompleted(turnId, call, result));
                }
            }
        } catch ( RuntimeException e ) {
            LOG.warn("turn {} failed", turnId, e);
            emit(new AgentEvent.ErrorOccurred(turnId, String.valueOf(e.getMessage())));
        } finally {
            emit(new AgentEvent.TurnEnded(turnId));
        }
    }

    /* ---- listeners ---- */

    @Override public void addListener( AgentEventListener listener )    { listeners.add(Objects.requireNonNull(listener)); }
    @Override public void removeListener( AgentEventListener listener ) { listeners.remove(listener); }

    private void emit( AgentEvent event ) {
        for ( AgentEventListener l : listeners ) {
            try {
                l.onEvent(event);
            } catch ( RuntimeException e ) {
                LOG.warn("listener threw on {}", event, e);
            }
        }
    }

    /* ---- 3. context management (serialized on the loop thread) ---- */

    @Override public AgentContext context() { return context; }

    @Override public void setSystemPrompt( String systemPrompt ) {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        onLoop(() -> { context = context.withSystemPrompt(systemPrompt); return null; });
    }

    @Override public void clearContext() {
        onLoop(() -> { context = context.cleared(); return null; });
    }

    @Override public void compactContext( int keepLast ) {
        onLoop(() -> { context = context.compactedTo(keepLast); return null; });
    }

    /* ---- 4. state management across time (serialized on the loop thread) ---- */

    @Override
    public SnapshotKey snapshot( String label ) {
        Objects.requireNonNull(label, "label");
        return onLoop(() -> {
            ensureStarted();
            String commitId = repo.commit("snapshot: " + (label.isBlank() ? "(unlabeled)" : label));
            SnapshotKey key = new SnapshotKey(Instant.now(), snapshotSeq.getAndIncrement());
            snapshots.save(new AgentSnapshot(key, context, commitId, label));
            emit(new AgentEvent.SnapshotTaken(key));
            return key;
        });
    }

    @Override public Tuple<SnapshotKey> snapshots() { return snapshots.keys(); }

    @Override public Optional<AgentSnapshot> snapshotAt( SnapshotKey key ) { return snapshots.load(key); }

    @Override
    public void restore( SnapshotKey key ) {
        Objects.requireNonNull(key, "key");
        onLoop(() -> {
            ensureStarted();
            AgentSnapshot snap = snapshots.load(key)
                .orElseThrow(() -> new IllegalArgumentException("no snapshot for key " + key));
            repo.checkout(snap.workspaceCommitId());
            context = snap.context();
            emit(new AgentEvent.SnapshotRestored(key));
            return null;
        });
    }

    /** Runs a state mutation on the single loop thread (serialized with turns), unwrapping failures. */
    private <T> T onLoop( Supplier<T> action ) {
        if ( closed ) throw new IllegalStateException("harness is closed");
        try {
            return loop.submit(action::get).get();
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch ( ExecutionException e ) {
            Throwable cause = e.getCause();
            if ( cause instanceof RuntimeException re ) throw re;
            throw new IllegalStateException(cause);
        }
    }

    @Override
    public void close() {
        if ( closed ) return;
        closed = true;
        loop.shutdown();
        try { sandbox.close(); } catch ( RuntimeException e ) { LOG.warn("sandbox close failed", e); }
        LOG.info("agent harness closed");
    }

    /* ====================================================================================
       Construction
       ==================================================================================== */

    public static Builder builder() { return new Builder(); }

    /**
     *  A dev/test stack: a {@link LocalSandboxRuntime} over {@code workspaceDir} (no host isolation),
     *  git via the given binary (bundled or on PATH), default sandbox tools, and an in-memory snapshot
     *  store. Use {@link #podman} for the real boundary.
     */
    public static AgentHarness local( AgentProvider provider, Path workspaceDir, Path gitBinary, String systemPrompt ) {
        ProcessRunner runner = new ProcessRunner();
        SandboxRuntime sandbox = new LocalSandboxRuntime(workspaceDir, runner);
        WorkspaceRepo repo = new WorkspaceRepo(gitBinary, workspaceDir, runner);
        return builder().provider(provider).sandbox(sandbox).workspaceRepo(repo).systemPrompt(systemPrompt).build();
    }

    /** The real sandbox: a {@link PodmanSandboxRuntime} provisioned from the bundled binaries (§9.5). */
    public static AgentHarness podman( AgentProvider provider, SandboxBinaries binaries,
                                       SandboxConfig config, String systemPrompt ) {
        ProcessRunner runner = new ProcessRunner();
        SandboxRuntime sandbox = new PodmanSandboxRuntime(binaries, config, runner);
        WorkspaceRepo repo = new WorkspaceRepo(binaries.gitOrThrow(), config.workspaceDir(), runner);
        return builder().provider(provider).sandbox(sandbox).workspaceRepo(repo).systemPrompt(systemPrompt).build();
    }

    /** Fluent assembler for an {@link AgentHarness}. */
    public static final class Builder {
        private @Nullable AgentProvider  provider;
        private @Nullable SandboxRuntime sandbox;
        private @Nullable WorkspaceRepo  repo;
        private @Nullable ToolRegistry   tools;
        private @Nullable SnapshotStore  snapshots;
        private String systemPrompt = "";
        private int    maxIterations = 16;

        public Builder provider( AgentProvider provider )      { this.provider = provider; return this; }
        public Builder sandbox( SandboxRuntime sandbox )        { this.sandbox = sandbox; return this; }
        public Builder workspaceRepo( WorkspaceRepo repo )      { this.repo = repo; return this; }
        public Builder tools( ToolRegistry tools )              { this.tools = tools; return this; }
        public Builder snapshotStore( SnapshotStore store )     { this.snapshots = store; return this; }
        public Builder systemPrompt( String systemPrompt )      { this.systemPrompt = systemPrompt; return this; }
        public Builder maxIterations( int maxIterations )       { this.maxIterations = maxIterations; return this; }

        public AgentHarness build() { return new AgentHarness(this); }
    }
}
