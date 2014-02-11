package demo;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Josh Long (josh@joshlong.com)
 */
@Configuration
class RabbitConfiguration {

    @Value("${processor.requests}")
    private String requests = "sfdc_requests";

    @Value("${processor.replies}")
    private String replies = "sfdc_replies";
    private String routingKey = "sfdc";
    private String exchange = "sfdc_exchange";

    @Bean
    RabbitTemplate fixedReplyQRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setExchange(exchange().getName());
        template.setRoutingKey(this.routingKey);
        template.setReplyTimeout(-1); // this means that it should wait forever for a reply. TODO is this too dangerous ?
        template.setReplyQueue(replyQueue());
        return template;
    }

    // this registers the RabbitTemplate as a SMLC so
    // that it can do the right thing for any replies coming back
    @Bean
    SimpleMessageListenerContainer replyListenerContainer(RabbitTemplate rabbitTemplate, ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueues(replyQueue());
        container.setMessageListener(rabbitTemplate);
        return container;
    }

    @Bean
    DirectExchange exchange() {
        return new DirectExchange(this.exchange);
    }

    @Bean
    Binding binding() {
        return BindingBuilder.bind(requestQueue()).to(exchange()).with(this.routingKey);
    }

    @Bean
    Queue requestQueue() {
        return new Queue(this.requests);
    }

    @Bean
    Queue replyQueue() {
        return new Queue(this.replies);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
