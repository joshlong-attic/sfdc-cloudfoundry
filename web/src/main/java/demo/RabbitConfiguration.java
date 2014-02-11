package demo;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
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
    RabbitTemplate fixedReplyQRabbitTemplate(Exchange exchange, @Qualifier("replyQueue") Queue replyQueue, ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setExchange(exchange.getName());
        template.setRoutingKey(this.routingKey);
        template.setReplyTimeout(-1); // this means that it should wait forever for a reply. TODO is this too dangerous ?
        template.setReplyQueue(replyQueue);
        return template;
    }

    @Bean
    AmqpAdmin amqpAdmin( ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    // this registers the RabbitTemplate as a SMLC so
    // that it can do the right thing for any replies coming back
    @Bean
    SimpleMessageListenerContainer replyListenerContainer(
            @Qualifier("replyQueue") Queue replyQueue,
            RabbitTemplate rabbitTemplate, ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueues(replyQueue);
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
    Binding binding(AmqpAdmin amqpAdmin, DirectExchange exchange, @Qualifier("requestQueue") Queue queue) {
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(this.routingKey);
        amqpAdmin.declareBinding(binding);
        return binding;
    }

    @Bean
    Queue requestQueue(AmqpAdmin amqpAdmin) {
        Queue queue = new Queue(this.requests);
        amqpAdmin.declareQueue(queue);
        return queue;
    }

    @Bean
    Queue replyQueue(AmqpAdmin amqpAdmin) {
        Queue queue = new Queue(this.replies);
        amqpAdmin.declareQueue(queue);
        return queue;
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}