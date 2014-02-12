package demo.processors;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;


@Configuration
class RabbitConfiguration {
    public static final String REQUEST_Q = "sfdc_requests";
    public static final String REPLY_Q = "sfdc_replies";

    @Value("${processor.requests}")
    private String requests = REQUEST_Q;//"sfdc_requests";

    @Value("${processor.replies}")
    private String replies = REPLY_Q;// "sfdc_replies";

    private String routingKey = "sfdc";

    private String exchange = "sfdc_exchange";

    @Bean
    RabbitTemplate fixedReplyQRabbitTemplate(Exchange exchange, @Qualifier(REPLY_Q) Queue replyQueue, ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setExchange(exchange.getName());
        template.setRoutingKey(this.routingKey);
        template.setReplyTimeout(-1); // this means that it should wait forever for a reply. TODO is this too dangerous ?
        template.setReplyQueue(replyQueue);
        return template;
    }

    @Bean
    AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // this registers the RabbitTemplate as a SMLC so
    // that it can do the right thing for any replies coming back
    @Bean
    SimpleMessageListenerContainer replyListenerContainer(
            RabbitTemplate rabbitTemplate, ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(REPLY_Q);
        container.setMessageListener(rabbitTemplate);
        return container;
    }

    @Bean
    DirectExchange exchange(AmqpAdmin amqpAdmin) {
        DirectExchange directExchange = new DirectExchange(this.exchange);
        amqpAdmin.declareExchange(directExchange);
        return directExchange;
    }

    @Bean
    Binding binding(AmqpAdmin amqpAdmin, DirectExchange exchange, @Qualifier(REQUEST_Q) Queue queue) {
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(this.routingKey);
        amqpAdmin.declareBinding(binding);
        return binding;
    }

    @Bean(name = REQUEST_Q)
    Queue requestQueue(AmqpAdmin amqpAdmin) {
        Queue queue = new Queue(this.requests);
        amqpAdmin.declareQueue(queue);
        return queue;
    }

    @Bean(name = REPLY_Q)
    Queue replyQueue(AmqpAdmin amqpAdmin) {
        Queue queue = new Queue(this.replies);
        amqpAdmin.declareQueue(queue);
        return queue;
    }


    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleMessageListenerContainer serviceListenerContainer(
            LeadProcessor leadProcessor, @Qualifier(REQUEST_Q) Queue queue,
            LeadGeocodingProcessor leadGeocodingProcessor,
            JdbcTemplate jdbcTemplate, ConnectionFactory rabbitConnectionFactory) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        BatchProcessor[] leadProcessors = new BatchProcessor[]{leadProcessor, leadGeocodingProcessor};
        PojoListener pojoListener = new PojoListener(jdbcTemplate, leadProcessors);
        SimpleMessageConverter simpleMessageConverter = new NoOpSimpleMessageConverter();
        container.setMessageListener(new MessageListenerAdapter(pojoListener, simpleMessageConverter));
        container.setConnectionFactory(rabbitConnectionFactory);
        container.setQueues(queue);
        return container;
    }

    public static class NoOpSimpleMessageConverter
            extends SimpleMessageConverter {
        @Override
        public Object fromMessage(Message message) throws MessageConversionException {
            return message;
        }
    }

    public static class PojoListener {
        private JdbcTemplate jdbcTemplate;
        private BatchProcessor[] batchProcessors;

        public PojoListener(JdbcTemplate jdbcTemplate,
                            BatchProcessor[] batchProcessors) {
            this.jdbcTemplate = jdbcTemplate;
            this.batchProcessors = batchProcessors;
        }

        public String handleMessage(Message msg) throws Exception {
            Map<String, Object> h = msg.getMessageProperties().getHeaders();
            //jdbcTemplate.execute("delete from sfdc_batch"); //todo delete this cleanup line
            jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)", h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));

            for (BatchProcessor batchProcessor : this.batchProcessors)
                batchProcessor.onMessage(msg);

            return (String) h.get("batchId");
        }
    }
}
