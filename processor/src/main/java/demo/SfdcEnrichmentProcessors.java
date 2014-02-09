package demo;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class SfdcEnrichmentProcessors {
    public static void main(String[] args) {
        SpringApplication.run(SfdcEnrichmentProcessors.class, args);
    }
}


@Configuration
class RabbitConsumerConfiguration {

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


