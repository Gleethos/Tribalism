package app.messaging;

import app.dice.DiceRoll;
import org.jspecify.annotations.Nullable;
import sprouts.HasId;

import java.util.Objects;

/**
 *  One immutable message in a session's log: who sent it, who it is addressed to, its kind
 *  (in-character / out-of-character / system), when, the body, and an optional {@link DiceRoll}
 *  attachment (a roll dropped into the chat). See {@code VISION.md} §7.3.
 *  <p>
 *  Carries a stable {@code uid} and implements {@link HasId} so the live chat can bind a
 *  {@code Var<Tuple<Message>>} with per-item views (the {@code uid} name avoids Topsoil's
 *  reserved {@code id} primary-key column should the session log become persisted later).
 */
public record Message(
    String              uid,
    Sender              sender,
    Audience            audience,
    Kind                kind,
    long                timestamp,
    String              body,
    @Nullable DiceRoll  dice
) implements HasId<String> {

    public enum Kind { IN_CHARACTER, OUT_OF_CHARACTER, SYSTEM }

    public Message {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(body, "body");
    }

    @Override
    public String id() { return uid; }

    /** @return Whether this message carries a dice-roll attachment. */
    public boolean hasDice() { return dice != null; }
}
