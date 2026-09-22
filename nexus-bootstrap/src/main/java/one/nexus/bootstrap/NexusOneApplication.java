package one.nexus.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "one.nexus")
public class NexusOneApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusOneApplication.class, args);
    }
}
