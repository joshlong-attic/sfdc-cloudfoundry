package demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.api.QueryResult;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@ComponentScan
@EnableAutoConfiguration(exclude = SecurityAutoConfiguration.class)
public class SfdcEnrichmentProcessors {
    public static void main(String[] args) {
        SpringApplication.run(SfdcEnrichmentProcessors.class, args);
    }
}

@Configuration
class SfdcProcessorsConfiguration {
    Logger logger = Logger.getLogger(getClass());

    private <T> T proxy(Class<T> tClass, MethodInterceptor methodInterceptor) {
        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.addAdvice(methodInterceptor);
        proxyFactoryBean.setProxyTargetClass(true);
        proxyFactoryBean.setTargetClass(tClass);
        return tClass.cast(proxyFactoryBean.getObject());
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
    ForceApi forceApi(final JdbcTemplate jdbcTemplate, final SfdcBatchTemplate sfdcBatchTemplate) {
        return this.proxy(ForceApi.class, new MethodInterceptor() {
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                String batchId = sfdcBatchTemplate.requiredCurrentBatchId();
                Map<String, Object> row = jdbcTemplate.queryForMap("select * from sfdc_batch where batch_id = ?", batchId);
                SecurityContext securityContext = securityContext();
                ApiSession session = new ApiSession();
                session.setAccessToken(securityContext.getSessionId());
                session.setApiEndpoint(securityContext.getEndPointHost());
                ForceApi forceApi = new ForceApi(session);

                return invocation.getMethod().invoke(forceApi, invocation.getArguments());
            }
        });
    }
}

@Component
class SfdcBatchTemplate {
    ThreadLocal<String> batchIdThreadLocal = new ThreadLocal<>();
    JdbcTemplate jdbcTemplate;

    @Autowired
    SfdcBatchTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    String requiredCurrentBatchId() {
        Assert.notNull(this.batchIdThreadLocal.get(), "something's wrong, there's no batchId");
        return this.batchIdThreadLocal.get();
    }

    void doInBatch(String batchId, BatchCallback batchCallback) {
        this.batchIdThreadLocal.set(batchId);
        try {
            batchCallback.doInBatch(batchId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.batchIdThreadLocal.remove();
        }
    }

    static interface BatchCallback {
        void doInBatch(String batchId) throws Exception;
    }
}


abstract class AbstractSfdcBatchProcessor implements MessageListener {

    SfdcBatchTemplate sfdcBatchTemplate;

    AbstractSfdcBatchProcessor(SfdcBatchTemplate sfdcBatchTemplate) {
        this.sfdcBatchTemplate = sfdcBatchTemplate;
    }

    @Override
    public void onMessage(final Message message) {
        Map<String, Object> headers = message.getMessageProperties().getHeaders();
        String batchId = (String) headers.get("batchId");
        this.sfdcBatchTemplate.doInBatch(batchId, new SfdcBatchTemplate.BatchCallback() {
            @Override
            public void doInBatch(String batchId) throws Exception {
                doProcessMessage(batchId, message);
            }
        });
    }

    public abstract void doProcessMessage(String batchId, Message msg);
}

@Component
class SfdcContactEnrichingProcessor extends AbstractSfdcBatchProcessor {
    ForceApi forceApi;
    Logger logger = Logger.getLogger(getClass());

    @Autowired
    SfdcContactEnrichingProcessor(SfdcBatchTemplate sfdcBatchTemplate, ForceApi forceApi) {
        super(sfdcBatchTemplate);
        this.forceApi = forceApi;
    }

    List<Contact> contacts() {
        QueryResult<Contact> res = forceApi.query(
                "SELECT MailingState, MailingCountry, MailingStreet , MailingPostalCode, Email,  Id, FirstName, LastName FROM contact", Contact.class);
        List<Contact> contacts = new ArrayList<>();
        for (Contact c : res.getRecords())
            if (StringUtils.hasText(c.getMailingStreet()))
                contacts.add(c);
        return contacts;
    }

    @Override
    public void doProcessMessage(String batchId, Message msg) {
        for (Contact c : contacts())
            logger.debug(c.toString());
    }
}


@Configuration
class RabbitConsumerConfiguration {

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @SuppressWarnings("unchecked")
    SimpleMessageListenerContainer simpleMessageListenerContainer(
            Queue customerQueue,
            final JdbcTemplate jdbcTemplate,
            ConnectionFactory connectionFactory) {

        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.setConcurrentConsumers(10);
        simpleMessageListenerContainer.setQueues(customerQueue);
        simpleMessageListenerContainer.setAutoStartup(true);
        simpleMessageListenerContainer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Map<String, Object> h = message.getMessageProperties().getHeaders();
                jdbcTemplate.update("insert into sfdc_batch(batch_id, api_endpoint, access_token) values(?,?,?)",
                        h.get("batchId"), h.get("apiEndpoint"), h.get("accessToken"));
            }
        });
        return simpleMessageListenerContainer;
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

    public String getMailingCity() {
        return mailingCity;
    }

    public void setMailingCity(String mailingCity) {
        this.mailingCity = mailingCity;
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