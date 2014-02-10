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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
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
        template.setReplyTimeout(1000 * 30);
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

}


@RestController
class SfdcRestController {

    @Value("${processor.requests}")
    String destination;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ForceApi forceApi;

    private Logger logger = Logger.getLogger(getClass());

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

        log("received batchId: " + batchId);

        return new ResponseEntity<Map<?, ?>>(stringStringMap, HttpStatus.OK);
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