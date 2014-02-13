package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.MysqlServiceInfo;
import org.springframework.cloud.service.common.RabbitServiceInfo;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * @author Josh Long (josh@joshlong.com)
 */

@ImportResource("classpath:/salesforceContext.xml")
@ComponentScan
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
public class Application extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }
}

@Configuration
class ApplicationConfiguration {
    @Bean
    SecurityContext securityContext() {
        return proxy(SecurityContext.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = ForceSecurityContextHolder.get(true);
                return invocation.getMethod().invoke(securityContext, invocation.getArguments());
            }
        });
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    ForceApi forceApiProxy() {
        return proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = securityContext();
                ApiSession session = new ApiSession();
                session.setAccessToken(securityContext.getSessionId());
                session.setApiEndpoint(securityContext.getEndPointHost());
                ForceApi forceApi = new ForceApi(session);
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
    }

    @Configuration
    @Profile("cloud")
    static class CloudFoundryConfiguration {

        @Bean
        DataSource dataSource(Cloud cloud) {
            List<ServiceInfo> serviceInfos = cloud.getServiceInfos(MysqlServiceInfo.class);
            ServiceInfo serviceInfo = serviceInfos.iterator().next();
            String serviceId = serviceInfo.getId();
            System.out.println("going to bind to '" + serviceId + "'");
            return cloud.getServiceConnector(serviceId, DataSource.class, null);
        }

        @Bean
        ConnectionFactory connectionFactory(Cloud cloud) {
            List<ServiceInfo> rabbitServiceInfo = cloud.getServiceInfos(RabbitServiceInfo.class);
            ServiceInfo serviceInfo = rabbitServiceInfo.iterator().next();
            String serviceId = serviceInfo.getId();
            System.out.println("going to bind to '" + serviceId + "'");
            return cloud.getServiceConnector(serviceId, ConnectionFactory.class, null);
        }

        @Bean
        CloudFactory cloudFactory() {
            return new CloudFactory();
        }

        @Bean
        Cloud cloud(CloudFactory cloudFactory) {
            return cloudFactory.getCloud();
        }
    }

    @Configuration
    @Profile("default")
    static class DefaultConfiguration {
    }

    private static <T> T proxy(Class<T> tClass,
                               MethodInterceptor methodInterceptor) {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(methodInterceptor);
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(tClass);
        return tClass.cast(proxyFactoryBean.getObject());
    }
}

