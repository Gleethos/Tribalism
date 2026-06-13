package app.messaging;

import sprouts.Tuple;

import java.util.ArrayList;
import java.util.List;

/**
 *  A session's message log: an immutable, ordered {@link Tuple} of {@link Message}s. Appending
 *  returns a new log (value semantics), and {@link #visibleTo} produces the slice a given
 *  {@link Recipient} is allowed to see — the server-side scope filter that lets one shared log
 *  drive every participant's chat without leaking whispers or GM-only notes (see
 *  {@code VISION.md} §7.3 / §11.2).
 */
public record MessageLog(Tuple<Message> messages) {

    public MessageLog {
        if ( messages == null ) throw new IllegalArgumentException("messages must not be null");
    }

    public static MessageLog empty() { return new MessageLog(Tuple.of(Message.class)); }

    /** @return A new log with {@code message} appended. */
    public MessageLog post( Message message ) { return new MessageLog(messages.add(message)); }

    /** @return The messages this recipient may see, in order. */
    public Tuple<Message> visibleTo( Recipient recipient ) {
        List<Message> out = new ArrayList<>();
        for ( int i = 0; i < messages.size(); i++ ) {
            Message m = messages.get(i);
            if ( isVisible(m, recipient) ) out.add(m);
        }
        return Tuple.of(Message.class, out);
    }

    private static boolean isVisible( Message m, Recipient r ) {
        // The game master is omniscient.
        if ( r.isGameMaster() ) return true;
        // You always see your own messages.
        if ( m.sender().hasSeat() && m.sender().participantId().equals(r.participantId()) ) return true;
        return switch ( m.audience() ) {
            case Audience.Everyone ignored -> true;
            case Audience.Party ignored    -> true; // observers not yet modelled; any participant sees party chat
            case Audience.GmOnly ignored   -> false;
            case Audience.Whisper w        -> w.participantId().equals(r.participantId());
        };
    }
}
