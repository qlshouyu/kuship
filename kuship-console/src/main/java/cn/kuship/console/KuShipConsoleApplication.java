package cn.kuship.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan

public class KuShipConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(KuShipConsoleApplication.class, args);
    }
}
