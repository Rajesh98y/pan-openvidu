package pan;

import static org.junit.Assert.*;
import javax.inject.Inject;
import javax.inject.Named;
import com.github.ecmel.router.Router;
import com.google.gson.Gson;
import com.google.inject.Guice;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.Session;

public class CallControllerTest
{
    @Inject
    @Named("httpURI")
    private String uri;
    @Inject
    private Gson gson;
    @Inject
    private OpenVidu openVidu;
    @Inject
    private HttpClient client;
    @Inject
    private Server server;
    @Inject
    private Router router;
    @Inject
    private CallController callController;

    @Before
    public void setUp() throws Exception
    {
        Guice.createInjector(new Development()).injectMembers(this);
        router.use("/call", callController);
        server.start();
        client.start();
    }

    @After
    public void tearDown() throws Exception
    {
        client.stop();
        server.stop();
    }

    @Test
    public void shouldReturnNotFound() throws Exception
    {
        ContentResponse res = client
            .newRequest(uri)
            .method(HttpMethod.GET)
            .path("/none")
            .send();

        assertEquals(404, res.getStatus());
    }

    @Test
    public void shouldJoinExistingSession() throws Exception
    {
        CallPayload payload = new CallPayload();
        payload.setSessionId("existing-session");

        ContentProvider content = new StringContentProvider(gson.toJson(payload));

        ContentResponse res;

        for (int i = 0; i < 10; i++)
        {
            res = client
                .newRequest(uri)
                .method(HttpMethod.POST)
                .content(content, "application/json")
                .path("/call")
                .send();

            assertEquals(200, res.getStatus());
        }

        openVidu.fetch();

        int count = 0;

        for (Session session : openVidu.getActiveSessions())
        {
            if (payload.getSessionId().equals(session.getSessionId()))
            {
                count++;
            }
        }

        assertEquals(1, count);
    }

    @Test
    public void shouldNotCreateMalformedSession() throws Exception
    {
        ContentProvider content = new StringContentProvider("{ essionId: 1 ");

        ContentResponse res = client
            .newRequest(uri)
            .method(HttpMethod.POST)
            .content(content, "application/json")
            .path("/call")
            .send();

        assertNotEquals(200, res.getStatus());
    }

    @Test
    public void shouldRemoveDiacriticsFromSessionId() throws Exception
    {
        CallPayload payload = new CallPayload();

        payload.setSessionId("AaĞğŞşİıÖöÇçĞğŞşİıÖöÇç-0123456789-%%");

        ContentProvider content = new StringContentProvider(gson.toJson(payload));

        ContentResponse res = client
            .newRequest(uri)
            .method(HttpMethod.POST)
            .path("/call")
            .content(content, "application/json")
            .send();

        assertEquals(200, res.getStatus());
        assertNotEquals(-1, res.getContentAsString().indexOf("AaGgSsIiOoCcGgSsIiOoCc-0123456789-__"));
    }
}
