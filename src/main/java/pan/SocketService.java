package pan;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Singleton;
import org.eclipse.jetty.websocket.api.Session;

@Singleton
public class SocketService
{
    private final Queue<Session> sessions = new ConcurrentLinkedQueue<>();

    public boolean add(Session session)
    {
        return sessions.add(session);
    }

    public boolean remove(Object session)
    {
        return sessions.remove(session);
    }
}
