package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;


@ComponentScan
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
        RabbitAutoConfiguration.class,
        SecurityAutoConfiguration.class})
public class SfdcEnrichment {
    public static void main(String[] args) {
        SpringApplication.run(SfdcEnrichment.class, args);
    }
}