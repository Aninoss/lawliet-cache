package core;

import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;

public class Main {

    public static void main(String[] args) {
        Program.init();
        ResourceConfig rc = new ResourceConfig(RestService.class, AuthFilter.class);

        URI endpoint = UriBuilder
                .fromUri("http://0.0.0.0/api/")
                .port(Integer.parseInt(System.getenv("PORT")))
                .build();

        GrizzlyHttpServerFactory.createHttpServer(endpoint, rc);
    }

}
