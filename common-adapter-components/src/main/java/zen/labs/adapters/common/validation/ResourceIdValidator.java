/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import zen.labs.domain.common.ResourceIdFormat;

/**
 * This constraint verifies a String value is
 * consistent with the format of a resource identifier.
 * This constraint can be applied in POJOs or URL path variables
 * to help verify a field or parameter "looks like" a resource ID.
 */
@SuppressWarnings({"java:S125"})
// S125: We don't care if a comment happens to look like code
public class ResourceIdValidator implements ConstraintValidator<ResourceId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return isValid(value);
    }

    public boolean isValid(String value) {
        return value != null
                && value.length() == ResourceIdFormat.RESOURCE_ID_LENGTH
                && value.chars().allMatch(Character::isLetterOrDigit);
    }
}
