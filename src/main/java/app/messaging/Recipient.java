package app.messaging;

/**
 *  The viewer a {@link MessageLog} is filtered for: a participant seat id plus whether that
 *  participant is the game master. Used to decide message visibility (audiences + whispers +
 *  GM omniscience). See {@code MessageLog#visibleTo}.
 */
public record Recipient(String participantId, boolean isGameMaster) {

    public Recipient {
        if ( participantId == null ) participantId = "";
    }

    public static Recipient gameMaster( String participantId ) { return new Recipient(participantId, true); }
    public static Recipient player( String participantId )     { return new Recipient(participantId, false); }
}
