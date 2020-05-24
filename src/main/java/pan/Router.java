package pan;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.pathmap.UriTemplatePathSpec;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

@Singleton
public class Router extends AbstractHandler {

    public static class Client {

        public class Response {

            private final int status;
            private final String body;

            private Response(int status, String body) {
                this.status = status;
                this.body = body;
            }

            public int getStatus() {
                return status;
            }

            public String getBody() {
                return body;
            }
        }

        private Response request(String method, String path, String payload) {
            HttpURLConnection con = null;
            try {
                URL url = new URL("http", "localhost", 8080, path);

                con = (HttpURLConnection) url.openConnection();

                con.setRequestMethod(method);
                con.setDoOutput(true);
                con.connect();

                if (payload != null) {
                    try (OutputStream out = con.getOutputStream()) {
                        out.write(payload.getBytes(Charset.forName("UTF-8")));
                        out.flush();
                    }
                }

                int status = con.getResponseCode();

                try (InputStream is = status > 299 ? con.getErrorStream() : con.getInputStream();
                        ByteArrayOutputStream os = new ByteArrayOutputStream();) {

                    byte[] buffer = new byte[1024];
                    int length;

                    while ((length = is.read(buffer)) != -1) {
                        os.write(buffer, 0, length);
                    }
                    os.flush();

                    return new Response(status, os.toString("UTF-8"));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }

        public Response get(String path) {
            return this.request("GET", path, null);
        }

        public Response post(String path, String payload) {
            return this.request("POST", path, payload);
        }

        public Response put(String path, String payload) {
            return this.request("PUT", path, payload);
        }

        public Response delete(String path) {
            return this.request("DELETE", path, null);
        }

        public Response options(String path, String payload) {
            return this.request("OPTIONS", path, payload);
        }
    }

    public static class ValidationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ValidationException(String msg) {
            super(msg);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public static @interface Path {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface ContentType {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Filter {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Get {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Post {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Put {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Delete {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Options {
        String value() default "";
    }

    public static class HttpServerExchange {

        private final String target;
        private final Map<String, String> params;
        private final HttpServletRequest request;
        private final HttpServletResponse response;

        public HttpServerExchange(String target, Map<String, String> params,
                HttpServletRequest request, HttpServletResponse response) {

            this.target = target;
            this.params = params;
            this.request = request;
            this.response = response;
        }

        public String getTarget() {
            return target;
        }

        public String getPathParam(String key) {
            return params.get(key);
        }

        public HttpServletRequest getRequest() {
            return request;
        }

        public HttpServletResponse getResponse() {
            return response;
        }

        public String getRequestBody() {
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

    private class Route {

        private final String method;
        private final String spec;
        private final UriTemplatePathSpec path;
        private final Object route;
        private final Method handler;
        private final String contentType;

        private Route(String method, String spec, Object route, Method handler) {
            this.method = method;
            this.spec = spec.replaceAll("/+", "/");
            this.path = new UriTemplatePathSpec(this.spec);
            this.route = route;
            this.handler = handler;

            this.contentType = handler.isAnnotationPresent(ContentType.class)
                    ? handler.getDeclaredAnnotation(ContentType.class).value()
                    : defaultContentType;
        }

        public String getMethod() {
            return method;
        }

        public String getSpec() {
            return spec;
        }

        public UriTemplatePathSpec getPath() {
            return path;
        }

        public void invoke(HttpServerExchange exchange) {
            exchange.getResponse().setContentType(contentType);
            try {
                handler.invoke(route, exchange);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String context = "";
    private List<Route> routes = new ArrayList<>();
    private List<Route> filters = new ArrayList<>();
    private String defaultContentType = MimeTypes.Type.APPLICATION_JSON.asString();

    public void setDefaultContentType(String defaultContentType) {
        this.defaultContentType = defaultContentType;
    }

    public void route(String route) {
        this.context = route;
    }

    public void route(Object route) {
        Class<?> type = route.getClass();

        String prefix = this.context;

        Path path = type.getDeclaredAnnotation(Path.class);
        if (path != null) {
            prefix += path.value();
        }

        Method[] methods = type.getDeclaredMethods();

        for (int i = 0; i < methods.length; i++) {

            Method method = methods[i];

            Get get = method.getDeclaredAnnotation(Get.class);
            if (get != null) {
                routes.add(new Route("GET", prefix + get.value(), route, method));
                continue;
            }

            Post post = method.getDeclaredAnnotation(Post.class);
            if (post != null) {
                routes.add(new Route("POST", prefix + post.value(), route, method));
                continue;
            }

            Put put = method.getDeclaredAnnotation(Put.class);
            if (put != null) {
                routes.add(new Route("PUT", prefix + put.value(), route, method));
                continue;
            }

            Delete delete = method.getDeclaredAnnotation(Delete.class);
            if (delete != null) {
                routes.add(new Route("DELETE", prefix + delete.value(), route, method));
                continue;
            }

            Options options = method.getDeclaredAnnotation(Options.class);
            if (options != null) {
                routes.add(new Route("OPTIONS", prefix + options.value(), route, method));
                continue;
            }

            Filter filter = method.getDeclaredAnnotation(Filter.class);
            if (filter != null) {
                filters.add(new Route("FILTER", filter.value(), route, method));
                continue;
            }
        }
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {

        String method = baseRequest.getMethod();

        for (Route route : routes) {
            if (route.getMethod().equals(method) && route.getPath().matches(target)) {

                request.setCharacterEncoding("UTF-8");
                response.setCharacterEncoding("UTF-8");

                HttpServerExchange exchange = new HttpServerExchange(target,
                        route.getPath().getPathParams(target), request, response);

                for (Route filter : filters) {
                    if (target.startsWith(filter.getSpec())) {
                        filter.invoke(exchange);
                    }
                }

                route.invoke(exchange);

                baseRequest.setHandled(true);
                break;
            }
        }
    }
}
