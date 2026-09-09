/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.ObjectUtils;

/**
 * Verifies that a search-by-text parameter only contains valid characters.
 * The OWASP Validation Regex Repository has useful expressions:
 * <ul>
 * <li><a href="https://owasp.org/www-community/OWASP_Validation_Regex_Repository">OWASP Regex Repository</a></li>
 * </ul>
 */
public class SearchTextValidator implements ConstraintValidator<SearchText, String> {

    private static final int MAXLENGTH = 24;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (ObjectUtils.isEmpty(value)) return true;
        return value.length() <= MAXLENGTH;
    }
}
