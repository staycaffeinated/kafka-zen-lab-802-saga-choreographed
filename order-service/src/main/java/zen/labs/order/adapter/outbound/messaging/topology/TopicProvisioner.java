package zen.labs.order.adapter.outbound.messaging.topology;

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
    private final String orderCommands;
    private final String paymentCommands;
    private final String inventoryCommands;
    private final String shippingCommands;
    private final String orchestratorReplies;

    public TopicProvisioner(
            KafkaAdmin kafkaAdmin,
            @Value("${application.saga.topics.order-commands}") String orderCommands,
            @Value("${application.saga.topics.payment-commands}") String paymentCommands,
            @Value("${application.saga.topics.inventory-commands}") String inventoryCommands,
            @Value("${application.saga.topics.shipping-commands}") String shippingCommands,
            @Value("${application.saga.topics.orchestrator-replies}") String orchestratorReplies) {
        this.kafkaAdmin = kafkaAdmin;
        this.orderCommands = orderCommands;
        this.paymentCommands = paymentCommands;
        this.inventoryCommands = inventoryCommands;
        this.shippingCommands = shippingCommands;
        this.orchestratorReplies = orchestratorReplies;
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
        return List.of(orderCommands, paymentCommands, inventoryCommands, shippingCommands, orchestratorReplies);
    }

    /*
     * The brokers' default number of partitions and replicas will be used
     * if those values are not explicitly set here.
     * For guidance on choosing the number of partitions, see:
     * https://www.confluent.io/blog/how-choose-number-topics-partitions-kafka-cluster/
     * https://www.confluent.io/blog/kafka-streams-tables-part-2-topics-partitions-and-storage-fundamentals/
     */
    private NewTopic createTopic(String topicName) {
        return TopicBuilder.name(topicName).build();
    }
}
