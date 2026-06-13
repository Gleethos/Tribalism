package app.session;

import app.messaging.Message;
import sprouts.Tuple;
import sprouts.Var;
import swingtree.UI;
import swingtree.UIForAnySwing;
import swingtree.threading.EventProcessor;

import javax.swing.JPanel;

import static swingtree.UI.*;

/**
 *  A desktop view of a live {@link Session} from one participant's seat: the participant list,
 *  the chat (filtered to what this participant may see), a message composer, and a dice tray.
 *  Everything binds to {@link SessionViewModel}'s {@code Var<Session>}; sending a message or
 *  rolling produces a new session value and the view reactively rebuilds (VISION §7).
 */
public final class SessionView extends JPanel
{
    public SessionView( SessionViewModel vm ) {
        Var<Session> session = vm.session();

        of(this).withLayout(FILL.and(INS(10))).withPrefSize(760, 620)
        .add(GROW,
            splitPane(UI.Align.HORIZONTAL).withDividerAt(200)
            .add(participantsPane(session))
            .add(chatPane(vm))
        );
    }

    private static UIForAnySwing<?,?> participantsPane( Var<Session> session ) {
        return panel(FILL.and(WRAP(1)))
            .add(GROW_X, html("<b>Participants</b>"))
            .add(GROW, session, (Session s) ->
                panel(FILL_X.and(WRAP(1))).apply(ui -> {
                    for ( int i = 0; i < s.participants().size(); i++ ) {
                        SessionParticipant p = s.participants().get(i);
                        boolean isTurn = s.currentTurn().map(t -> t.seatId().equals(p.seatId())).orElse(false);
                        ui.add(GROW_X, label((isTurn ? "▶ " : "   ") + p.displayName() + "  (" + p.role() + ")"));
                    }
                })
            )
            .add(GROW_X, button("End turn").onClick(it -> session.update(Session::advanceTurn)));
    }

    private static UIForAnySwing<?,?> chatPane( SessionViewModel vm ) {
        Var<Session> session = vm.session();
        return panel(FILL.and(WRAP(1)))
            .add(GROW_X, html("<b>Session chat</b> &mdash; seen by " + vm.me().displayName()))
            .add(GROW,
                scrollPane().add(
                    panel(FILL.and(WRAP(1)))
                    .add(GROW, session, (Session s) -> messageList(s.messagesFor(vm.me())))
                )
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(2)), "[grow][shrink]")
                .add(GROW_X, textField(vm.draft()).onEnter(it -> vm.send()))
                .add(button("Send").onClick(it -> vm.send()))
            )
            .add(GROW_X,
                panel(FILL_X.and(WRAP(3)), "[shrink][grow][shrink]")
                .add(label("Dice"))
                .add(GROW_X, textField(vm.notation()).onEnter(it -> vm.roll()))
                .add(button("Roll").onClick(it -> vm.roll()))
            );
    }

    private static UIForAnySwing<?,?> messageList( Tuple<Message> messages ) {
        return panel(FILL_X.and(WRAP(1))).apply(ui -> {
            for ( int i = 0; i < messages.size(); i++ )
                ui.add(GROW_X, label(render(messages.get(i))));
        });
    }

    private static String render( Message m ) {
        String who = m.sender().name();
        return switch ( m.kind() ) {
            case SYSTEM          -> "* " + m.body();
            case OUT_OF_CHARACTER -> "(" + who + ") " + m.body();
            case IN_CHARACTER    -> who + ": " + m.body();
        };
    }

    /** Standalone demo: a prepopulated session viewed from the GM's (omniscient) seat. */
    public static void main( String[] args ) {
        var gm    = SessionParticipant.gameMaster("p-gm", "Dan (GM)");
        var alice = SessionParticipant.player("p-alice", "Alice", 101L);
        var bob   = SessionParticipant.player("p-bob", "Bob", 102L);

        Session s = Session.create(1L).withParticipant(gm).withParticipant(alice).withParticipant(bob)
            .system("The party enters a torch-lit hall.")
            .announce(alice, "I scan the room for traps.")
            .roll(alice, app.messaging.Audience.EVERYONE, app.dice.DiceRoller.seeded(4L).roll("1d20+3"))
            .whisper(gm, "p-alice", "You notice a pressure plate by the far door.")
            .announce(bob, "I ready my shield.");

        Var<Session> session = Var.of(s);
        var vm = new SessionViewModel(session, gm, app.dice.DiceRoller.seeded(99L));
        UI.show(f -> new SessionView(vm));
        EventProcessor.DECOUPLED.join();
    }
}
