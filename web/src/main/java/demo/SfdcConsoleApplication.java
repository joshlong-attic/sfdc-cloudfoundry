package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.api.Identity;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import com.force.sdk.springsecurity.OAuthAuthenticationToken;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
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
    SecurityContext securityContext() {
        return this.proxy(SecurityContext.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = ForceSecurityContextHolder.get(true);
                return invocation.getMethod().invoke(securityContext, invocation.getArguments());
            }
        });
    }

    @Bean
    ForceApi forceApiProxy() {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                SecurityContext securityContext = securityContext();
                ApiSession session = new ApiSession();
                session.setAccessToken(securityContext.getSessionId());
                session.setApiEndpoint(securityContext.getEndPointHost());
                ForceApi forceApi = new ForceApi(session);
                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
    }

    private <T> T proxy(Class<T> tClass, MethodInterceptor methodInterceptor) {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(methodInterceptor);
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(tClass);
        return tClass.cast(proxyFactoryBean.getObject());
    }
}

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

/**
 * Meant to represent a record from SFDC (e.g., a contact or a lead)
 */
class SfdcPerson {
    private String street, email, city, state, postalCode, batchId, recordType;
    private double latitude, longitude;

    public SfdcPerson(String street, String email, String city, String state, String postalCode, String batchId, String recordType, double latitude, double longitude) {
        this.street = street;
        this.email = email;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.batchId = batchId;
        this.latitude = latitude;
        this.longitude = longitude;

        Assert.hasText(recordType);
        this.recordType = this.recordType.toLowerCase();

        Assert.isTrue(this.recordType.equalsIgnoreCase("lead") ||
                this.recordType.equalsIgnoreCase("contact"));
    }

    public String getStreet() {
        return street;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getRecordType() {
        return recordType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}

@Service
class SfdcPeopleService {

    @Autowired
    JdbcTemplate jdbcTemplate;

    private RowMapper<SfdcPerson> sfdcPersonRowMapper = new RowMapper<SfdcPerson>() {
        @Override
        public SfdcPerson mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SfdcPerson(rs.getString("street"), rs.getString("email"), rs.getString("city"), rs.getString("state"),
                    rs.getString("postcal_code"), rs.getString("batch_id"), rs.getString("record_type"), rs.getDouble("latitude"), rs.getDouble("longitude"));
        }
    };

    List<SfdcPerson> results(String batchId) {
        return this.jdbcTemplate.query("select * from sfdc_directory where batch_id = ?", this.sfdcPersonRowMapper, batchId);
    }

    List<SfdcPerson> geolocated(String batchId) {
        return this.jdbcTemplate.query("select * from sfdc_directory where latitude is not null and longitude is not null and batch_id = ? ", this.sfdcPersonRowMapper, batchId);
    }

}

/*
// todo consider using websockets
class Greeting {
    String text;

    Greeting() {
    }

    Greeting(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

@Controller
class WebSocketController {

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public Greeting greeting(Greeting message) throws Exception {
        Thread.sleep(3000); // simulated delay
        return new Greeting("Hello, " + message.getText() + "!");
    }

}*/


@RestController
class SfdcRestController {

    @Value("${processor.requests}")
    String destination;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ForceApi forceApi;

    Logger logger = Logger.getLogger(getClass());

    @Autowired
    SfdcPeopleService sfdcPeopleService;

    @RequestMapping(value = "/user", method = RequestMethod.GET)
    Identity user() {
        return forceApi.getIdentity();
    }

    @RequestMapping(value = "/process", method = RequestMethod.POST)
    ResponseEntity<Map<?, ?>> process(OAuthAuthenticationToken t, @RequestBody String query) {
        logger.debug("the principal is " + t.getName());

        SecurityContext securityContext = ForceSecurityContextHolder.get();

        String accessToken = securityContext.getSessionId();
        String endpoint = securityContext.getEndPointHost();
        String uuid = UUID.randomUUID().toString() + System.currentTimeMillis() + "";

        final Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("batchId", uuid);
        stringStringMap.put("accessToken", accessToken);
        stringStringMap.put("apiEndpoint", endpoint);
        stringStringMap.put("query", query);

        String batchId = (String) this.rabbitTemplate.convertSendAndReceive(
                (Object) query, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                for (String h : stringStringMap.keySet()) {
                    message.getMessageProperties().setHeader(h, stringStringMap.get(h));
                }
                return message;
            }
        });
        // the client will know to update the results page once this REST endpoint returns.
        // todo could we refactor this so that client is notified of the updated state by a websocket? \
        // todo could we maybe refactor this to use async servlets?

        log("received batchId: " + batchId);

        return new ResponseEntity<Map<?, ?>>(stringStringMap, HttpStatus.OK);
    }

    @RequestMapping("/results/{batchId}")
    List<SfdcPerson> results(@PathVariable String batchId) {
        return this.sfdcPeopleService.results(batchId);
    }

    protected void log(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

}

@Controller
class SfdcMvcController {

    @Autowired
    ForceApi forceApi;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    String contacts(Model model) {
        Identity identity = this.forceApi.getIdentity();
        model.addAttribute("id", identity.getId());
        return "maps";
    }
}