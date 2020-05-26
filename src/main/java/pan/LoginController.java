package pan;

import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import pan.Router.ContentType;
import pan.Router.Get;
import pan.Router.HttpServerExchange;
import pan.Router.Path;
import pan.Router.Post;

@Singleton
@Path("/login")
public class LoginController {

    private Mustache template;

    @Inject
    public void setTemplate(MustacheFactory mustacheFactory) {
        template = mustacheFactory.compile("login.html");
    }

    @Get
    @ContentType("text/html")
    public void login(HttpServerExchange exchange) throws Exception {
        HashMap<String, Object> scopes = new HashMap<>();
        template.execute(exchange.getResponse().getWriter(), scopes).flush();
    }

    @Post
    @ContentType("text/html")
    public void doLogin(HttpServerExchange exchange) throws Exception {
        HttpServletRequest req = exchange.getRequest();
        HttpServletResponse res = exchange.getResponse();

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        HashMap<String, Object> scopes = new HashMap<>();

        if ("ecmel".equals(username) && "0908".equals(password)) {
            res.addCookie(new Cookie("ROLE", "MODERATOR"));
            scopes.put("message", "Bağlantı başarılı");
        } else {
            scopes.put("message", "Hatalı kullancı adı veya parola");
        }

        template.execute(res.getWriter(), scopes).flush();
    }
}
