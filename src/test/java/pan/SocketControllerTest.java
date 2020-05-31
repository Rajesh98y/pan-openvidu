package pan;

import static org.junit.Assert.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import com.google.inject.Guice;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.github.ecmel.router.Router;
import pan.Modules.Development;

public class SocketControllerTest
{
    @Inject
    @Named("wsURI")
    private String uri;
    @Inject
    private WebSocketClient client;
    @Inject
    private Server server;
    @Inject
    private Router router;

    @Before
    public void setUp() throws Exception
    {
        Guice.createInjector(new Development()).injectMembers(this);
        router.use("/echo", SocketController.class);
        server.start();
        client.start();
    }

    @After
    public void tearDown() throws Exception
    {
        client.stop();
        server.stop();
    }

    @WebSocket
    public class ClientSocket
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private String message;

        public String get() throws InterruptedException
        {
            latch.await(5, TimeUnit.SECONDS);
            return message;
        }

        @OnWebSocketConnect
        public void connected(Session session) throws IOException
        {
            session.getRemote().sendString("Hello");
        }

        @OnWebSocketClose
        public void closed(Session session, int statusCode, String reason)
        {
            latch.countDown();
        }

        @OnWebSocketMessage
        public void message(Session session, String message) throws IOException
        {
            this.message = message;
            session.close();
        }
    }

    @Test
    public void shouldReturnHello() throws Exception
    {
        ClientSocket socket = new ClientSocket();
        client.connect(socket, new URI(uri));
        assertEquals("Hello", socket.get());
    }
}
