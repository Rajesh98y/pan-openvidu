package pan;

import javax.inject.Named;
import javax.inject.Singleton;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.eclipse.jetty.server.Server;
import io.openvidu.java.client.OpenVidu;

public class Modules
{
    private static class Common extends AbstractModule
    {
        private String openViduPassword;

        protected void setOpenViduPassword(String openViduPassword)
        {
            this.openViduPassword = openViduPassword;
        }

        @Provides
        @Singleton
        public GsonBuilder provideGsonBuilder()
        {
            return new GsonBuilder();
        }

        @Provides
        @Singleton
        public Gson provideGson(GsonBuilder gsonBuilder)
        {
            return gsonBuilder.create();
        }

        @Provides
        @Singleton
        public MustacheFactory provideMustacheFactory()
        {
            return new DefaultMustacheFactory();
        }

        @Provides
        @Singleton
        public Server provideServer(Router router)
        {
            Server server = new Server(8080);
            server.setHandler(router);
            return server;
        }

        @Provides
        @Singleton
        public OpenVidu provideOpenVidu()
        {
            return new OpenVidu("https://localhost:4443", openViduPassword);
        }
    }

    public static class Production extends Common
    {
        public Production(String[] args)
        {
            setOpenViduPassword(args[0]);
        }
    }

    public static class Development extends Common
    {
        @Provides
        @Named("testURI")
        public String provideTestURI()
        {
            return "http://localhost:8080";
        }
        
        public Development()
        {
            setOpenViduPassword("MY_SECRET");
        }
    }
}
