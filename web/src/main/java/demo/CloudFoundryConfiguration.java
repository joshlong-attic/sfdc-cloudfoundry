package demo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudFactory;
import org.springframework.cloud.service.ServiceInfo;
import org.springframework.cloud.service.common.RabbitServiceInfo;
import org.springframework.cloud.service.common.RelationalServiceInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.List;

/**
 * this should work inside of cloud foundry
 *
 * @author Josh Long (josh@joshlong.com)
 */
@Configuration
@Profile("cloud")
class CloudFoundryConfiguration {

    Log log = LogFactory.getLog(getClass());

    @PostConstruct
    public void setup() {
        log.debug(getClass().getName() + " has been loaded!");
    }

    @Bean
    DataSource dataSource(Cloud cloud) {
        List<ServiceInfo> serviceInfos = cloud.getServiceInfos(RelationalServiceInfo.class);
        Assert.isTrue(serviceInfos.size() > 0);
        ServiceInfo serviceInfo = serviceInfos.get(0);
        String serviceId = serviceInfo.getId();
        return cloud.getServiceConnector(serviceId, DataSource.class, null);
    }

    @Bean
    ConnectionFactory connectionFactory(Cloud cloud) {
        List<ServiceInfo> serviceInfos = cloud.getServiceInfos(RabbitServiceInfo.class);
        Assert.isTrue(serviceInfos.size() > 0);
        ServiceInfo serviceInfo = serviceInfos.get(0);
        String serviceId = serviceInfo.getId();
        return cloud.getServiceConnector(serviceId, ConnectionFactory.class, null);
    }

    @Bean
    CloudFactory cloudFactory() {
        return new CloudFactory();
    }

    @Bean
    Cloud cloud(CloudFactory cloudFactory) {
        return cloudFactory.getCloud();
    }
}
