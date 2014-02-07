package demo;

import com.force.api.ForceApi;
import com.force.api.QueryResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ImportResource("/salesforceContext.xml")
@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class Application {

    public final static String CONTACTS = "contacts";

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ForceApiFactoryBean forceApiFactoryBean() {
        return new ForceApiFactoryBean();
    }
}

@Configuration
class RabbitProducerConfiguration {

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rt = new RabbitTemplate(connectionFactory);
        rt.setMessageConverter(messageConverter);
        return rt;
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
}


@Service
class ContactService {
    private Log logger = LogFactory.getLog(getClass());

    @Autowired
    private ForceApi forceApi; // thread safe proxy

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public List<Contact> listContacts() {
        QueryResult<Contact> res = forceApi.query(
                "SELECT MailingState, MailingCountry, MailingStreet , MailingPostalCode, Email,  Id, FirstName, LastName FROM contact", Contact.class);
        List<Contact> contacts = new ArrayList<>();
        for (Contact c : res.getRecords())
            if (StringUtils.hasText(c.getMailingStreet()))
                contacts.add(c);
        return contacts;
    }

    public void process(Contact contact) {
        try {
            this.rabbitTemplate.convertAndSend(Application.CONTACTS, contact);
        } catch (Throwable ex) {
            logger.debug("couldn't send the message!", ex);
        }
    }

    public void removeContact(String id) {
        forceApi.deleteSObject("contact", id);
    }

    public void addContact(Contact contact) {
        forceApi.createSObject("contact", contact);
    }
}

@RestController
class ContactRestController {

    @Autowired
    private ContactService contactService;


    @RequestMapping("/contacts")
    public List<Contact> contacts() {
        return this.contactService.listContacts();
    }

    @RequestMapping(value = "/map", method = RequestMethod.POST)
    public void processContact(@RequestBody Contact contact) {
        this.contactService.process(contact);
    }
}

@Controller
class ContactMvcController {

    @RequestMapping("/")
    public String home() {
        return "contacts";
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