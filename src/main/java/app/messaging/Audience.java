package app.messaging;

/**
 *  Who a {@link Message} is addressed to — a sum type implemented only by records.
 *  {@link Everyone} reaches all participants (including observers); {@link Party} reaches the
 *  GM and players (not observers); {@link GmOnly} reaches only the game master; {@link Whisper}
 *  reaches one named participant. The GM is omniscient and sees every message regardless of
 *  audience (see {@code MessageLog#visibleTo} and {@code VISION.md} §7.1/§7.3).
 */
public sealed interface Audience permits Audience.Everyone, Audience.Party, Audience.GmOnly, Audience.Whisper {

    record Everyone() implements Audience {}
    record Party()    implements Audience {}
    record GmOnly()   implements Audience {}
    record Whisper(String participantId) implements Audience {
        public Whisper {
            if ( participantId == null || participantId.isEmpty() )
                throw new IllegalArgumentException("whisper target participantId must be non-empty");
        }
    }

    Audience EVERYONE = new Everyone();
    Audience PARTY    = new Party();
    Audience GM_ONLY  = new GmOnly();

    static Audience whisper( String participantId ) { return new Whisper(participantId); }
}
