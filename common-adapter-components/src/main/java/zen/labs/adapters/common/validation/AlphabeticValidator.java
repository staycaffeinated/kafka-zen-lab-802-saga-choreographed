/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Verifies a value only contains alphabetic characters
 */
public class AlphabeticValidator implements ConstraintValidator<Alphabetic, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && value.chars().allMatch(Character::isAlphabetic);
    }
}
