package pan;

import java.io.BufferedReader;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.gson.Gson;
import io.openvidu.java.client.OpenVidu;
import io.openvidu.java.client.OpenViduRole;
import io.openvidu.java.client.Session;
import io.openvidu.java.client.SessionProperties;
import io.openvidu.java.client.SessionProperties.Builder;
import io.openvidu.java.client.TokenOptions;
import pan.Router.ValidationException;

@Singleton
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

    public void doPost(HttpServletRequest req, HttpServletResponse res) throws Exception {
        res.setContentType("application/json");

        CallPayload payload = gson.fromJson(getRequestBody(req), CallPayload.class);
        String sessionId = normalizeSessionId(payload.getSessionId());
        SessionProperties properties = new Builder().customSessionId(sessionId).build();

        OpenViduRole role = OpenViduRole.PUBLISHER;

        Cookie[] cookies = req.getCookies();
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

        res.getWriter().print(result);
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

    private String getRequestBody(HttpServletRequest request) {
        try {
            StringBuilder builder = new StringBuilder();
            BufferedReader reader = request.getReader();

            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            return builder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
