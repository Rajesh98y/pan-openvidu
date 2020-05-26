package pan;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;
import com.google.gson.Gson;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.OpenViduRole;
import io.openvidu.java.client.Session;
import io.openvidu.java.client.SessionProperties;
import io.openvidu.java.client.SessionProperties.Builder;
import io.openvidu.java.client.TokenOptions;
import pan.Router.HttpServerExchange;
import pan.Router.Path;
import pan.Router.Post;
import pan.Router.ValidationException;

@Singleton
@Path("/call")
public class CallController {

    private Gson gson;
    private OpenVidu openVidu;

    @Inject
    public void setGson(Gson gson) {
        this.gson = gson;
    }

    @Inject
    public void setOpenVidu(OpenVidu openVidu) {
        this.openVidu = openVidu;
    }

    @Post
    public void getToken(HttpServerExchange exchange) throws Exception {
        CallPayload payload = gson.fromJson(exchange.getRequestBody(), CallPayload.class);
        String sessionId = normalizeSessionId(payload.getSessionId());
        SessionProperties properties = new Builder().customSessionId(sessionId).build();

        OpenViduRole role = OpenViduRole.PUBLISHER;

        Cookie[] cookies = exchange.getRequest().getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("ROLE".equals(cookie.getName())) {
                    if ("MODERATOR".equals(cookie.getValue())) {
                        role = OpenViduRole.MODERATOR;
                    }
                    break;
                }
            }
        }

        TokenOptions opts = new TokenOptions.Builder().role(role).build();

        Session session = openVidu.createSession(properties);
        String result = gson.toJson(session.generateToken(opts));

        exchange.getResponse().getWriter().print(result);
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) {
            throw new ValidationException("sessionId is null");
        }

        if (sessionId.length() < 4) {
            throw new ValidationException("sessionId is too short");
        }

        if (sessionId.length() > 50) {
            throw new ValidationException("sessionId is too long");
        }

        return sessionId.replace('ğ', 'g').replace('Ğ', 'G').replace('ü', 'u').replace('Ü', 'U')
                .replace('ş', 's').replace('Ş', 'S').replace('ı', 'i').replace('İ', 'I')
                .replace('ö', 'o').replace('Ö', 'O').replace('ç', 'c').replace('Ç', 'C')
                .replaceAll("[^0-9a-zA-Z-]", "_");
    }
}
