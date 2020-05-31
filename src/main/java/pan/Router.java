package pan;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.inject.Injector;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

@Singleton
public class Router extends HandlerList
{
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

    private Injector injector;
    private ArrayDeque<String> context = new ArrayDeque<>();

    @Inject
    public void setInjector(Injector injector)
    {
        this.injector = injector;
    }

    private class FilterHandler extends AbstractHandler
    {
        private final String route;
        private final RouteHandler handler;

        public FilterHandler(String route, RouteHandler handler)
        {
            this.route = route;
            this.handler = handler;
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException
        {
            if (target.startsWith(route))
            {
                try
                {
                    handler.handle(request, response);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private class UriTemplatePathSpecHandler extends AbstractHandler
    {
        private final String method;
        private final UriTemplatePathSpec route;
        private final RouteHandler handler;

        public UriTemplatePathSpecHandler(String method, String route, RouteHandler handler)
        {
            this.method = method;
            this.route = new UriTemplatePathSpec(route);
            this.handler = handler;
        }

        @Override
        public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException
        {
            if (method.equals(baseRequest.getMethod()) && route.matches(target))
            {
                try
                {
                    route.getPathParams(target).forEach((k, v) -> request.setAttribute(k, v));
                    handler.handle(request, response);
                    baseRequest.setHandled(true);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
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
        addHandler(new RouterWebSocketHandler(getContext(route), controller));
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
        addHandler(new UriTemplatePathSpecHandler("GET", getContext(route), handler));
        return this;
    }

    public Router put(RouteHandler handler)
    {
        return put("", handler);
    }

    public Router put(String route, RouteHandler handler)
    {
        addHandler(new UriTemplatePathSpecHandler("PUT", getContext(route), handler));
        return this;
    }

    public Router post(RouteHandler handler)
    {
        return post("", handler);
    }

    public Router post(String route, RouteHandler handler)
    {
        addHandler(new UriTemplatePathSpecHandler("POST", getContext(route), handler));
        return this;
    }

    public Router delete(RouteHandler handler)
    {
        return delete("", handler);
    }

    public Router delete(String route, RouteHandler handler)
    {
        addHandler(new UriTemplatePathSpecHandler("DELETE", getContext(route), handler));
        return this;
    }

    public Router filter(RouteHandler handler)
    {
        return filter("", handler);
    }

    public Router filter(String route, RouteHandler handler)
    {
        addHandler(new FilterHandler(getContext(route), handler));
        return this;
    }
}
