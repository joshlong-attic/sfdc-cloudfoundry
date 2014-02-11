package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * @author Josh Long (josh@joshlong.com)
 */
@ImportResource("/salesforceContext.xml")
@Configuration
@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class SfdcConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SfdcConsoleApplication.class, args);
    }

    @Bean
    SecurityContext securityContext() {
        return this.proxy(SecurityContext.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = ForceSecurityContextHolder.get(true);
                return invocation.getMethod().invoke(securityContext, invocation.getArguments());
            }
        });
    }


    @Bean
    ForceApi forceApiProxy() {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
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

    private <T> T proxy(Class<T> tClass, MethodInterceptor methodInterceptor) {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(methodInterceptor);
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(tClass);
        return tClass.cast(proxyFactoryBean.getObject());
    }
}

