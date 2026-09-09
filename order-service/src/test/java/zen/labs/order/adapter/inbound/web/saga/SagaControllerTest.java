/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.adapter.inbound.web.saga;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.adapters.common.handlers.GlobalExceptionHandler;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.order.application.port.inbound.FindSagaUseCase;
import zen.labs.order.domain.SagaState;
import zen.labs.order.domain.SagaStatus;
import zen.labs.order.domain.SagaStep;

@WebMvcTest(SagaController.class)
@Import({GlobalExceptionHandler.class, SagaControllerTest.TestConfig.class})
@ActiveProfiles("test")
@DisplayName("SagaController")
class SagaControllerTest {

    private static final String BASE_URL = "/api/v1/sagas";

    static class TestConfig {
        @Bean
        JsonMapper jsonMapper() {
            return new JsonMapper();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindSagaUseCase findSagaUseCase;

    @Nested
    @DisplayName("GET /api/v1/sagas/{sagaId}")
    class GetSaga {

        @Test
        @DisplayName("should return 200 with saga state when saga is found")
        void shouldReturn200WithSagaStateWhenFound() throws Exception {
            // Given
            String sagaId = "saga-abc-123";
            SagaState saga = SagaState.builder()
                    .sagaId(sagaId)
                    .orderId("order-001")
                    .status(SagaStatus.COMPLETED)
                    .currentStep(SagaStep.DONE)
                    .startedAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .updatedAt(Instant.parse("2026-01-01T00:01:00Z"))
                    .build();

            when(findSagaUseCase.findSaga(sagaId)).thenReturn(saga);

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{sagaId}", sagaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sagaId").value(sagaId))
                    .andExpect(jsonPath("$.orderId").value("order-001"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.currentStep").value("DONE"))
                    .andExpect(jsonPath("$.startedAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        @DisplayName("should return 404 when saga is not found")
        void shouldReturn404WhenSagaNotFound() throws Exception {
            // Given
            String sagaId = "saga-nonexistent-999";
            when(findSagaUseCase.findSaga(sagaId))
                    .thenThrow(new ResourceNotFoundException("Saga not found: " + sagaId));

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{sagaId}", sagaId)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return all saga status values correctly serialized")
        void shouldReturnSagaInFailedState() throws Exception {
            // Given
            String sagaId = "saga-failed-001";
            SagaState saga = SagaState.builder()
                    .sagaId(sagaId)
                    .orderId("order-002")
                    .status(SagaStatus.FAILED)
                    .currentStep(SagaStep.DONE)
                    .startedAt(Instant.parse("2026-02-01T10:00:00Z"))
                    .updatedAt(Instant.parse("2026-02-01T10:00:05Z"))
                    .build();

            when(findSagaUseCase.findSaga(sagaId)).thenReturn(saga);

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{sagaId}", sagaId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.currentStep").value("DONE"));
        }
    }
}
