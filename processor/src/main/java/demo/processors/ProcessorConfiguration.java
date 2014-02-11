package demo.processors;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import demo.BatchTemplate;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

//@EnableTransactionManagement
@Configuration
class ProcessorConfiguration {

    @Bean
    DataSourceTransactionManager dataSourceTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    ForceApi forceApi(final JdbcTemplate jdbcTemplate, final BatchTemplate batchTemplate) {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                String batchId = batchTemplate.requiredCurrentBatchId();
                Map<String, Object> row = jdbcTemplate.queryForMap(
                        "select * from sfdc_batch where batch_id = ? limit 1", batchId);
                ApiSession session = new ApiSession();
                session.setAccessToken((String) row.get("access_token"));
                session.setApiEndpoint((String) row.get("api_endpoint"));
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
