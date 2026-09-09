/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.adapter.inbound.web.home;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The default implementation of this controller returns Http:200 responses to GET requests.
 */
@RestController
@RequestMapping("/")
public class RootController {

    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> getHome() {
        return ResponseEntity.ok().build();
    }
}
