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
class RabbitConfiguration {



    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(@Reply Queue replyQueue, ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setQueue(replyQueue.getName() );
        return template;
    }

    @Bean
    @Request
    Queue requests(AmqpAdmin amqpAdmin, @Value("${processor.requests}") String destination) {
        Queue q = new Queue(destination);
        amqpAdmin.declareQueue(q);
        return q;
    }

    @Bean
    @Reply
    Queue replies(AmqpAdmin amqpAdmin, @Value("${processor.replies}") String destination) {
        Queue q = new Queue(destination);
        amqpAdmin.declareQueue(q);
        return q;
    }

    @Bean
    @Reply
    DirectExchange repliesExchange(AmqpAdmin amqpAdmin, @Reply Queue queue) {
        DirectExchange directExchange = new DirectExchange(queue.getName());
        amqpAdmin.declareExchange(directExchange);
        return directExchange;
    }

    @Bean
    @Request
    DirectExchange requestsExchange(AmqpAdmin amqpAdmin, @Request Queue queue) {
        DirectExchange directExchange = new DirectExchange(queue.getName());
        amqpAdmin.declareExchange(directExchange);
        return directExchange;
    }

    @Bean
    @Reply
    Binding replyBinding(@Reply Queue q, @Reply DirectExchange e, @Value("${processor.replies}") String d) {
        return BindingBuilder
                .bind(q)
                .to(e)
                .with(d);
    }

    @Bean
    @Request
    Binding requestBinding(@Request Queue q, @Request DirectExchange e, @Value("${processor.requests}") String d) {
        return BindingBuilder
                .bind(q)
                .to(e)
                .with(d);
    }

}