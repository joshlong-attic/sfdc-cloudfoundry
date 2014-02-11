package demo.processors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.RabbitServiceInfo;
import org.springframework.cloud.service.common.RelationalServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.net.URI;
import java.sql.Driver;
import java.util.List;

/**
 * this should work inside of cloud foundry
 */

@Configuration
@Profile("cloud")
class CloudFoundryConfiguration {

    Log log = LogFactory.getLog(getClass());

    @PostConstruct
    public void setup() {
        log.debug(getClass().getName() + " has been loaded!");
    }


    @Bean(destroyMethod = "close")
    DataSource dataSource(Environment environment) {

        Class<Driver> driverClassName = environment.getPropertyAsClass(
                "spring.datasource.driverClassName", Driver.class);

        String username = "bf2395e0f9b3f1",
                password = "0fc24418",
                host = "us-cdbr-east-05.cleardb.net",
                db = "ad_7a84275d93529ba";

        String url = String.format("jdbc:mysql://%s/%s", host, db);
        org.apache.tomcat.jdbc.pool.DataSource pool = new org.apache.tomcat.jdbc.pool.DataSource();
        pool.setDriverClassName(driverClassName.getName());
        pool.setUsername(username);
        pool.setUrl(url);
        pool.setPassword((password));
        pool.setInitialSize((10));
        pool.setMaxActive((100));
        pool.setMaxIdle(8);
        pool.setMinIdle(8);
        pool.setTestOnBorrow((false));
        pool.setTestOnReturn((false));
        pool.setValidationQuery("select 1");
        return pool;
    }


    @Bean
    ConnectionFactory rabbitConnectionFactory() throws Throwable {
        String uri = "amqp://guest:guest@54.241.56.37:5672/".trim(); // / 5672
         URI uriObject = new URI(uri);
        com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setUri(uri);

        CachingConnectionFactory c = new CachingConnectionFactory(factory);
        c.setUsername(uriObject.getUserInfo().split(":")[0]);
        c.setPassword(uriObject.getUserInfo().split(":")[1]);
        c.setVirtualHost(uriObject.getPath());
        return c;
    }

}
