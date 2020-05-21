package pan;

import static org.junit.Assert.*;
import javax.inject.Inject;
import com.google.gson.Gson;
import com.google.inject.Guice;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.Session;
import pan.Modules.Development;
import pan.Router.Client;
import pan.Router.Client.Response;

public class CallControllerTest {

    @Inject
    private Gson gson;
    @Inject
    private OpenVidu openVidu;
    @Inject
    private Client client;
    @Inject
    private Server server;
    @Inject
    private Router router;
    @Inject
    private CallController callController;

    @Before
    public void setUp() throws Exception {
        Guice.createInjector(new Development()).injectMembers(this);
        router.route(callController);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void shouldReturnNotFound() throws Exception {
        assertEquals(404, client.get("/none").getStatus());
        assertEquals(404, client.get("/call").getStatus());
    }

    @Test
    public void shouldJoinExistingSession() throws Exception {
        CallPayload payload = new CallPayload();
        payload.setSessionId("existing-session");

        Response res;

        for (int i = 0; i < 10; i++) {
            res = client.post("/call", gson.toJson(payload));
            assertEquals(200, res.getStatus());
        }

        openVidu.fetch();

        int count = 0;

        for (Session session : openVidu.getActiveSessions()) {
            if (payload.getSessionId().equals(session.getSessionId())) {
                count++;
            }
        }

        assertEquals(1, count);
    }

    @Test
    public void shouldNotCreateMalformedSession() throws Exception {
        assertNotEquals(200, client.post("/call", "{ essionId: 1 }").getStatus());
        assertNotEquals(200, client.post("/call", "{ error }").getStatus());
        assertNotEquals(200, client.post("/call", "test").getStatus());
        assertNotEquals(200, client.post("/call", "").getStatus());
    }

    @Test
    public void shouldRemoveDiacriticsFromSessionId() throws Exception {
        CallPayload payload = new CallPayload();
        payload.setSessionId("AaĞğŞşİıÖöÇçĞğŞşİıÖöÇç-0123456789-%%");

        Response res = client.post("/call", gson.toJson(payload));

        assertEquals(200, res.getStatus());
        assertNotEquals(-1, res.getBody().indexOf("AaGgSsIiOoCcGgSsIiOoCc-0123456789-__"));
    }
}
