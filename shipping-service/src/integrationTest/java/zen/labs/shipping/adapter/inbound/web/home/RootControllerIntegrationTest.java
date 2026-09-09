/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.adapter.inbound.web.home;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import zen.labs.shipping.persistence.AbstractPostgresIntegrationTest;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
// toggle this according to what makes sense in your project
@TestPropertySource(
        properties = {"spring.kafka.streams.auto-startup=false", "spring.kafka.listener.auto-startup=false"})
@Testcontainers
class RootControllerIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void testGetHome() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }
}
