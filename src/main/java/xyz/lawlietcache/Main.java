package xyz.lawlietcache;

import xyz.lawlietcache.core.Program;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;

@SpringBootApplication(exclude = { R2dbcAutoConfiguration.class })
public class Main {

    public static void main(String[] args) {
        Program.init();
        SpringApplication.run(Main.class, args);
    }

}
