/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This is a constraint for alphabet fields.
 * Besides using this annotation, alphabetic constraints
 * can be defined with, say: @Pattern(regexp = "[a-zA-Z ]").
 * The interface is chosen simply to illustrate how to
 * implement a constraint with an interface.
 */
@Target({METHOD, FIELD, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = AlphabeticValidator.class)
@Documented
public @interface Alphabetic {
    String message() default "{Alphabetic.invalid}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
