package app.agent;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 *  The date-time handle to a saved agent state (§9.8, §10.2). A snapshot is taken "now", and this key
 *  — its {@link #timestamp()} — is what callers keep and pass to
 *  {@link TribalismAgentHarness#restore} to switch the live agent back to that moment.
 *  <p>
 *  A {@code sequence} disambiguates two snapshots that land on the same instant, so keys are always
 *  unique and totally ordered in time. The natural ordering is chronological.
 */
public record SnapshotKey(
    Instant timestamp,
    long    sequence
) implements Comparable<SnapshotKey> {

    public SnapshotKey {
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static SnapshotKey at( Instant timestamp, long sequence ) {
        return new SnapshotKey(timestamp, sequence);
    }

    @Override
    public int compareTo( SnapshotKey other ) {
        int byTime = timestamp.compareTo(other.timestamp);
        return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
    }

    /** @return The key rendered as an ISO-8601 instant (with a {@code #n} suffix when disambiguated). */
    @Override
    public String toString() {
        String iso = DateTimeFormatter.ISO_INSTANT.format(timestamp);
        return sequence == 0 ? iso : iso + "#" + sequence;
    }
}
