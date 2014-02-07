package demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@ComponentScan
@EnableAutoConfiguration
public class Application {

    public final static String CONTACTS = "contacts";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@Configuration
class RabbitConsumerConfiguration {

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rt = new RabbitTemplate(connectionFactory);
        rt.setMessageConverter(messageConverter);
        return rt;
    }

    @Bean
    TaskScheduler taskScheduler(ScheduledExecutorService simpleAsyncTaskExecutor) {
        return new ConcurrentTaskScheduler(simpleAsyncTaskExecutor);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    Queue customerQueue(AmqpAdmin amqpAdmin) {
        Queue q = new Queue(Application.CONTACTS);
        amqpAdmin.declareQueue(q);
        return q;
    }

    @Bean
    DirectExchange customerExchange(AmqpAdmin amqpAdmin) {
        DirectExchange directExchange = new DirectExchange(Application.CONTACTS);
        amqpAdmin.declareExchange(directExchange);
        return directExchange;
    }

    @Bean
    Binding marketDataBinding(Queue customerQueue, DirectExchange directExchange) {
        return BindingBuilder
                .bind(customerQueue)
                .to(directExchange)
                .with(Application.CONTACTS);
    }

    @Bean
    SimpleMessageListenerContainer simpleMessageListenerContainer(
            TaskExecutor taskExecutor,
            Queue customerQueue,
            final MessageConverter jsonMessageConverter,
            ConnectionFactory connectionFactory) {

        SimpleMessageListenerContainer smlc = new SimpleMessageListenerContainer();
        smlc.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Contact contact = (Contact) jsonMessageConverter.fromMessage(message);
                System.out.println("Received new contact " + contact.toString());
            }
        });
        smlc.setTaskExecutor(taskExecutor);
        smlc.setAutoStartup(true);
        smlc.setQueues(customerQueue);
        smlc.setConcurrentConsumers(10);
        smlc.setConnectionFactory(connectionFactory);
        return smlc;
    }

    @Bean
    ScheduledExecutorService taskExecutor() {
        return Executors.newScheduledThreadPool(10);
    }
}


@JsonIgnoreProperties(ignoreUnknown = true)
class Contact {

    @JsonProperty(value = "Id")
    private String id;

    @JsonProperty(value = "FirstName")
    private String firstName;

    @JsonProperty(value = "LastName")
    private String lastName;

    @JsonProperty(value = "MailingCity")
    private String mailingCity;

    @JsonProperty(value = "MailingState")
    private String mailingState;

    @JsonProperty(value = "MailingCountry")
    private String mailingCountry;

    @JsonProperty(value = "MailingStreet")
    private String mailingStreet;

    @JsonProperty(value = "MailingPostalCode")
    private String mailingPostalCode;

    @JsonProperty(value = "Email")
    private String email;

    public void setMailingCity(String mailingCity) {
        this.mailingCity = mailingCity;
    }

    public String getMailingCity() {
        return mailingCity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMailingState() {
        return mailingState;
    }

    public void setMailingState(String mailingState) {
        this.mailingState = mailingState;
    }

    public String getMailingCountry() {
        return mailingCountry;
    }

    public void setMailingCountry(String mailingCountry) {
        this.mailingCountry = mailingCountry;
    }

    public String getMailingStreet() {
        return mailingStreet;
    }

    public void setMailingStreet(String mailingStreet) {
        this.mailingStreet = mailingStreet;
    }

    public String getMailingPostalCode() {
        return mailingPostalCode;
    }

    public void setMailingPostalCode(String mailingPostalCode) {
        this.mailingPostalCode = mailingPostalCode;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

// todo create a Cloud Foundry specific Java configuration class for our RabbitMQ connection-factory