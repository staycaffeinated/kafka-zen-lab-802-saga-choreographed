package zen.labs.shipping.infrastructure.config;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/*
 * This is a convenience class that creates the standard topics used by the application.
 * Auto-creating topics and state stores is usually acceptable for local development.
 * Production environments usually have Kubernetes, Terraform or an equivalent tool
 * create the topics.
 */

@Component
@Slf4j
public class TopicProvisioner {

    private final KafkaAdmin kafkaAdmin;
    private final String shippingEvents;
    private final String paymentEvents;
    private final String orderEvents;

    public TopicProvisioner(
            KafkaAdmin kafkaAdmin,
            @Value("${application.saga.topics.shipping-events}") String shippingEvents,
            @Value("${application.saga.topics.payment-events}") String paymentEvents,
            @Value("${application.saga.topics.order-events}") String orderEvents) {
        this.kafkaAdmin = kafkaAdmin;
        this.shippingEvents = shippingEvents;
        this.paymentEvents = paymentEvents;
        this.orderEvents = orderEvents;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createTopics() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            List<NewTopic> topics = topicNames().stream().map(this::createTopic).toList();
            client.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Topics already exist");
            } else {
                log.error("Failed to create Kafka topics", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Topic creation interrupted", e);
        } catch (TimeoutException e) {
            log.error("Timed out waiting for topic creation", e);
        }
    }

    private List<String> topicNames() {
        return List.of(shippingEvents, paymentEvents, orderEvents, paymentEvents + ".dlq", orderEvents + ".dlq");
    }

    /*
     * The brokers' default number of partitions and replicas will be used
     * if those values are not explicitly set here.
     */
    private NewTopic createTopic(String topicName) {
        return TopicBuilder.name(topicName).build();
    }
}
