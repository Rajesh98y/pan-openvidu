package pan;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Router extends AbstractHandler
{
    /**********
     * CLIENT *
     **********/

    public static class Client
    {
        public class Response
        {
            private final int status;
            private final ByteArrayOutputStream body;

            private Response(int status, ByteArrayOutputStream body)
            {
                this.status = status;
                this.body = body;
            }

            public int getStatus()
            {
                return status;
            }

            public int getSize()
            {
                return body.size();
            }

            public byte[] getBytes()
            {
                return body.toByteArray();
            }

            public String getBody()
            {
                try
                {
                    return body.toString("UTF-8");
                }
                catch (UnsupportedEncodingException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        private Response request(String method, String path, @Nullable String body)
        {
            HttpURLConnection con = null;

            try
            {
                URL url = new URL("http", "localhost", 8080, path);

                con = (HttpURLConnection) url.openConnection();

                con.setRequestMethod(method);

                if (body != null)
                {
                    con.setDoOutput(true);

                    try (OutputStream out = con.getOutputStream())
                    {
                        out.write(body.getBytes(Charset.forName("UTF-8")));
                        out.flush();
                    }
                }

                int status = con.getResponseCode();

                try (InputStream in = status > 299
                    ? con.getErrorStream()
                    : con.getInputStream();

                    ByteArrayOutputStream out = new ByteArrayOutputStream();)
                {
                    byte[] buffer = new byte[1024];
                    int length;

                    while ((length = in.read(buffer)) != -1)
                    {
                        out.write(buffer, 0, length);
                    }

                    out.flush();

                    return new Response(status, out);
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                if (con != null)
                {
                    con.disconnect();
                }
            }
        }

        public Response get(String target)
        {
            return request("GET", target, null);
        }

        public Response post(String target, String body)
        {
            return request("POST", target, body);
        }

        public Response put(String target, String body)
        {
            return request("PUT", target, body);
        }

        public Response delete(String target)
        {
            return request("DELETE", target, null);
        }
    }

    /**************
     * EXCEPTIONS *
     **************/

    public static class ValidationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public ValidationException(String msg)
        {
            super(msg);
        }
    }

    /***********
     * LAMBDAS *
     ***********/

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

    /**********
     * ROUTER *
     **********/

    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    private List<Route> routes = new ArrayList<>();
    private List<Route> befores = new ArrayList<>();
    private ArrayDeque<String> context = new ArrayDeque<>();

    private class Route
    {
        private final String method;
        private final String spec;
        private final UriTemplatePathSpec path;
        private final RouteHandler handler;

        private Route(String method, String spec, RouteHandler handler)
        {
            this.method = method;
            this.spec = spec.replaceAll("/+", "/");
            this.path = new UriTemplatePathSpec(this.spec);
            this.handler = handler;

            LOG.info("{}\t{}", this.method, this.spec);
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

        return builder.toString();
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
        String method = baseRequest.getMethod();

        for (Route route : routes)
        {
            if (route.matches(method, target))
            {
                req.setCharacterEncoding("UTF-8");
                res.setCharacterEncoding("UTF-8");

                route.setAttributes(req, target);

                for (Route before : befores)
                {
                    if (before.matches(target))
                    {
                        before.handle(req, res);
                    }
                }

                route.handle(req, res);
                baseRequest.setHandled(true);
                return;
            }
        }

        LOG.warn("Not found: {} {} {}", method, target, req.getRemoteAddr());
    }
}
