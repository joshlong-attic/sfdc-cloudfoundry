package demo.processors;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import demo.Request;
import demo.SfdcBatchTemplate;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

@SuppressWarnings("unchecked")
@Configuration
class SfdcProcessorsConfiguration {

    @Bean
    SimpleMessageListenerContainer simpleMessageListenerContainer(
            @Request final Queue requests,
            final BatchProcessor[] bps,
            final RabbitTemplate rabbitTemplate,
            final JdbcTemplate jdbcTemplate,
            final ConnectionFactory connectionFactory) {

        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.setConcurrentConsumers(10);
        simpleMessageListenerContainer.setQueueNames(requests.getName());
        simpleMessageListenerContainer.setAutoStartup(true);
        simpleMessageListenerContainer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Map<String, Object> h = message.getMessageProperties().getHeaders();

                //todo delete this cleanup line
                jdbcTemplate.execute("delete from sfdc_batch");
                jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)",
                        h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));

                  /*


                for (BatchProcessor bp : bps) {
                    bp.onMessage(message);
                }*/

                /// we need to send a reply back to our web app client which is waiting for us (or, at least, it should be!)

                Address replyToDestination = message.getMessageProperties().getReplyToAddress();


                Message out = new Message(((String) h.get("batchId")).getBytes(), new MessageProperties());
                rabbitTemplate.send(replyToDestination.getExchangeName(), replyToDestination.getRoutingKey(), out);

               /* rabbitTemplate.convertAndSend(replyToDestination, h.get("batchId")  ,new MessagePostProcessor() {
                    @Override
                    public Message postProcessMessage(Message message) throws AmqpException {
                        message.getMessageProperties().setCorrelationId( message.getMessageProperties().getCorrelationId());
                        return message ;
                    }
                });*/
            }
        });
        return simpleMessageListenerContainer;
    }

    private <T> T proxy(Class<T> tClass, MethodInterceptor methodInterceptor) {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(methodInterceptor);
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(tClass);
        return tClass.cast(proxyFactoryBean.getObject());
    }

    @Bean
    ForceApi forceApi(final JdbcTemplate jdbcTemplate, final SfdcBatchTemplate sfdcBatchTemplate) {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                String batchId = sfdcBatchTemplate.requiredCurrentBatchId();


                Map<String, Object> row = jdbcTemplate.queryForMap("select * from sfdc_batch where batch_id = ? limit 1", batchId);
                ApiSession session = new ApiSession();
                session.setAccessToken((String) row.get("access_token"));
                session.setApiEndpoint((String) row.get("api_endpoint"));
                ForceApi forceApi = new ForceApi(session);
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
    }
}
