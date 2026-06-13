package messaging

import app.messaging.Audience
import app.messaging.Message
import app.messaging.MessageLog
import app.messaging.Recipient
import app.messaging.Sender
import groovy.transform.CompileDynamic
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title
import spock.lang.Unroll

@Title("Messaging — exhaustive visibility matrix (stable-API contract)")
@Narrative('''

    Pins the full visibility contract of MessageLog: for a message sent by one player, which
    viewers (the GM, the sender, another player, the whisper target) see it under each audience.
    This is the rule the live session and the (future) per-participant bridge filter rely on.

''')
@CompileDynamic
class Messaging_Hardening_Spec extends Specification
{
    static final def ALICE = Sender.player("Alice", "p-alice")

    static final def GM       = Recipient.gameMaster("p-gm")
    static final def SELF     = Recipient.player("p-alice")   // the sender, Alice
    static final def OTHER    = Recipient.player("p-bob")
    static final def TARGET   = Recipient.player("p-carol")   // the whisper target

    private static MessageLog logWith(Audience audience) {
        var m = new Message("m1", ALICE, audience, Message.Kind.IN_CHARACTER, 0L, "body", null)
        return MessageLog.empty().post(m)
    }

    private static boolean sees(MessageLog log, Recipient r) { return log.visibleTo(r).size() == 1 }

    @Unroll
    def 'message from a player with audience #label is seen by gm:#gm self:#self other:#other target:#target.'() {
        given :
            var log = logWith(audience)
        expect :
            sees(log, GM)     == gm
            sees(log, SELF)   == self
            sees(log, OTHER)  == other
            sees(log, TARGET) == target
        where :
            label       | audience                          || gm    | self  | other | target
            "EVERYONE"  | Audience.EVERYONE                 || true  | true  | true  | true
            "PARTY"     | Audience.PARTY                    || true  | true  | true  | true
            "GM_ONLY"   | Audience.GM_ONLY                  || true  | true  | false | false
            "whisper→carol" | Audience.whisper("p-carol")   || true  | true  | false | true
    }

    def 'the GM sees every message regardless of audience (omniscience).'() {
        given :
            var log = MessageLog.empty()
                .post(new Message("a", ALICE, Audience.GM_ONLY, Message.Kind.SYSTEM, 0L, "secret", null))
                .post(new Message("b", ALICE, Audience.whisper("p-bob"), Message.Kind.IN_CHARACTER, 0L, "psst", null))
                .post(new Message("c", ALICE, Audience.EVERYONE, Message.Kind.IN_CHARACTER, 0L, "hi", null))
        expect :
            log.visibleTo(GM).size() == 3
    }

    def 'visibleTo preserves order and excludes only non-visible messages.'() {
        given :
            var log = MessageLog.empty()
                .post(new Message("1", ALICE, Audience.EVERYONE, Message.Kind.IN_CHARACTER, 0L, "first", null))
                .post(new Message("2", ALICE, Audience.whisper("p-carol"), Message.Kind.IN_CHARACTER, 0L, "second", null))
                .post(new Message("3", ALICE, Audience.EVERYONE, Message.Kind.IN_CHARACTER, 0L, "third", null))
        when : 'Bob (not the whisper target) reads the log.'
            var bobSees = log.visibleTo(OTHER).collect { it.body() }
        then : 'He sees the two public messages in order, not the whisper.'
            bobSees == ["first", "third"]
    }

    def 'an empty-seat recipient only sees public and party messages.'() {
        given : 'A viewer with no seat id (e.g. an unauthenticated/observer view).'
            var anon = Recipient.player("")
            var log = MessageLog.empty()
                .post(new Message("p", ALICE, Audience.EVERYONE, Message.Kind.IN_CHARACTER, 0L, "public", null))
                .post(new Message("w", ALICE, Audience.whisper("p-alice"), Message.Kind.IN_CHARACTER, 0L, "whisper", null))
        expect : 'Only the public message is visible; the empty seat never matches a whisper target.'
            log.visibleTo(anon).collect { it.body() } == ["public"]
    }
}
