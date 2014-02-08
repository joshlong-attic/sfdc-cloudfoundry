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
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// sets up views that in turn handle
// testing out Spring MVC's websocket support
@Configuration
class ConsumerController {


}

@ComponentScan
@EnableAutoConfiguration
public class Application {

    public final static String CONTACTS = "contacts";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
/*

class HelloMessage {

    private String name;

    public String getName() {
        return name;
    }
}

class Greeting {

    private String content;

    public Greeting(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}


@Controller
class GreetingController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public Greeting greeting(HelloMessage message) throws Exception {
        Thread.sleep(3000); // simulated delay
        return new Greeting("Hello, " + message.getName() + "!");
    }
}

*/

// register some pages
@Component
class ConsumerMvcConfiguration extends WebMvcConfigurerAdapter {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {

        Map<String, String> mappings = new HashMap<>();
        mappings.put("/", "index");

        String[] pages = {"consumer"};
        for (String x : pages) {
            mappings.put("/" + x, x);
        }

        for (String k : mappings.keySet()) {
            registry.addViewController(k).setViewName(mappings.get(k));
        }
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/hello").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration channelRegistration) {
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration channelRegistration) {
    }

    @Override
    public boolean configureMessageConverters(List<org.springframework.messaging.converter.MessageConverter> messageConverters) {
        return true;
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
        smlc.setAutoStartup(true);
        smlc.setQueues(customerQueue);
        smlc.setConcurrentConsumers(10);
        smlc.setConnectionFactory(connectionFactory);
        return smlc;
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