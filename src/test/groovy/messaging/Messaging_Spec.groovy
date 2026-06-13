package messaging

import app.dice.DiceRoller
import app.messaging.Audience
import app.messaging.Message
import app.messaging.MessageLog
import app.messaging.Messages
import app.messaging.Recipient
import app.messaging.Sender
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("The messaging system")
@Narrative('''

    A session's chat is one shared, immutable MessageLog. Appending returns a new log; visibleTo
    filters it per recipient by audience (everyone / party / GM-only / whisper), with the GM
    omniscient and senders always seeing their own messages. The AI speaks through the same
    factories. Messages can carry a dice-roll attachment.

''')
@CompileDynamic
class Messaging_Spec extends Specification
{
    def gm     = Sender.gm("Dan", "p-gm")
    def alice  = Sender.player("Alice", "p-alice")
    def bob    = Sender.player("Bob", "p-bob")

    def gmView    = Recipient.gameMaster("p-gm")
    def aliceView = Recipient.player("p-alice")
    def bobView   = Recipient.player("p-bob")

    def 'posting appends to the log immutably.'() {
        given :
            var log = MessageLog.empty()
        when :
            var log2 = log.post(Messages.broadcast(alice, "Hello party"))
        then : 'The original is unchanged and the new log has one message.'
            log.messages().isEmpty()
            log2.messages().size() == 1
            log2.messages().get(0).body() == "Hello party"
    }

    def 'an everyone message is visible to all participants.'() {
        given :
            var log = MessageLog.empty().post(Messages.broadcast(gm, "A dragon appears!"))
        expect :
            log.visibleTo(gmView).size() == 1
            log.visibleTo(aliceView).size() == 1
            log.visibleTo(bobView).size() == 1
    }

    def 'a whisper reaches only its target, the sender, and the omniscient GM.'() {
        given : 'The GM whispers to Alice.'
            var log = MessageLog.empty().post(Messages.whisper(gm, "p-alice", "You spot a tripwire"))
        expect : 'Alice sees it (target), the GM sees it (omniscient), Bob does not.'
            log.visibleTo(aliceView).size() == 1
            log.visibleTo(gmView).size() == 1
            log.visibleTo(bobView).isEmpty()

        when : 'Alice whispers back to Bob.'
            log = log.post(Messages.whisper(alice, "p-bob", "Cover me"))
        then : 'Bob sees it (target), Alice sees it (sender), the GM sees it (omniscient).'
            log.visibleTo(bobView).collect { it.body() }.contains("Cover me")
            log.visibleTo(aliceView).collect { it.body() }.contains("Cover me")
            log.visibleTo(gmView).collect { it.body() }.contains("Cover me")
    }

    def 'a GM-only message is hidden from players but visible to the GM.'() {
        given :
            var note = new Message("n1", gm, Audience.GM_ONLY, Message.Kind.SYSTEM, 0L, "secret DC is 15", null)
            var log = MessageLog.empty().post(note)
        expect :
            log.visibleTo(gmView).size() == 1
            log.visibleTo(aliceView).isEmpty()
            log.visibleTo(bobView).isEmpty()
    }

    def 'a system message is visible to everyone.'() {
        given :
            var log = MessageLog.empty().post(Messages.system("Round 2 begins."))
        expect :
            log.visibleTo(aliceView).size() == 1
            log.messages().get(0).kind() == Message.Kind.SYSTEM
            log.messages().get(0).sender().kind() == Sender.Kind.SYSTEM
    }

    def 'a message can carry a dice-roll attachment that everyone in the audience sees.'() {
        given : 'Alice rolls a public 2d6+3.'
            var roll = DiceRoller.seeded(5L).roll("2d6+3")
            var log = MessageLog.empty().post(Messages.roll(alice, Audience.EVERYONE, roll))
        when :
            var seen = log.visibleTo(bobView)
        then : 'Bob sees the roll, the body is the roll breakdown, and the attachment is present.'
            seen.size() == 1
            seen.get(0).hasDice()
            seen.get(0).dice() == roll
            seen.get(0).body() == roll.describe()
    }

    def 'the AI speaks through the same factories as a human.'() {
        given : 'The AI (as an NPC barkeep) addresses the party.'
            var ai = Sender.ai("Barkeep (AI)")
            var log = MessageLog.empty().post(Messages.party(ai, "What'll it be, travelers?"))
        expect : 'Players see it like any other party message.'
            log.visibleTo(aliceView).size() == 1
            log.messages().get(0).sender().kind() == Sender.Kind.AI
    }
}
