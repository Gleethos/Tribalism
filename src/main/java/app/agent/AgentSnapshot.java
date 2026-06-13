package app.agent;

import java.util.Objects;

/**
 *  One coherent point-in-time for the agent (§9.6, §10.2): the immutable {@link AgentContext} value
 *  <b>and</b> the id of the git commit capturing the workspace at the same moment, under one
 *  date-time {@link SnapshotKey}. Restoring it repoints the context and checks the workspace out to the
 *  commit — context and files move together.
 */
public record AgentSnapshot(
    SnapshotKey   key,
    AgentContext  context,
    String        workspaceCommitId,
    String        label
) {
    public AgentSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(workspaceCommitId, "workspaceCommitId");
        Objects.requireNonNull(label, "label");
    }
}
