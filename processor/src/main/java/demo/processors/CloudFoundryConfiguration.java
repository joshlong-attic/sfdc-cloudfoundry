package demo.processors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.net.URI;
import java.sql.Driver;

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
    DataSource dataSource(Environment environment) throws Exception {

        Class<Driver> driverClassName = environment.getPropertyAsClass(
                "spring.datasource.driverClassName", Driver.class);


        URI uri = new URI("jdbc:mysql://bd1349d03a0d63:5e87a384@us-cdbr-east-05.cleardb.net:3306/ad_9c3fc7bd5ae423d");

        String username = uri.getUserInfo().split(":")[0],
                password = uri.getUserInfo().split(":")[1],
                host = uri.getHost(),
                db = uri.getPath().substring(1);

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
        //   String uri = "amqp://dfbxoafe:6LPEQ28AF7W7cDN8Am19e7BAu2bzKrEd@lemur.cloudamqp.com/dfbxoafe".trim();
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
