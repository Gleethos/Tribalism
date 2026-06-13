package app.messaging;

import app.dice.DiceRoll;

import java.util.UUID;

/**
 *  Factories for the common kinds of {@link Message}. Each stamps a fresh {@code uid} and the
 *  current time. These are the building blocks the GM, players, and the AI all use to speak into
 *  a {@link MessageLog} (the AI has no separate I/O path — see {@code VISION.md} §7.3/§9).
 */
public final class Messages
{
    private Messages() {}

    /** An in-character message to everyone. */
    public static Message broadcast( Sender sender, String body ) {
        return of(sender, Audience.EVERYONE, kindFor(sender), body, null);
    }

    /** An in-character message to the GM and players. */
    public static Message party( Sender sender, String body ) {
        return of(sender, Audience.PARTY, kindFor(sender), body, null);
    }

    /** A private message to one participant (also visible to that participant and the GM). */
    public static Message whisper( Sender sender, String targetParticipantId, String body ) {
        return of(sender, Audience.whisper(targetParticipantId), kindFor(sender), body, null);
    }

    /** An out-of-character (table-talk) message to the party. */
    public static Message outOfCharacter( Sender sender, String body ) {
        return of(sender, Audience.PARTY, Message.Kind.OUT_OF_CHARACTER, body, null);
    }

    /** A system message to everyone (reveals, status changes, log entries). */
    public static Message system( String body ) {
        return of(Sender.system(), Audience.EVERYONE, Message.Kind.SYSTEM, body, null);
    }

    /** A message carrying a dice-roll attachment, with the given audience/visibility. */
    public static Message roll( Sender sender, Audience audience, DiceRoll roll ) {
        return of(sender, audience, kindFor(sender), roll.describe(), roll);
    }

    private static Message of( Sender sender, Audience audience, Message.Kind kind, String body, DiceRoll dice ) {
        return new Message(UUID.randomUUID().toString(), sender, audience, kind, System.currentTimeMillis(), body, dice);
    }

    private static Message.Kind kindFor( Sender sender ) {
        return sender.kind() == Sender.Kind.SYSTEM ? Message.Kind.SYSTEM : Message.Kind.IN_CHARACTER;
    }
}
