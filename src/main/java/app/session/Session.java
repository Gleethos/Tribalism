package app.session;

import app.dice.DiceRoll;
import app.messaging.Audience;
import app.messaging.Message;
import app.messaging.MessageLog;
import app.messaging.Messages;
import sprouts.Tuple;

import java.util.Optional;

/**
 *  The live coordination object of a play meeting (see {@code VISION.md} §7): the active map, the
 *  participants (GM, players, AI), the shared {@link MessageLog}, the dice log, and the turn
 *  order. It is an immutable value — every operation returns a new {@code Session} — so it
 *  snapshots and crosses threads for free, and a session view model just holds a
 *  {@code Var<Session>}.
 *  <p>
 *  This is the substrate the AI plugs into as one more participant: it perceives the session
 *  (filtered to its scope), and acts by producing new sessions through the same operations the GM
 *  uses (§9). Per-participant message visibility is delegated to the {@link MessageLog}; fog of
 *  war over the map is an engine-side filter keyed by each participant's {@link VisibilityScope}.
 */
public record Session(
    long                      activeMapId,
    Tuple<SessionParticipant> participants,
    MessageLog                messages,
    Tuple<DiceRoll>           diceLog,
    int                       turnIndex
) {
    public Session {
        if ( participants == null || messages == null || diceLog == null )
            throw new IllegalArgumentException("Session components must not be null");
        if ( !participants.isEmpty() && (turnIndex < 0 || turnIndex >= participants.size()) )
            throw new IllegalArgumentException("turnIndex out of range");
    }

    /** A fresh, empty session for the given active map (0 = no map yet). */
    public static Session create( long activeMapId ) {
        return new Session(activeMapId, Tuple.of(SessionParticipant.class), MessageLog.empty(),
                           Tuple.of(DiceRoll.class), 0);
    }

    public Session withActiveMap( long mapId ) {
        return new Session(mapId, participants, messages, diceLog, turnIndex);
    }

    /** Adds a participant (a player joining, the GM, the AI seat). */
    public Session withParticipant( SessionParticipant participant ) {
        return new Session(activeMapId, participants.add(participant), messages, diceLog, turnIndex);
    }

    /** Appends a raw message to the log. */
    public Session post( Message message ) {
        return new Session(activeMapId, participants, messages.post(message), diceLog, turnIndex);
    }

    /** A participant says something to everyone. */
    public Session announce( SessionParticipant from, String body ) {
        return post(Messages.broadcast(from.asSender(), body));
    }

    /** A participant whispers to one seat. */
    public Session whisper( SessionParticipant from, String targetSeatId, String body ) {
        return post(Messages.whisper(from.asSender(), targetSeatId, body));
    }

    /** A system message to everyone (a reveal, a status change, a log line). */
    public Session system( String body ) {
        return post(Messages.system(body));
    }

    /**
     *  Records a dice roll in the dice log and announces it to the given audience — the single
     *  operation that ties the dice system (§7.4) to the chat (§7.3).
     */
    public Session roll( SessionParticipant from, Audience audience, DiceRoll roll ) {
        return new Session(activeMapId, participants,
                           messages.post(Messages.roll(from.asSender(), audience, roll)),
                           diceLog.add(roll), turnIndex);
    }

    /** Advances the turn pointer to the next participant (wraps around). */
    public Session advanceTurn() {
        if ( participants.isEmpty() ) return this;
        return new Session(activeMapId, participants, messages, diceLog, (turnIndex + 1) % participants.size());
    }

    /** @return The participant whose turn it is, if any. */
    public Optional<SessionParticipant> currentTurn() {
        return participants.isEmpty() ? Optional.empty() : Optional.of(participants.get(turnIndex));
    }

    /** @return The messages a given participant is allowed to see, in order. */
    public Tuple<Message> messagesFor( SessionParticipant participant ) {
        return messages.visibleTo(participant.asRecipient());
    }
}
