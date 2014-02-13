package demo;

import com.force.api.ForceApi;
import com.force.api.Identity;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import com.force.sdk.springsecurity.OAuthAuthenticationToken;
import org.apache.log4j.Logger;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * @author Josh Long (josh@joshlong.com)
 */
@RestController
class SfdcRestController {

    @Value("${processor.requests}")
    String destination;

    //@Autowired
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
