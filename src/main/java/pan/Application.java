package pan;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.inject.Guice;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pan.Modules.Production;

@Singleton
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    private Server server;
    private Router router;
    private CallController callController;

    @Inject
    public void setServer(Server server) {
        this.server = server;
    }

    @Inject
    public void setRouter(Router router) {
        this.router = router;
    }

    @Inject
    public void setCallController(CallController callController) {
        this.callController = callController;
    }

    public void run() {
        router.route(callController);

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        Guice.createInjector(new Production(args)).getInstance(Application.class).run();
    }
}
