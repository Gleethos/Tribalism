package app.agent;

import sprouts.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 *  A thread-safe, in-memory {@link SnapshotStore} backed by a time-ordered map (so {@link #keys()} is
 *  chronological and {@link #latest()} is the last entry). Suitable for a running session; persistence
 *  is a later concern (§10).
 */
public final class InMemorySnapshotStore implements SnapshotStore {

    private final ConcurrentSkipListMap<SnapshotKey,AgentSnapshot> byKey = new ConcurrentSkipListMap<>();

    @Override
    public void save( AgentSnapshot snapshot ) {
        Objects.requireNonNull(snapshot, "snapshot");
        byKey.put(snapshot.key(), snapshot);
    }

    @Override
    public Optional<AgentSnapshot> load( SnapshotKey key ) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public Tuple<SnapshotKey> keys() {
        List<SnapshotKey> keys = new ArrayList<>(byKey.keySet());
        return Tuple.of(SnapshotKey.class, keys);
    }

    @Override
    public Optional<AgentSnapshot> latest() {
        var entry = byKey.lastEntry();
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }
}
