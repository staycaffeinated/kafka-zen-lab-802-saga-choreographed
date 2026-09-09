/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchTextValidatorTest {

    SearchTextValidator validationUnderTest = new SearchTextValidator();

    @Mock
    ConstraintValidatorContext mockContext;

    @Nested
    class PositiveTestCases {
        @ParameterizedTest
        @ValueSource(strings = {"something", "Something", "SOMETHING", "A", "Abc", "abc"})
        @EmptySource
        void shouldAllowShortText(String candidateText) {
            assertThat(validationUnderTest.isValid(candidateText, mockContext)).isTrue();
        }
    }

    @Nested
    class NegativeTestCases {
        @ParameterizedTest
        @ValueSource(strings = {"supercalifragilisticexpialidocious"})
        void shouldNotAllowTextExceedingMaxLength(String candidateText) {
            assertThat(validationUnderTest.isValid(candidateText, mockContext)).isFalse();
        }

        @Test
        void shouldTreatNullAsValid() {
            assertThat(validationUnderTest.isValid(null, mockContext)).isTrue();
        }
    }
}
