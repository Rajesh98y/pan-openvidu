package pan;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.github.ecmel.router.HttpRequest;
import com.github.ecmel.router.HttpResponse;
import com.github.ecmel.router.RouteGroup;
import com.github.ecmel.router.Router;
import com.google.gson.Gson;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.OpenViduRole;
import io.openvidu.java.client.Session;
import io.openvidu.java.client.SessionProperties;
import io.openvidu.java.client.TokenOptions;

@Singleton
public class CallController implements RouteGroup
{
    private Router router;
    private Gson gson;
    private OpenVidu openVidu;

    @Inject
    public void setRouter(Router router)
    {
        this.router = router;
    }

    @Inject
    public void setGson(Gson gson)
    {
        this.gson = gson;
    }

    @Inject
    public void setOpenVidu(OpenVidu openVidu)
    {
        this.openVidu = openVidu;
    }

    public void generateToken(HttpRequest req, HttpResponse res) throws Exception
    {
        CallPayload payload = gson.fromJson(req.getContentAsString(), CallPayload.class);

        SessionProperties properties = new SessionProperties.Builder()
            .customSessionId(getSessionId(payload))
            .build();

        Session session = openVidu.createSession(properties);

        TokenOptions options = new TokenOptions.Builder()
            .role(OpenViduRole.PUBLISHER)
            .build();

        String token = gson.toJson(session.generateToken(options));

        res.getWriter().print(token);
    }

    private String getSessionId(CallPayload payload) throws Exception
    {
        String sessionId = payload.getSessionId();

        if (sessionId == null)
        {
            throw new ValidationException("sessionId is null");
        }

        if (sessionId.length() < 4)
        {
            throw new ValidationException("sessionId is too short");
        }

        if (sessionId.length() > 50)
        {
            throw new ValidationException("sessionId is too long");
        }

        return sessionId.replace('ğ', 'g').replace('Ğ', 'G').replace('ü', 'u').replace('Ü', 'U')
            .replace('ş', 's').replace('Ş', 'S').replace('ı', 'i').replace('İ', 'I')
            .replace('ö', 'o').replace('Ö', 'O').replace('ç', 'c').replace('Ç', 'C')
            .replaceAll("[^0-9a-zA-Z-]", "_");
    }

    @Override
    public void init()
    {
        router.post(this::generateToken);
    }
}
