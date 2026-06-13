package app.agent;

import sprouts.Tuple;

import java.util.Objects;

/**
 *  The agent's conversational state: a system prompt plus the running transcript of
 *  {@link AgentMessage}s, carried as one immutable value (principle 1). It is what
 *  {@link TribalismAgentHarness#context()} exposes, what the provider is prompted with each turn
 *  (§9.4), and — being immutable — exactly what a snapshot captures alongside the workspace git
 *  commit (§9.6, §10.2).
 *  <p>
 *  Every "edit" returns a new {@code AgentContext}; nothing here mutates.
 */
public record AgentContext(
    String                 systemPrompt,
    Tuple<AgentMessage>    transcript
) {
    public AgentContext {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(transcript, "transcript");
    }

    /** An empty context with the given system prompt. */
    public static AgentContext of( String systemPrompt ) {
        return new AgentContext(systemPrompt, Tuple.of(AgentMessage.class));
    }

    /** An empty context with an empty system prompt. */
    public static AgentContext empty() { return of(""); }

    public AgentContext withSystemPrompt( String systemPrompt ) {
        return new AgentContext(systemPrompt, transcript);
    }

    /** Appends one transcript entry, returning a new context. */
    public AgentContext add( AgentMessage message ) {
        return new AgentContext(systemPrompt, transcript.add(Objects.requireNonNull(message, "message")));
    }

    public AgentContext addUser( String text )      { return add(AgentMessage.user(text)); }
    public AgentContext addAssistant( String text ) { return add(AgentMessage.assistant(text)); }
    public AgentContext addSystem( String text )    { return add(AgentMessage.system(text)); }
    public AgentContext addToolResult( String toolCallId, String text ) {
        return add(AgentMessage.toolResult(toolCallId, text));
    }

    /** Clears the transcript but keeps the system prompt (a fresh conversation, §9 "clearContext"). */
    public AgentContext cleared() {
        return new AgentContext(systemPrompt, Tuple.of(AgentMessage.class));
    }

    /**
     *  Compacts the transcript by keeping the most recent {@code keepLast} entries — a minimal,
     *  deterministic context-management primitive (a summarizing compaction can replace this later
     *  without changing the call site). The system prompt is untouched.
     */
    public AgentContext compactedTo( int keepLast ) {
        if ( keepLast < 0 ) throw new IllegalArgumentException("keepLast must be >= 0");
        if ( transcript.size() <= keepLast ) return this;
        return new AgentContext(systemPrompt, transcript.sliceLast(keepLast));
    }

    public int size()        { return transcript.size(); }
    public boolean isEmpty() { return transcript.isEmpty(); }
}
