package xyz.lawlietcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import xyz.lawlietcache.core.Program;

@SpringBootApplication(exclude = { R2dbcAutoConfiguration.class })
public class Main {

    public static void main(String[] args) {
        Program.init();
        SpringApplication.run(Main.class, args);
    }

}
