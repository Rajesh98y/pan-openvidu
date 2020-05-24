package pan;

import java.util.HashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import pan.Router.ContentType;
import pan.Router.Get;
import pan.Router.HttpServerExchange;
import pan.Router.Path;
import pan.Router.Post;

@Singleton
@Path("/call/login")
public class LoginController {

    private MustacheFactory mustacheFactory;

    @Inject
    public void setMustacheFactory(MustacheFactory mustacheFactory) {
        this.mustacheFactory = mustacheFactory;
    }

    @Get
    @ContentType("text/html")
    public void login(HttpServerExchange exchange) throws Exception {
        Mustache mustache = mustacheFactory.compile("login.html");
        HashMap<String, Object> scopes = new HashMap<>();
        mustache.execute(exchange.getResponse().getWriter(), scopes).flush();
    }

    @Post
    @ContentType("text/html")
    public void doLogin(HttpServerExchange exchange) throws Exception {
        exchange.getResponse().sendRedirect("/");
    }
}
