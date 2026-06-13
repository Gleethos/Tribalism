package app.agent;

import sprouts.Tuple;

import java.util.Optional;

/**
 *  Where {@link AgentSnapshot}s live, keyed by their date-time {@link SnapshotKey} (§10.2). The
 *  in-memory {@link InMemorySnapshotStore} is the default; a persisted store (folding into the
 *  campaign timeline, §10) can implement the same interface later.
 */
public interface SnapshotStore {

    void save( AgentSnapshot snapshot );

    Optional<AgentSnapshot> load( SnapshotKey key );

    /** @return All snapshot keys, oldest first. */
    Tuple<SnapshotKey> keys();

    /** @return The most recent snapshot, if any. */
    Optional<AgentSnapshot> latest();
}
