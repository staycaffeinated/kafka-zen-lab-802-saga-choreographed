package zen.labs.adapters.common.kafka;

public final class KafkaTopics {

    // Orchestrator → participant services
    public static final String ORDER_COMMANDS = "kafka-zen.lab-801.saga.order.commands";
    public static final String PAYMENT_COMMANDS = "kafka-zen.lab-801.saga.payment.commands";
    public static final String INVENTORY_COMMANDS = "kafka-zen.lab-801.saga.inventory.commands";
    public static final String SHIPPING_COMMANDS = "kafka-zen.lab-801.saga.shipping.commands";

    // All participant services → orchestrator
    public static final String ORCHESTRATOR_REPLIES = "kafka-zen.lab-801.saga.orchestrator.replies";

    private KafkaTopics() {}
}
