package app.agent;

import sprouts.Tuple;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 *  The one interface the rest of Tribalism uses to drive the AI agent (§9.8). It hides the podman
 *  container, the git-versioned workspace, the provider call and the agent loop (§9.4) behind a small
 *  surface, so nothing above it ever touches {@code podman}/{@code git} directly — the sandbox stays an
 *  implementation detail and principle 7 holds by construction.
 *
 *  <h2>Four responsibilities</h2>
 *  <ol>
 *    <li><b>Messaging</b> — {@link #send} hands the agent a turn of input; its replies and tool
 *        activity arrive asynchronously through {@link #addListener listeners} as {@link AgentEvent}s.</li>
 *    <li><b>The loop</b> — behind {@code send}, the harness prompts the provider, dispatches the tool
 *        calls it returns (domain tools to the GM's services, sandbox tools into the container) and
 *        streams the effects back as events, iterating until the turn settles.</li>
 *    <li><b>Context management</b> — {@link #context()}, {@link #setSystemPrompt}, {@link #clearContext}
 *        and {@link #compactContext} reshape the immutable {@link AgentContext}.</li>
 *    <li><b>State across time</b> — {@link #snapshot} captures one coherent point-in-time (the context
 *        value <i>and</i> a git commit of the workspace) under a date-time {@link SnapshotKey};
 *        {@link #snapshots()} lists them and {@link #restore} switches the live agent back to one.</li>
 *  </ol>
 *
 *  <p>Turns and state changes are <b>serialized</b> on a single harness thread, so the agent is never
 *  mid-turn while a snapshot or restore happens.
 */
public interface TribalismAgentHarness extends AutoCloseable {

    /* ---- lifecycle ---- */

    /** Brings the sandbox up and initializes the workspace repo. Idempotent; {@link #send} also ensures it. */
    void start();

    /** @return Whether the underlying sandbox is a real host-isolation boundary (false ⇒ dev/test double). */
    boolean isSandboxIsolated();

    /* ---- 1. messaging ---- */

    /** Sends a turn of user input to the agent; the reply streams back as {@link AgentEvent}s. */
    CompletableFuture<Void> send( String userMessage );

    /** Sends an arbitrary transcript entry (e.g. a system note) as a turn. */
    CompletableFuture<Void> send( AgentMessage message );

    /* ---- listeners ---- */

    void addListener( AgentEventListener listener );

    void removeListener( AgentEventListener listener );

    /* ---- 3. context management ---- */

    /** @return The current immutable conversation state. */
    AgentContext context();

    void setSystemPrompt( String systemPrompt );

    /** Clears the transcript (keeps the system prompt). */
    void clearContext();

    /** Keeps only the most recent {@code keepLast} transcript entries. */
    void compactContext( int keepLast );

    /* ---- 4. state management across time ---- */

    /** Captures context + workspace at "now" and returns its date-time key. */
    SnapshotKey snapshot( String label );

    /** Captures an unlabeled snapshot. */
    default SnapshotKey snapshot() { return snapshot(""); }

    /** @return All snapshot keys, oldest first. */
    Tuple<SnapshotKey> snapshots();

    Optional<AgentSnapshot> snapshotAt( SnapshotKey key );

    /** Switches the live agent (context + workspace) back to the given snapshot. */
    void restore( SnapshotKey key );

    @Override
    void close();
}
