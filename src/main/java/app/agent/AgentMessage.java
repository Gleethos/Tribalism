package app.agent;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 *  One turn-entry in the agent's own conversation transcript (the harness's chat with the model,
 *  distinct from the in-game {@code app.messaging.Message} channel, §7.3). An immutable value: who
 *  authored it, the text, when, and — for a {@link Author#TOOL} entry — which tool call it answers.
 *  <p>
 *  The transcript is a {@code Tuple<AgentMessage>} inside {@link AgentContext}; because both are
 *  immutable values it snapshots for free (§9.6).
 */
public record AgentMessage(
    Author            author,
    String            text,
    Instant           timestamp,
    @Nullable String  toolCallId
) {
    /** Who produced a transcript entry. */
    public enum Author { SYSTEM, USER, ASSISTANT, TOOL }

    public AgentMessage {
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static AgentMessage system( String text )    { return new AgentMessage(Author.SYSTEM, text, Instant.now(), null); }
    public static AgentMessage user( String text )       { return new AgentMessage(Author.USER, text, Instant.now(), null); }
    public static AgentMessage assistant( String text )  { return new AgentMessage(Author.ASSISTANT, text, Instant.now(), null); }

    /** A tool's output, addressed back to the call it answers. */
    public static AgentMessage toolResult( String toolCallId, String text ) {
        return new AgentMessage(Author.TOOL, text, Instant.now(), Objects.requireNonNull(toolCallId, "toolCallId"));
    }
}
