package app.messaging;

import java.util.Objects;

/**
 *  Who sent a {@link Message}: a session participant (the GM, a player, an NPC the GM/AI runs,
 *  the AI itself, or the system). For participants that map to a session seat (GM, player), the
 *  {@code participantId} identifies that seat (used for whisper targeting and "see your own
 *  messages"); for NPC/AI/system senders it may be blank.
 *  <p>
 *  See {@code VISION.md} §7.3. The AI sends messages through the same channel as everyone else.
 */
public record Sender(Kind kind, String name, String participantId) {

    public enum Kind { GM, PLAYER, NPC, AI, SYSTEM }

    public Sender {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(participantId, "participantId");
    }

    public static Sender system()                         { return new Sender(Kind.SYSTEM, "System", ""); }
    public static Sender gm(String name, String id)       { return new Sender(Kind.GM, name, id); }
    public static Sender player(String name, String id)   { return new Sender(Kind.PLAYER, name, id); }
    public static Sender npc(String name)                 { return new Sender(Kind.NPC, name, ""); }
    public static Sender ai(String name)                  { return new Sender(Kind.AI, name, ""); }

    public boolean hasSeat() { return !participantId.isEmpty(); }
}
