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
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.RabbitServiceInfo;
import org.springframework.cloud.service.common.RelationalServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;
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
            SfdcContactProcessor contactProcessor,
            SfdcContactGeolocationProcessor sfdcContactGeolocationProcessor,
            SfdcLeadProcessor leadProcessor,
            SfdcLeadGeolocationProcessor sfdcLeadGeolocationProcessor,
            JdbcTemplate jdbcTemplate, ConnectionFactory rabbitConnectionFactory) {

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        // todo restore all processors
        BatchProcessor[] contactProcessors =
                new BatchProcessor[]{contactProcessor, sfdcContactGeolocationProcessor};
        BatchProcessor[] leadProcessors =
                new BatchProcessor[]{leadProcessor, sfdcLeadGeolocationProcessor};
        BatchProcessor[] allBatchProcessors =
                new BatchProcessor[]{contactProcessor, sfdcContactGeolocationProcessor, leadProcessor, sfdcLeadGeolocationProcessor};

        PojoListener pojoListener = new PojoListener(jdbcTemplate, allBatchProcessors);

        container.setMessageListener(new MessageListenerAdapter(pojoListener, new NoOpSimpleMessageConverter()));
        container.setConnectionFactory(rabbitConnectionFactory);
        container.setQueues(requestQueue());
        return container;
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

        public PojoListener(JdbcTemplate jdbcTemplate, BatchProcessor[] batchProcessors) {
            this.jdbcTemplate = jdbcTemplate;
            this.batchProcessors = batchProcessors;
        }

        public String handleMessage(Message msg) throws Exception {
            Map<String, Object> h = msg.getMessageProperties().getHeaders();
            jdbcTemplate.execute("delete from sfdc_batch"); //todo delete this cleanup line
            jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)",
                    h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));

            for (BatchProcessor batchProcessor : this.batchProcessors)
                batchProcessor.onMessage(msg);

            return (String) h.get("batchId");
        }
    }
}
