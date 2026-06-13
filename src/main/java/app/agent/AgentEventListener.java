package app.agent;

/**
 *  The callback registered via {@link TribalismAgentHarness#addListener} to receive the stream of
 *  {@link AgentEvent}s a turn produces (§9.8). A functional interface so callers can pass a lambda;
 *  a desktop view model, for instance, turns these into bound properties for the AI tool-log panel.
 *  <p>
 *  Implementations should be quick and non-blocking — they are invoked on the harness's turn thread.
 *  Offload heavy work (or UI updates onto the Swing/event thread) yourself.
 */
@FunctionalInterface
public interface AgentEventListener {
    void onEvent( AgentEvent event );
}
