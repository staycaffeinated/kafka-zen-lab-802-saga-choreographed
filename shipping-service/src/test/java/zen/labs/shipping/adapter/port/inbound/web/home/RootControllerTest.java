/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.shipping.adapter.port.inbound.web.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import zen.labs.shipping.adapter.inbound.web.home.RootController;

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
