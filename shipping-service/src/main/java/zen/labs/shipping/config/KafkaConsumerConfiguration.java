/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@EnableConfigurationProperties(SagaTopicsProperties.class)
public class KafkaConsumerConfiguration {

    @Bean
    public DefaultErrorHandler buildErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3));
    }
}
