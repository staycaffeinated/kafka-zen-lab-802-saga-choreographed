/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResourceIdValidatorTest {

    final ResourceIdValidator validationUnderTest = new ResourceIdValidator();

    @ParameterizedTest
    @CsvSource(
            value = {
                //  123456789.123456789.1234567
                "123456",
                "abcd-34843-abkkcuu4-kkuyzu4",
                "PZ4PD3xVOYNFYMJDOrumUCV44",
                "NeUDAQPAGP0WbqOB7OHfQTaMM1234",
                "abcd{34843-}ab#![]%kkuyf$1234"
            })
    void shouldDetectInvalidIds(String candidateId) {
        assertThat(validationUnderTest.isValid(candidateId, null)).isFalse();
    }

    @Test
    void shouldDetectEmptyStringAsInvalid() {
        assertThat(validationUnderTest.isValid("", null)).isFalse();
    }

    @Test
    void shouldDetectBlankStringAsInvalid() {
        assertThat(validationUnderTest.isValid("     ", null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                // 123456789.123456789.1234567
                "PZ4PD3xVOYNFYMJDOrumUCVDuvi",
                "TvKziJ2az5OyTxz40lasUAZpxyz",
                "bkVAQFIacs7ey0zHQewIJFBu125",
                "8qdOk3M2GHI5daFIanZjOJoNu22",
                "NeUDAQPAGP0WbqOB7OHfQTaMB45"
            })
    void shouldDetectValidIds(String candidateId) {
        assertThat(validationUnderTest.isValid(candidateId)).isTrue();
    }

    @Test
    void shouldTreatNullAsInvalid() {
        assertThat(validationUnderTest.isValid(null)).isFalse();
    }
}
