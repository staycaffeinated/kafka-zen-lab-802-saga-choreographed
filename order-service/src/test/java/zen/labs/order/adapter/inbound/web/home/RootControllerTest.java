/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.order.adapter.inbound.web.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(RootController.class)
@ActiveProfiles("test")
class RootControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Nested
    class TestRootRoute {
        /*
         * Ensure the root controller handles GET requests
         */
        @Test
        void shouldGetRootPage() throws Exception {
            mockMvc.perform(get("/")).andExpect(status().isOk());
        }
    }
}
