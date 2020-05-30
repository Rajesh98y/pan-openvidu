package pan;

import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket
public class SocketController
{
    private SocketService service;

    @Inject
    public void setService(SocketService service)
    {
        this.service = service;
    }

    @OnWebSocketConnect
    public void connected(Session session)
    {
        service.add(session);
    }

    @OnWebSocketClose
    public void closed(Session session, int statusCode, String reason)
    {
        service.remove(session);
    }

    @OnWebSocketMessage
    public void message(Session session, String message) throws IOException
    {
        service.message(session, message);
    }
}
