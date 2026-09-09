/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// See https://www.baeldung.com/spring-mvc-custom-validator

@Target({METHOD, FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = ResourceIdValidator.class)
@Documented
public @interface ResourceId {
    String message() default "{ResourceId.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
