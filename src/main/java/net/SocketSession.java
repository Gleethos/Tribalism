package net;

import org.json.JSONObject;

/**
 *  This interface is used to give the {@link WebUserSession} a nice API to send messages to the client
 *  as well as to check if the connection is still open and when it was created...
 */
public interface SocketSession
{
    void send(JSONObject json);

    boolean isOpen();

    long creationTime();
}
