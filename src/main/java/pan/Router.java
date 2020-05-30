package pan;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.inject.Injector;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Router extends HandlerList
{
    public static class ValidationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public ValidationException(String msg)
        {
            super(msg);
        }
    }

    @FunctionalInterface
    public static interface Controller
    {
        abstract void init();
    }

    @FunctionalInterface
    public static interface RouteHandler
    {
        abstract void handle(HttpServletRequest req, HttpServletResponse res) throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    private Injector injector;
    private List<Route> routes = new ArrayList<>();
    private List<Route> befores = new ArrayList<>();
    private ArrayDeque<String> context = new ArrayDeque<>();

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    private class RouterWebSocketHandler extends WebSocketHandler
    {
        private final String route;
        private final Class<?> controller;

        private class RouterWebSocketCreator implements WebSocketCreator
        {
            @Override
            public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse res)
            {
                return injector.getInstance(controller);
            }
        }

        public RouterWebSocketHandler(String route, Class<?> controller)
        {
            this.route = route;
            this.controller = controller;
        }

        @Override
        public void configure(WebSocketServletFactory factory)
        {
            factory.setCreator(new RouterWebSocketCreator());
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException
        {
            if (route.equals(target))
            {
                super.handle(target, baseRequest, request, response);
            }
        }
    }

    private class Route
    {
        private final String method;
        private final String spec;
        private final UriTemplatePathSpec path;
        private final RouteHandler handler;

        private Route(String method, String spec, RouteHandler handler)
        {
            this.method = method;
            this.spec = spec;
            this.path = new UriTemplatePathSpec(spec);
            this.handler = handler;
        }

        public boolean matches(String target)
        {
            return target.startsWith(this.spec);
        }

        public boolean matches(String method, String target)
        {
            return this.method.equals(method) && this.path.matches(target);
        }

        public void setAttributes(HttpServletRequest req, String target)
        {
            this.path.getPathParams(target).forEach((k, v) -> req.setAttribute(k, v));
        }

        public boolean isHandling()
        {
            return handler != null;
        }

        public void handle(HttpServletRequest req, HttpServletResponse res)
        {
            try
            {
                handler.handle(req, res);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public String getRequestBody(HttpServletRequest req) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;

        while ((line = reader.readLine()) != null)
        {
            builder.append(line);
        }

        return builder.toString();
    }

    private String getContext(String route)
    {
        StringBuilder builder = new StringBuilder();
        Iterator<String> it = context.descendingIterator();

        while (it.hasNext())
        {
            builder.append(it.next());
        }

        builder.append(route);

        return builder.toString().replaceAll("/+", "/");
    }

    public Router use(Class<?> controller)
    {
        return use("", controller);
    }

    public Router use(String route, Class<?> controller)
    {
        route = getContext(route);

        routes.add(new Route("GET", route, null));
        addHandler(new RouterWebSocketHandler(route, controller));

        return this;
    }

    public Router use(Controller controller)
    {
        return use("", controller);
    }

    public Router use(String route, Controller controller)
    {
        context.push(route);
        controller.init();
        context.pop();
        return this;
    }

    public Router get(RouteHandler handler)
    {
        return get("", handler);
    }

    public Router get(String route, RouteHandler handler)
    {
        routes.add(new Route("GET", getContext(route), handler));
        return this;
    }

    public Router put(RouteHandler handler)
    {
        return put("", handler);
    }

    public Router put(String route, RouteHandler handler)
    {
        routes.add(new Route("PUT", getContext(route), handler));
        return this;
    }

    public Router post(RouteHandler handler)
    {
        return post("", handler);
    }

    public Router post(String route, RouteHandler handler)
    {
        routes.add(new Route("POST", getContext(route), handler));
        return this;
    }

    public Router delete(RouteHandler handler)
    {
        return delete("", handler);
    }

    public Router delete(String route, RouteHandler handler)
    {
        routes.add(new Route("DELETE", getContext(route), handler));
        return this;
    }

    public Router before(RouteHandler handler)
    {
        return before("", handler);
    }

    public Router before(String route, RouteHandler handler)
    {
        befores.add(new Route("BEFORE", getContext(route), handler));
        return this;
    }

    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest req,
        HttpServletResponse res) throws IOException, ServletException
    {
        for (Route route : routes)
        {
            if (route.matches(baseRequest.getMethod(), target))
            {
                route.setAttributes(req, target);

                for (Route before : befores)
                {
                    if (before.matches(target))
                    {
                        before.handle(req, res);
                    }
                }

                if (route.isHandling())
                {
                    route.handle(req, res);
                    baseRequest.setHandled(true);
                }
                else
                {
                    super.handle(target, baseRequest, req, res);
                }

                return;
            }
        }

        LOG.warn("Not found: {} {}", req.getMethod(), req.getRequestURI());
    }
}
