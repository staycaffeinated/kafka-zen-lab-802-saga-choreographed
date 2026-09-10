package zen.labs.adapters.common.kafka;

public final class KafkaTopics {

    public static final String ORDER_EVENTS = "kafka-zen.lab-802.saga.order.events";
    public static final String INVENTORY_EVENTS = "kafka-zen.lab-802.saga.inventory.events";
    public static final String PAYMENT_EVENTS = "kafka-zen.lab-802.saga.payment.events";
    public static final String SHIPPING_EVENTS = "kafka-zen.lab-802.saga.shipping.events";

    private KafkaTopics() {}
}
