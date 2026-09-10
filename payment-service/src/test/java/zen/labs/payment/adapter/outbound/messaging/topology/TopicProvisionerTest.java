package zen.labs.payment.adapter.outbound.messaging.topology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.kafka.core.KafkaAdmin;

class TopicProvisionerTest {

    private KafkaAdmin kafkaAdmin;
    private AdminClient adminClient;
    private KafkaFuture<Void> kafkaFuture;
    private MockedStatic<AdminClient> mockedAdminClient;

    private TopicProvisioner topicProvisioner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaAdmin = mock(KafkaAdmin.class);
        adminClient = mock(AdminClient.class);
        CreateTopicsResult createTopicsResult = mock(CreateTopicsResult.class);
        kafkaFuture = mock(KafkaFuture.class);

        when(kafkaAdmin.getConfigurationProperties()).thenReturn(Map.of());
        when(adminClient.createTopics(any())).thenReturn(createTopicsResult);
        when(createTopicsResult.all()).thenReturn(kafkaFuture);

        mockedAdminClient = mockStatic(AdminClient.class);
        mockedAdminClient.when(() -> AdminClient.create(any(Map.class))).thenReturn(adminClient);

        topicProvisioner = new TopicProvisioner(
                kafkaAdmin,
                "saga.payment.events",
                "saga.inventory.events",
                "saga.shipping.events",
                "saga.order.events");
    }

    @AfterEach
    void tearDown() {
        mockedAdminClient.close();
        Thread.interrupted(); // clear any interrupt flag set during tests
    }

    @Nested
    class WhenCreateTopicsSucceeds {

        @Test
        void givenAllTopicsCreated_thenCompletesWithoutException() throws Exception {
            when(kafkaFuture.get(30, TimeUnit.SECONDS)).thenReturn(null);

            topicProvisioner.createTopics();

            verify(adminClient).createTopics(any());
        }
    }

    @Nested
    class WhenCreateTopicsFails {
        @Test
        void givenExecutionExceptionWithTopicExistsCause_thenLogsInfoAndDoesNotThrow() throws Exception {
            ExecutionException ex = new ExecutionException(new TopicExistsException("topic already exists"));
            when(kafkaFuture.get(30, TimeUnit.SECONDS)).thenThrow(ex);

            topicProvisioner.createTopics();

            verify(adminClient).createTopics(any());
        }

        @Test
        void givenExecutionExceptionWithNonTopicExistsCause_thenLogsErrorAndDoesNotThrow() throws Exception {
            ExecutionException ex = new ExecutionException(new RuntimeException("broker unavailable"));
            when(kafkaFuture.get(30, TimeUnit.SECONDS)).thenThrow(ex);

            topicProvisioner.createTopics();

            verify(adminClient).createTopics(any());
        }

        @Test
        void givenInterruptedException_thenSetsInterruptFlagAndDoesNotThrow() throws Exception {
            when(kafkaFuture.get(30, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));

            topicProvisioner.createTopics();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }

        @Test
        void givenTimeoutException_thenLogsErrorAndDoesNotThrow() throws Exception {
            when(kafkaFuture.get(30, TimeUnit.SECONDS)).thenThrow(new TimeoutException("timed out"));

            topicProvisioner.createTopics();

            verify(adminClient).createTopics(any());
        }
    }
}
