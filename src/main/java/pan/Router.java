package pan;

import java.io.IOException;
import java.util.ArrayDeque;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.inject.Injector;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

@Singleton
public class Router extends HandlerCollection
{
    @FunctionalInterface
    public static interface Route
    {
        void handle(HttpServletRequest req, HttpServletResponse res) throws Exception;
    }

    @FunctionalInterface
    public static interface Controller
    {
        void init();
    }

    private Injector injector;
    private ArrayDeque<String> context = new ArrayDeque<>();

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    private class FilterHandler extends AbstractHandler
    {
        private final String path;
        private final Route route;

        public FilterHandler(String path, Route route)
        {
            this.path = path;
            this.route = route;
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException
        {
            if (target.startsWith(path))
            {
                try
                {
                    route.handle(request, response);
                }
                catch (Exception e)
                {
                    throw new ServletException(e);
                }
            }
        }
    }

    private class RouteHandler extends AbstractHandler
    {
        private final String method;
        private final UriTemplatePathSpec path;
        private final Route route;

        public RouteHandler(String method, String path, Route route)
        {
            this.method = method;
            this.path = new UriTemplatePathSpec(path);
            this.route = route;
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException
        {
            if (method.equals(baseRequest.getMethod()) && path.matches(target))
            {
                try
                {
                    path.getPathParams(target).forEach((k, v) -> request.setAttribute(k, v));
                    route.handle(request, response);
                    baseRequest.setHandled(true);
                }
                catch (Exception e)
                {
                    throw new ServletException(e);
                }
            }
        }
    }

    private class RouteWebSocketHandler extends WebSocketHandler
    {
        private final String path;
        private final Class<?> controller;

        public RouteWebSocketHandler(String path, Class<?> controller)
        {
            this.path = path;
            this.controller = controller;
        }

        @Override
        public void configure(WebSocketServletFactory factory)
        {
            factory.setCreator((req, res) -> injector.getInstance(controller));
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException
        {
            if (path.equals(target))
            {
                super.handle(target, baseRequest, request, response);
            }
        }
    }

    public String getBody(HttpServletRequest req) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        req.getReader().lines().forEach((e) -> builder.append(e));
        return builder.toString();
    }

    private String getContext(String path)
    {
        StringBuilder builder = new StringBuilder();
        context.descendingIterator().forEachRemaining((e) -> builder.append(e));
        builder.append(path);

        return builder.toString().replaceAll("/+", "/");
    }

    public Router use(Class<?> controller)
    {
        return use("", controller);
    }

    public Router use(String path, Class<?> controller)
    {
        addHandler(new RouteWebSocketHandler(getContext(path), controller));
        return this;
    }

    public Router use(Controller controller)
    {
        return use("", controller);
    }

    public Router use(String path, Controller controller)
    {
        context.push(path);
        controller.init();
        context.pop();
        return this;
    }

    public Router filter(Route route)
    {
        return filter("", route);
    }

    public Router filter(String path, Route route)
    {
        addHandler(new FilterHandler(getContext(path), route));
        return this;
    }

    public Router get(Route route)
    {
        return get("", route);
    }

    public Router get(String path, Route route)
    {
        addHandler(new RouteHandler("GET", getContext(path), route));
        return this;
    }

    public Router put(Route route)
    {
        return put("", route);
    }

    public Router put(String path, Route route)
    {
        addHandler(new RouteHandler("PUT", getContext(path), route));
        return this;
    }

    public Router post(Route route)
    {
        return post("", route);
    }

    public Router post(String path, Route route)
    {
        addHandler(new RouteHandler("POST", getContext(path), route));
        return this;
    }

    public Router delete(Route route)
    {
        return delete("", route);
    }

    public Router delete(String path, Route route)
    {
        addHandler(new RouteHandler("DELETE", getContext(path), route));
        return this;
    }
}
