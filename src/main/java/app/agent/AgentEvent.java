package app.agent;

import app.agent.tool.ToolCall;
import app.agent.tool.ToolResult;

import java.util.Objects;

/**
 *  Everything the harness emits while running a turn, as a sealed sum type so listeners
 *  ({@link AgentEventListener}) can match exhaustively (§9.8). This is the asynchronous read-side of
 *  the harness: {@link TribalismAgentHarness#send} pushes input, and the model's text and its tool
 *  activity stream back as these events — the same "watch the agent think and act" view the GM gets
 *  in copilot mode (§9.1).
 */
public sealed interface AgentEvent {

    /** A turn began in response to a {@code send}. */
    record TurnStarted( long turnId ) implements AgentEvent {}

    /** The model produced assistant text (a full message; streaming deltas can be added later). */
    record AssistantText( long turnId, String text ) implements AgentEvent {
        public AssistantText { Objects.requireNonNull(text, "text"); }
    }

    /** The harness is about to dispatch a tool call (domain or sandbox, §9.3). */
    record ToolCallDispatched( long turnId, ToolCall call ) implements AgentEvent {
        public ToolCallDispatched { Objects.requireNonNull(call, "call"); }
    }

    /** A dispatched tool call returned. */
    record ToolCompleted( long turnId, ToolCall call, ToolResult result ) implements AgentEvent {
        public ToolCompleted {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(result, "result");
        }
    }

    /** The turn settled (the model returned no further tool calls). */
    record TurnEnded( long turnId ) implements AgentEvent {}

    /** Something went wrong during the turn; the turn is aborted. */
    record ErrorOccurred( long turnId, String message ) implements AgentEvent {
        public ErrorOccurred { Objects.requireNonNull(message, "message"); }
    }

    /** A point-in-time snapshot was taken (context + workspace commit), keyed by date-time (§10.2). */
    record SnapshotTaken( SnapshotKey key ) implements AgentEvent {
        public SnapshotTaken { Objects.requireNonNull(key, "key"); }
    }

    /** The live agent was switched back to an earlier snapshot. */
    record SnapshotRestored( SnapshotKey key ) implements AgentEvent {
        public SnapshotRestored { Objects.requireNonNull(key, "key"); }
    }
}
