package pan;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

@Singleton
public class Router extends AbstractHandler
{
    public static class Client
    {
        public class Response
        {
            private final int status;
            private final String body;

            private Response(int status, String body)
            {
                this.status = status;
                this.body = body;
            }

            public int getStatus()
            {
                return status;
            }

            public String getBody()
            {
                return body;
            }
        }

        private Response request(String method, String path, String payload)
        {
            HttpURLConnection con = null;
            try
            {
                URL url = new URL("http", "localhost", 8080, path);

                con = (HttpURLConnection) url.openConnection();

                con.setRequestMethod(method);
                con.setDoOutput(true);
                con.connect();

                if (payload != null)
                {
                    try (OutputStream out = con.getOutputStream())
                    {
                        out.write(payload.getBytes(Charset.forName("UTF-8")));
                        out.flush();
                    }
                }

                int status = con.getResponseCode();

                try (InputStream is = status > 299 ? con.getErrorStream() : con.getInputStream();
                    ByteArrayOutputStream os = new ByteArrayOutputStream();)
                {

                    byte[] buffer = new byte[1024];
                    int length;

                    while ((length = is.read(buffer)) != -1)
                    {
                        os.write(buffer, 0, length);
                    }
                    os.flush();

                    return new Response(status, os.toString("UTF-8"));
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

        public Response get(String path)
        {
            return this.request("GET", path, null);
        }

        public Response post(String path, String payload)
        {
            return this.request("POST", path, payload);
        }

        public Response put(String path, String payload)
        {
            return this.request("PUT", path, payload);
        }

        public Response delete(String path)
        {
            return this.request("DELETE", path, null);
        }

        public Response options(String path, String payload)
        {
            return this.request("OPTIONS", path, payload);
        }
    }

    public static class ValidationException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public ValidationException(String msg)
        {
            super(msg);
        }
    }

    @FunctionalInterface
    public static interface RouteHandler
    {
        abstract void handle(HttpServletRequest req, HttpServletResponse res) throws Exception;
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
            this.spec = spec.replaceAll("/+", "/");
            this.path = new UriTemplatePathSpec(this.spec);
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

    public static String getRequestBody(HttpServletRequest request) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = request.getReader();

        String line;

        while ((line = reader.readLine()) != null)
        {
            builder.append(line);
        }

        return builder.toString();
    }

    private String context = "/";
    private List<Route> routes = new ArrayList<>();
    private List<Route> filters = new ArrayList<>();

    public Router route(String route)
    {
        this.context = route;
        return this;
    }

    public Router get(RouteHandler handler)
    {
        return get("", handler);
    }

    public Router get(String route, RouteHandler handler)
    {
        routes.add(new Route("GET", context.concat(route), handler));
        return this;
    }

    public Router put(RouteHandler handler)
    {
        return put("", handler);
    }

    public Router put(String route, RouteHandler handler)
    {
        routes.add(new Route("PUT", context.concat(route), handler));
        return this;
    }

    public Router post(RouteHandler handler)
    {
        return post("", handler);
    }

    public Router post(String route, RouteHandler handler)
    {
        routes.add(new Route("POST", context.concat(route), handler));
        return this;
    }

    public Router delete(RouteHandler handler)
    {
        return delete("", handler);
    }

    public Router delete(String route, RouteHandler handler)
    {
        routes.add(new Route("DELETE", context.concat(route), handler));
        return this;
    }

    public Router filter(RouteHandler handler)
    {
        return filter("", handler);
    }

    public Router filter(String route, RouteHandler handler)
    {
        filters.add(new Route("FILTER", context.concat(route), handler));
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

                for (Route filter : filters)
                {
                    if (filter.matches(target))
                    {
                        filter.handle(req, res);
                    }
                }

                route.handle(req, res);
                baseRequest.setHandled(true);
                break;
            }
        }
    }
}
