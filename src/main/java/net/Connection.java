package net;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import java.util.concurrent.Future;

/**
 *  This is where the web-socket communication happens.
 *  Here messages are received from the client and sent to the client
 *  through the {@link Session} implementation of the Jetty websocket API.
 */
@WebSocket
public class Connection
{
    private final static Logger log = LoggerFactory.getLogger(Connection.class);

    private final SocketSession socketSession;

    private final WebUserSession userSession;


    public Connection(WebUserContext webUserContext, HttpSession httpSession) {
        this.socketSession = new SocketSession(httpSession, webUserContext);
        this.userSession   = new WebUserSession(webUserContext, socketSession);
        log.info("Created new websocket for user: " + httpSession.getId() + " at time " + httpSession.getCreationTime());
    }

    /**
     *  This is where the connection is established.
     *
     * @param session The session object, which is used to send messages to the client.
     */
    @OnWebSocketConnect
    public void onConnect(Session session)
    {
        try {
            this.socketSession.setSession(session);
            this.socketSession.sendPending();
            log.info("Connected to client: {}", session.getRemoteAddress().getAddress());
        } catch (Throwable t) {
            log.error("Error sending message to websocket!", t);
        }
    }

    /**
     *  This is where all the messages are received from the web-frontend client!
     *
     * @param message The message received from the React client.
     */
    @OnWebSocketMessage
    public void onMessage(String message)
    {
        log.info("Received: " + message);

        JSONObject json;
        try {
            json = new JSONObject(message);
        } catch (Exception e) {
            log.error("Error parsing message '{}' from websocket as json!", message, e);
            userSession.sendError(e);
            return;
        }
        userSession.receive(json);
    }

    /**
     *  A simple implementation of the {@link SocketSession} interface,
     *  which abstracts away the Jetty websocket implementation and
     *  makes message sending easier (json is automatically converted to string).
     */
    private class SocketSession
    implements net.SocketSession
    {
        private final HttpSession httpSession;
        private final WebUserContext webUserContext;

        private Session session;


        public SocketSession(HttpSession httpSession, WebUserContext webUserContext) {
            this.httpSession = httpSession;
            this.webUserContext = webUserContext;
        }

        public void setSession(Session session) {
            this.session = session;
        }

        private boolean _send(String message) {
                try {
                    Future<Void> future = session.getRemote().sendStringByFuture(message);
                    // Now we wait for the message to be sent:
                    future.get();
                    log.debug("Sent: " + message);
                    return true;
                } catch (Throwable t) {
                    log.error("Error sending message to websocket!", t);
                }
                return false;
            }

            void sendPending() {
                var pendingMessage = this.webUserContext.getPendingMessage();
                while (pendingMessage.isPresent()) {
                    boolean success = _send(pendingMessage.get());
                    if (!success) {
                        log.info("Failed to send pending message, message will be sent later: " + pendingMessage.get());
                        return;
                    } else {
                        log.info("Sent message delayed: " + pendingMessage.get());
                        webUserContext.removePendingMessage();
                    }
                    pendingMessage = webUserContext.getPendingMessage();
                }
            }

            @Override
            public void send(JSONObject json) {
                String message = json.toString();
                if (!session.isOpen()) {
                    webUserContext.addPendingMessage(message);
                    log.info("Session is closed, message will be sent later: " + message);
                    return;
                }
                sendPending();
                boolean success = _send(message);
                if (!success) {
                    webUserContext.addPendingMessage(message);
                    log.info("Failed to send message, message will be sent later: " + message);
                } else log.info("Sent message: " + message);
            }

            @Override
            public boolean isOpen() {
                return session.isOpen();
            }

            @Override
            public long creationTime() {
                return httpSession.getCreationTime();
            }
        }

}
