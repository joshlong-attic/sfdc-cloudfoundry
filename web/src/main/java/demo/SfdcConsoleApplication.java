package demo;

import com.force.api.ApiSession;
import com.force.api.ForceApi;
import com.force.api.Identity;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Request {
}

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@interface Reply {
}

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


    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(@Request Queue request, @Reply Queue replyQueue, ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
      /*  template.setReplyQueue(replyQueue);
        template.setQueue(request.getName());*/
        template.setReplyTimeout(30 * 1000);
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

@RestController
class SfdcRestController {

    @Value("${processor.requests}")
    String destination;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    ForceApi forceApi;

    @Request
    @Autowired
    Queue requests;

    @Reply
    @Autowired
    Queue replies;

    @RequestMapping(value = "/user", method = RequestMethod.GET)
    Identity user() {
        return forceApi.getIdentity();
    }

    @RequestMapping(value = "/process", method = RequestMethod.POST)
    ResponseEntity<Map<?, ?>> process() {
        SecurityContext securityContext = ForceSecurityContextHolder.get();
        String at = securityContext.getSessionId();
        String endpoint = securityContext.getEndPointHost();
        String uuid = UUID.randomUUID().toString() + System.currentTimeMillis() + "";

        Map<String, String> payload = beginProcessing(uuid, at, endpoint);

        return new ResponseEntity<Map<?, ?>>(payload, HttpStatus.OK);
    }

    private Map<String, String> beginProcessing(final String batchId,
                                                final String accessToken,
                                                final String apiEndpoint) {
        final Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("batchId", batchId);
        stringStringMap.put("accessToken", accessToken);
        stringStringMap.put("apiEndpoint", apiEndpoint);
        Object msg = rabbitTemplate.convertSendAndReceive(this.requests.getName(),
                (Object) batchId,
                new MessagePostProcessor() {
                    @Override
                    public Message postProcessMessage(Message message) throws AmqpException {
                        MessageProperties messageProperties = message.getMessageProperties();
                        for (String h : stringStringMap.keySet())
                            messageProperties.setHeader(h, stringStringMap.get(h));
//                        messageProperties.setCorrelationId(batchId.getBytes());
//                        messageProperties.setReplyTo( );
                        return message;
                    }
                });

        System.out.println(null == msg ? "" : msg.toString());


        return stringStringMap;
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
        return "console";
    }
}