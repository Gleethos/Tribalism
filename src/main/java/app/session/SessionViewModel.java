package app.session;

import app.dice.DiceNotation;
import app.dice.DiceRoller;
import app.messaging.Audience;
import sprouts.Var;

import java.util.Objects;

/**
 *  A view model for one participant's view of a live {@link Session}. It holds the session as a
 *  single {@code Var<Session>} (the editing root the whole table shares) and the identity of the
 *  participant whose screen this is, so the chat is filtered to what they may see (§7.3) and
 *  messages/rolls are attributed to them.
 *  <p>
 *  Imports no Swing types; the desktop {@code SessionView} (and a future web view) bind to it.
 */
public final class SessionViewModel
{
    private final Var<Session>      session;
    private final SessionParticipant me;
    private final DiceRoller        roller;
    private final Var<String>       draft    = Var.of("");
    private final Var<String>       notation = Var.of("1d20+3");

    public SessionViewModel( Var<Session> session, SessionParticipant me, DiceRoller roller ) {
        this.session = Objects.requireNonNull(session);
        this.me      = Objects.requireNonNull(me);
        this.roller  = Objects.requireNonNull(roller);
    }

    public SessionViewModel( Var<Session> session, SessionParticipant me ) {
        this(session, me, DiceRoller.systemRandom());
    }

    public Var<Session>       session()  { return session; }
    public SessionParticipant me()       { return me; }
    public Var<String>        draft()    { return draft; }
    public Var<String>        notation() { return notation; }

    /** Sends the draft as a broadcast from this participant, then clears it. */
    public void send() {
        String text = draft.get().trim();
        if ( text.isEmpty() ) return;
        session.update(s -> s.announce(me, text));
        draft.set("");
    }

    /** Rolls the current dice notation publicly (records + announces); reports a parse error to the log. */
    public void roll() {
        try {
            var result = roller.roll(DiceNotation.parse(notation.get()));
            session.update(s -> s.roll(me, Audience.EVERYONE, result));
        } catch ( RuntimeException e ) {
            session.update(s -> s.system("Invalid dice notation: \"" + notation.get() + "\""));
        }
    }
}
