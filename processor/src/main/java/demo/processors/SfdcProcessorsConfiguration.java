package demo.processors;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import demo.SfdcBatchTemplate;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

@Configuration
class SfdcProcessorsConfiguration {


    @Value("${processor.requests}")
    private String requests;

    @Value("${processor.replies}")
    private String replies;

    @Bean
    Queue requestQueue() {
        return new Queue(requests);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleMessageListenerContainer serviceListenerContainer(  final BatchProcessor [] bp, final JdbcTemplate jdbcTemplate, ConnectionFactory rabbitConnectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(rabbitConnectionFactory);
        container.setQueues(requestQueue());
        container.setMessageListener(new MessageListenerAdapter(new PojoListener(jdbcTemplate, bp ), new NoOpSimpleMessageConverter()));
        return container;
    }

    @Bean
    ForceApi forceApi(final JdbcTemplate jdbcTemplate, final SfdcBatchTemplate sfdcBatchTemplate) {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                String batchId = sfdcBatchTemplate.requiredCurrentBatchId();
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

    private static class NoOpSimpleMessageConverter extends SimpleMessageConverter {
        @Override
        public Object fromMessage(Message message) throws MessageConversionException {
            return message;
        }
    }

    public static class PojoListener {
        private Log logger = LogFactory.getLog(getClass());
        private JdbcTemplate jdbcTemplate;
        private BatchProcessor[] batchProcessors;

        public PojoListener(JdbcTemplate jdbcTemplate, BatchProcessor[] bps) {
            this.jdbcTemplate = jdbcTemplate;
            this.batchProcessors = bps;
        }

        public String handleMessage(Message msg) throws Exception {
            Map<String, Object> h = msg.getMessageProperties().getHeaders();
            jdbcTemplate.execute("delete from sfdc_batch"); //todo delete this cleanup line
            jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)",
                    h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));

            for (BatchProcessor bp : this.batchProcessors)
                bp.onMessage(msg);

            return (String) h.get("batchId");
        }
    }
}
