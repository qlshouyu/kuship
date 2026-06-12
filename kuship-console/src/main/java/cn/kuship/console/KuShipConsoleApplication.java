package cn.kuship.console;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * kuship-console 启动类。
 *
 * <p>用 Java 21 / Spring Boot 4 重做 rainbond-console 服务端，复用其全部对外契约
 * （URL 前缀 /console/*、GRJWT 鉴权、general_message 响应信封），共享同一 MySQL console 库。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KuShipConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(KuShipConsoleApplication.class, args);
    }
}
