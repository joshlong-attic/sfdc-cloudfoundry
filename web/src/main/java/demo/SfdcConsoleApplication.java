package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.api.Identity;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ImportResource("/salesforceContext.xml")
@Configuration
@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class SfdcConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SfdcConsoleApplication.class, args);
    }

    @Bean
    ForceApi forceApiProxy() {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = ForceSecurityContextHolder.get(true);
                ApiSession session = new ApiSession();
                session.setAccessToken(securityContext.getSessionId());
                session.setApiEndpoint(securityContext.getEndPointHost());
                ForceApi forceApi = new ForceApi(session);
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(ForceApi.class);
        return (ForceApi) proxyFactoryBean.getObject();
    }
}

@Configuration
class SfdcRabbitProducerConfiguration {

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

@RestController
class SfdcRestController {

    @Value("${processor.destination}")
    private String destination;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RequestMapping(value = "/process", method = RequestMethod.POST)
    ResponseEntity<Map<?, ?>> process() {
        SecurityContext securityContext = ForceSecurityContextHolder.get();
        String at = securityContext.getSessionId();
        String endpoint = securityContext.getEndPointHost();
        String uuid = UUID.randomUUID().toString();

        Map<String, String> payload = beginProcessing(uuid, at, endpoint);

        return new ResponseEntity<Map<?, ?>>(payload, HttpStatus.OK);
    }

    protected Map<String, String> beginProcessing(String batchCorrelationId,
                                                  String accessToken,
                                                  String apiEndpoint) {
        Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("batchId", batchCorrelationId);
        stringStringMap.put("accessToken", accessToken);
        stringStringMap.put("apiEndpoint", apiEndpoint);
        this.rabbitTemplate.convertAndSend(this.destination, stringStringMap);
        return stringStringMap;
    }
}

@Controller
class SfdcMvcController {

    @Autowired
    private ForceApi forceApi;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String contacts(Model model) {
        Identity identity = this.forceApi.getIdentity();
        model.addAttribute("id", identity.getId());
        return "console";
    }
}

/*

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

// todo create a Cloud Foundry specific Java configuration class for our RabbitMQ connection-factory*/



/*

class ContactService {
    private Log logger = LogFactory.getLog(getClass());

    //  @Autowired
    private ForceApi forceApi; // thread safe proxy

    ContactService(ForceApi forceApi) {
        this.forceApi = forceApi;
    }

    // @Autowired
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
}*/
/*

*/