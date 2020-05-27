package pan;

import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.github.mustachejava.MustacheFactory;

@Singleton
public class LoginController
{
    private MustacheFactory mustacheFactory;

    @Inject
    public void setMustacheFactory(MustacheFactory mustacheFactory)
    {
        this.mustacheFactory = mustacheFactory;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws Exception
    {
        res.setContentType("text/html");

        HashMap<String, Object> scopes = new HashMap<>();

        mustacheFactory
            .compile("login.html")
            .execute(res.getWriter(), scopes);
    }

    public void doPost(HttpServletRequest req, HttpServletResponse res) throws Exception
    {
        res.setContentType("text/html");

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        HashMap<String, Object> scopes = new HashMap<>();

        if ("admin".equals(username) && "admin".equals(password))
        {
            Cookie cookie = new Cookie("ROLE", "MODERATOR");
            cookie.setHttpOnly(true);
            res.addCookie(cookie);
            scopes.put("message", "Bağlantı başarılı.");
        }
        else
        {
            scopes.put("message", "Hatalı kullancı adı veya parola.");
        }

        mustacheFactory
            .compile("login.html")
            .execute(res.getWriter(), scopes);
    }
}
