/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.payment.adapter.inbound.web.home;

import static org.mockito.BDDMockito.given;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.payment.persistence.AbstractPostgresIntegrationTest;

/**
 * Integration tests of the exception handling of the root controller
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
// toggle this according to what makes sense in your project
@TestPropertySource(
        properties = {"spring.kafka.streams.auto-startup=false", "spring.kafka.listener.auto-startup=false"})
@Testcontainers
class RootControllerExceptionHandlerIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    private RootController controllerUnderTest;

    @Nested
    class ExceptionTests {

        @Test
        void shouldNotReturnStackTrace() throws Exception {
            // when/then
            mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.stackTrace").doesNotExist()) // sometimes traces come back like this
                    .andExpect(jsonPath("$.trace").doesNotExist()) // sometimes traces come back like this
                    .andDo((print()))
                    .andReturn();
        }

        /**
         * This exercises the GlobalExceptionHandler by simulating an HttpRequestMethodNotSupportedException.
         * If properly configured, the ProblemHandling method that handles this exception should catch it
         * and configure a proper problem/json response.  The point of this test is not to validate ProblemHandling,
         * but to ensure these kinds of exceptions return a problem/json response.
         */
        @Test
        void onHttpRequestMethodNotSupported() throws Exception {
            // For this test, let's say http GET is not allowed...
            // Note: Mockito won't allow its <code>thenThrows</code> method to throw this particular exception.
            // When attempted, Mockito gives the error "Checked exception is invalid for this method".
            // The work-around is to use the <code>willAnswer</code> method.
            given(controllerUnderTest.getHome()).willAnswer(invocation -> {
                throw new HttpRequestMethodNotSupportedException("GET");
            });

            // when/then
            mockMvc.perform(get("/").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is(HttpStatus.METHOD_NOT_ALLOWED.value()))
                    .andDo(print())
                    .andReturn();
        }
    }
}
