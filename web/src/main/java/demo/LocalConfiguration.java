package demo;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Driver;

@Configuration
@Profile("default")
@EnableConfigurationProperties(RabbitProperties.class)
public class LocalConfiguration {


    private org.apache.tomcat.jdbc.pool.DataSource pool;

    @Bean(destroyMethod = "close")
    DataSource dataSource(Environment environment) {
        Class<Driver> driverClassName = environment.getPropertyAsClass(
                "spring.datasource.driverClassName", Driver.class);
        String url = environment.getProperty("spring.datasource.url"),
                username = environment.getProperty("spring.datasource.username"),
                password = environment.getProperty("spring.datasource.password");
        this.pool = new org.apache.tomcat.jdbc.pool.DataSource();
        this.pool.setDriverClassName(driverClassName.getName());
        this.pool.setUrl((url));
        this.pool.setUsername(username);
        this.pool.setPassword((password));
        this.pool.setInitialSize((10));
        this.pool.setMaxActive((100));
        this.pool.setMaxIdle(8);
        this.pool.setMinIdle(8);
        this.pool.setTestOnBorrow((false));
        this.pool.setTestOnReturn((false));
        this.pool.setValidationQuery("select 1");
        return this.pool;
    }

    @Bean
    ConnectionFactory rabbitConnectionFactory(RabbitProperties config) {
        CachingConnectionFactory factory = new CachingConnectionFactory(
                config.getHost());
        factory.setPort(config.getPort());
        if (config.getUsername() != null) {
            factory.setUsername(config.getUsername());
        }
        if (config.getPassword() != null) {
            factory.setPassword(config.getPassword());
        }
        if (config.getVirtualHost() != null) {
            factory.setVirtualHost(config.getVirtualHost());
        }
        return factory;
    }


    @PreDestroy
    public void close() {
        if (this.pool != null) {
            this.pool.close();
        }
    }

}
