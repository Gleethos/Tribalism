package app.session;

import app.messaging.Recipient;
import app.messaging.Sender;

import java.util.Objects;

/**
 *  One seat in a live {@link Session}: the GM, a player (bound to their character), the AI, or an
 *  observer. A participant is the concrete realization of "a screen + a channel + a permission
 *  set" (see {@code VISION.md} §7.1) — here we model identity, role and visibility scope; the
 *  screen/camera lives in the engine and the permission set arrives with the AI-gating work (§9).
 */
public record SessionParticipant(
    String          seatId,
    Role            role,
    String          displayName,
    VisibilityScope scope
) {
    public enum Role { GAME_MASTER, PLAYER, AI, OBSERVER }

    public SessionParticipant {
        Objects.requireNonNull(seatId, "seatId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(scope, "scope");
    }

    /** The game master: an omniscient seat. */
    public static SessionParticipant gameMaster( String seatId, String name ) {
        return new SessionParticipant(seatId, Role.GAME_MASTER, name, VisibilityScope.OMNISCIENT);
    }

    /** A player who sees the world through their character (fog of war applies). */
    public static SessionParticipant player( String seatId, String name, long characterId ) {
        return new SessionParticipant(seatId, Role.PLAYER, name, VisibilityScope.fogged(characterId));
    }

    /** The AI running as game master (omniscient). NPC-scoped AI seats can be added later. */
    public static SessionParticipant aiGameMaster( String seatId, String name ) {
        return new SessionParticipant(seatId, Role.AI, name, VisibilityScope.OMNISCIENT);
    }

    /** @return Whether this seat sees everything (drives both fog of war and message visibility). */
    public boolean isOmniscient() { return scope.isOmniscient(); }

    /** @return This participant as a messaging {@link Recipient} (id + omniscience). */
    public Recipient asRecipient() { return new Recipient(seatId, isOmniscient()); }

    /** @return This participant as a messaging {@link Sender}. */
    public Sender asSender() {
        return switch ( role ) {
            case GAME_MASTER -> Sender.gm(displayName, seatId);
            case PLAYER      -> Sender.player(displayName, seatId);
            case AI          -> Sender.ai(displayName);
            case OBSERVER    -> Sender.player(displayName, seatId);
        };
    }
}
