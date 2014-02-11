package demo.processors;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;


@Configuration
class RabbitConfiguration {


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
    SimpleMessageListenerContainer serviceListenerContainer(
//            ContactProcessor contactProcessor,
//            ContactGeocodingProcessor contactGeocodingProcessor,
            LeadProcessor leadProcessor,
            LeadGeocodingProcessor leadGeocodingProcessor,
            JdbcTemplate jdbcTemplate, ConnectionFactory rabbitConnectionFactory) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();

        // todo restore all processors
        // BatchProcessor[] contactProcessors = new BatchProcessor[]{contactProcessor, contactGeocodingProcessor};
        // BatchProcessor[] allBatchProcessors = new BatchProcessor[]{contactProcessor, contactGeocodingProcessor, leadProcessor, leadGeocodingProcessor};

        BatchProcessor[] leadProcessors = new BatchProcessor[]{leadProcessor, leadGeocodingProcessor};
        PojoListener pojoListener = new PojoListener(jdbcTemplate, leadProcessors);
        SimpleMessageConverter simpleMessageConverter = new NoOpSimpleMessageConverter();
        container.setMessageListener(new MessageListenerAdapter(pojoListener, simpleMessageConverter));
        container.setConnectionFactory(rabbitConnectionFactory);
        container.setQueues(requestQueue());
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
