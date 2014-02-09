package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;


@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class SfdcEnrichmentProcessors {
    public static void main(String[] args) {
        SpringApplication.run(SfdcEnrichmentProcessors.class, args);
    }
}

@Configuration
class SfdcProcessorsConfiguration {

    Logger logger = Logger.getLogger(getClass());

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
                Map<String, Object> row = jdbcTemplate.queryForMap("select * from sfdc_batch where batch_id = ?", batchId);
                ApiSession session = new ApiSession();
                session.setAccessToken((String) row.get("access_token"));
                session.setApiEndpoint((String) row.get("api_endpoint"));
                ForceApi forceApi = new ForceApi(session);
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
    }
}


@Configuration
class RabbitConsumerConfiguration {

    @Bean
    @SuppressWarnings("unchecked")
    SimpleMessageListenerContainer simpleMessageListenerContainer(
            Queue customerQueue,
            final JdbcTemplate jdbcTemplate,
            ConnectionFactory connectionFactory) {

        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.setConcurrentConsumers(10);
        simpleMessageListenerContainer.setQueues(customerQueue);
        simpleMessageListenerContainer.setAutoStartup(true);
        simpleMessageListenerContainer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Map<String, Object> h = message.getMessageProperties().getHeaders();
                jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)",
                        h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));


            }
        });
        return simpleMessageListenerContainer;
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    Queue customerQueue(AmqpAdmin amqpAdmin, @Value("${processor.destination}") String destination) {
        Queue q = new Queue(destination);
        amqpAdmin.declareQueue(q);
        return q;
    }

    @Bean
    DirectExchange customerExchange(AmqpAdmin amqpAdmin, @Value("${processor.destination}") String destination) {
        DirectExchange directExchange = new DirectExchange(destination);
        amqpAdmin.declareExchange(directExchange);
        return directExchange;
    }

    @Bean
    Binding marketDataBinding(Queue customerQueue, DirectExchange directExchange, @Value("${processor.destination}") String destination) {
        return BindingBuilder
                .bind(customerQueue)
                .to(directExchange)
                .with(destination);
    }
}


