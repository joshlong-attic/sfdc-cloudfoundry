package demo;


import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.FactoryBean;

/**
 * creates a scoped {@link com.force.api.ForceApi forceAPI} proxy.
 *
 * @author Josh Long
 */
public class ForceApiFactoryBean implements FactoryBean<ForceApi> {

    @Override
    public ForceApi getObject() throws Exception {
        return forceApiProxy();
    }

    @Override
    public Class<?> getObjectType() {
        return ForceApi.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private ForceApi forceApiProxy() {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                ForceApi forceApi = forceApi();
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(ForceApi.class);
        return (ForceApi) proxyFactoryBean.getObject();
    }

    private ForceApi forceApi() {
        SecurityContext securityContext = ForceSecurityContextHolder.get();
        ApiSession session = new ApiSession();
        session.setAccessToken(securityContext.getSessionId());
        session.setApiEndpoint(securityContext.getEndPointHost());
        return new ForceApi(session);
    }

}
