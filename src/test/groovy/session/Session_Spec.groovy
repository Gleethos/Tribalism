package session

import app.dice.DiceRoller
import app.messaging.Audience
import app.session.Session
import app.session.SessionParticipant
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The Session — live coordination object")
@Narrative('''

    A Session ties the participants (GM, players, AI), the shared message log, the dice log and the
    turn order into one immutable value. It integrates the dice and messaging primitives: rolling
    records the roll AND announces it, whispers respect per-participant visibility, and the GM/AI
    are omniscient. Every operation returns a new Session.

''')
@CompileDynamic
class Session_Spec extends Specification
{
    def gm    = SessionParticipant.gameMaster("p-gm", "Dan")
    def alice = SessionParticipant.player("p-alice", "Alice", 101L)
    def bob   = SessionParticipant.player("p-bob", "Bob", 102L)
    def ai    = SessionParticipant.aiGameMaster("p-ai", "Narrator")

    private Session newSession() {
        return Session.create(7L).withParticipant(gm).withParticipant(alice).withParticipant(bob)
    }

    def 'participants join and the active map is set.'() {
        when :
            var s = newSession()
        then :
            s.participants().size() == 3
            s.activeMapId() == 7L
    }

    def 'a roll is recorded in the dice log and announced in the chat.'() {
        given :
            var s = newSession()
            var roll = DiceRoller.seeded(3L).roll("1d20+5")
        when : 'Alice makes a public roll.'
            s = s.roll(alice, Audience.EVERYONE, roll)
        then : 'The dice log has the roll and everyone sees a message describing it.'
            s.diceLog().size() == 1
            s.diceLog().get(0) == roll
            s.messagesFor(bob).size() == 1
            s.messagesFor(bob).get(0).dice() == roll
    }

    def 'a GM whisper is visible to the target and the omniscient GM/AI, not other players.'() {
        given :
            var s = newSession().withParticipant(ai)
        when : 'The GM whispers a secret to Alice.'
            s = s.whisper(gm, "p-alice", "the door is trapped")
        then :
            s.messagesFor(alice).collect { it.body() }.contains("the door is trapped")
            s.messagesFor(gm).size() == 1
            s.messagesFor(ai).size() == 1      // AI as GM is omniscient
            s.messagesFor(bob).isEmpty()
    }

    def 'announcements reach everyone.'() {
        given :
            var s = newSession()
        when :
            s = s.announce(gm, "You enter a torch-lit hall.")
        then :
            s.messagesFor(alice).size() == 1
            s.messagesFor(bob).size() == 1
            s.messagesFor(gm).size() == 1
    }

    def 'turn order advances through participants and wraps.'() {
        given :
            var s = newSession()   // gm, alice, bob
        expect :
            s.currentTurn().get().seatId() == "p-gm"
        when :
            s = s.advanceTurn()
        then :
            s.currentTurn().get().seatId() == "p-alice"
        when :
            s = s.advanceTurn().advanceTurn()   // bob, then wrap to gm
        then :
            s.currentTurn().get().seatId() == "p-gm"
    }

    def 'the session is immutable: operations return new values.'() {
        given :
            var s = newSession()
        when :
            var s2 = s.announce(alice, "hi").advanceTurn()
        then :
            s.messages().messages().isEmpty()
            s.turnIndex() == 0
            s2.messages().messages().size() == 1
            s2.turnIndex() == 1
    }

    def 'the AI acts through the same operations as the GM.'() {
        given : 'A session run by the AI as GM.'
            var s = Session.create(1L).withParticipant(ai).withParticipant(alice)
        when : 'The AI narrates and rolls an NPC attack.'
            var atk = DiceRoller.seeded(9L).roll("1d20+3")
            s = s.announce(ai, "The goblin lunges!").roll(ai, Audience.EVERYONE, atk)
        then : 'Players see the narration and the roll like any other participant.'
            s.messagesFor(alice).size() == 2
            s.diceLog().size() == 1
            s.messagesFor(alice).get(0).sender().kind() == app.messaging.Sender.Kind.AI
    }
}
