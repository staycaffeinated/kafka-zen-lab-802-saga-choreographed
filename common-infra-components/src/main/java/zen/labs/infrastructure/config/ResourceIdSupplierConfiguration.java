/*
 * Copyright 2026 [CopyrightOwner]
 */

package zen.labs.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import zen.labs.application.common.port.outbound.ResourceIdSupplier;
import zen.labs.infrastructure.utils.ResourceIdGenerator;

@AutoConfiguration
public class ResourceIdSupplierConfiguration {

    @Bean
    public ResourceIdSupplier resourceIdSupplier() {
        return new ResourceIdGenerator();
    }
}
