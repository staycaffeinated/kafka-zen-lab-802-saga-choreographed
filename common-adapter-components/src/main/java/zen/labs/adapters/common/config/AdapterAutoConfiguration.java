package zen.labs.adapters.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import zen.labs.adapters.common.handlers.GlobalExceptionHandler;

@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class AdapterAutoConfiguration {}
