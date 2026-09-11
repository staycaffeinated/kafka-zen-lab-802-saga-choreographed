/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.adapters.common.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.*;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import zen.labs.application.common.exceptions.BadRequestException;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;

/**
 * Unit tests of GlobalExceptionHandler
 *
 * Borrowed ideas from:
 *  <a href="https://github.com/spring-projects/spring-framework/blob/master/spring-webmvc/src/test/java/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandlerTests.java">...</a>
 *
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandlerUnderTest = new GlobalExceptionHandler(new JsonMapper());

    /**
     * Test the condition the server raised a DataIntegrityViolation
     */
    @Test
    void onDataIntegrityViolationException_shouldReturnBadRequest() {
        Set<ConstraintViolation<String>> violations = new HashSet<>();
        ConstraintViolationException cause = new ConstraintViolationException(violations);
        DataIntegrityViolationException ex = new DataIntegrityViolationException("my data integrity violation", cause);

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleDataIntegrityViolationException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(response.getBody().getTitle()).isNotEmpty();
    }

    @Test
    void onDataAccessException_shouldReturn503WithoutLeakingDbDetails() {
        String rawDbMessage = "ERROR: relation \"order_service.idempotency_key\" does not exist; SQL [select ...]";
        DataAccessException ex = new InvalidDataAccessResourceUsageException(rawDbMessage);

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleDataAccessException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getBody().getTitle()).isNotBlank();
        assertThat(response.getBody().getDetail()).doesNotContain(rawDbMessage);
    }

    /**
     * Test the condition the server raised a MethodArgumentTypeMismatchException
     */
    @Test
    void onMethodArgumentTypeMismatchException_shouldReturnBadRequest() {
        WebRequest webRequest = mock(WebRequest.class);
        Class<?> stubResponse = String.class;
        MethodParameter parameter = mock(MethodParameter.class);

        // getParameterType() returns Class<?>, so the syntax shown is needed.
        // See https://stackoverflow.com/questions/16890133/cant-return-class-object-with-mockito
        Mockito.<Class<?>>when(parameter.getParameterType()).thenReturn(stubResponse);
        when(parameter.getParameterName()).thenReturn("firstName");

        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("parameterName");
        when(ex.getMessage()).thenReturn("argument type mismatch");

        // See the note above about why we use this syntax on this line
        Mockito.<Class<?>>when(ex.getRequiredType()).thenReturn(stubResponse);
        when(ex.getValue()).thenReturn("-badValue-");
        when(ex.getParameter()).thenReturn(parameter);

        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleMethodArgumentTypeMismatch(ex, webRequest);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void onTransactionSystemException_shouldReturnUnprocessableContent() {
        TransactionSystemException ex = new TransactionSystemException("constraint violation on commit");

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleTransactionSystemException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT.value());
        assertThat(response.getBody().getTitle()).isNotBlank();
    }

    @Test
    void onResponseStatusException_shouldReturnStatusFromException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleResponseStatusException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void onBadRequestException_shouldReturnBadRequest() {
        BadRequestException ex = new BadRequestException("some bad request");
        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleBadRequestException(ex);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onResourceNotFoundException_shouldReturnNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("some resource not found");
        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleResourceNotFoundException(ex);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Test our catch-all handler
     */
    @Test
    void onRuntimeException_shouldReturnServerError() {
        RuntimeException exception = new RuntimeException("I am a fake exception");

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleUncaughtException(exception);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void whenUnprocessableEntityException_expectBadRequest() {
        var ex = Mockito.mock(UnprocessableRequestException.class);
        when(ex.getMessage()).thenReturn("A mock message");

        ResponseEntity<ProblemDetail> response = exceptionHandlerUnderTest.handleUnprocessableRequestException(ex);
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().getTitle()).isNotBlank();
    }

    @Test
    void whenMissingServletRequestParameter_expectBadRequest() {
        var ex = Mockito.mock(MissingServletRequestParameterException.class);
        when(ex.getMessage()).thenReturn("Mock message");
        HttpStatus status = HttpStatus.BAD_REQUEST;
        HttpHeaders headers = new HttpHeaders();
        WebRequest webRequest = Mockito.mock(WebRequest.class);

        ResponseEntity<Object> response =
                exceptionHandlerUnderTest.handleMissingServletRequestParameter(ex, headers, status, webRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void whenMethodArgumentTypeMismatch_expectBadRequest() {
        var ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("Mock message");
        when(ex.getValue()).thenReturn("STRING");
        when(ex.getRequiredType()).thenAnswer(it -> Integer.class);

        var webRequest = Mockito.mock(WebRequest.class);

        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleMethodArgumentTypeMismatch(ex, webRequest);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void whenMethodArgumentTypeMismatchAndValueIsNull_expectBadRequest() {
        var ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("Mock message");
        when(ex.getValue()).thenReturn(null);
        when(ex.getRequiredType()).thenAnswer(it -> Integer.class);

        var webRequest = Mockito.mock(WebRequest.class);

        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleMethodArgumentTypeMismatch(ex, webRequest);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void whenMethodArgumentTypeMismatchAndRequiredTypeIsNull_expectBadRequest() {
        var ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("Mock message");
        when(ex.getValue()).thenReturn("FirstName");
        when(ex.getRequiredType()).thenAnswer(it -> null);

        var webRequest = Mockito.mock(WebRequest.class);

        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleMethodArgumentTypeMismatch(ex, webRequest);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ParameterizedTest
    @MethodSource("provideSampleObject")
    void whenConstraintViolationException_expectProblemDetail(SampleObject sampleObject) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        ConstraintViolationException exception = getConstraintViolationException(factory, sampleObject);

        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleConstraintViolationException(exception);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail problemDetail = response.getBody();
        assertThat(problemDetail).isNotNull();
    }

    /**
     * dotIndex < 0: field-level constraint produces a path with no dot (e.g. "name"),
     * so the handler uses the full path as-is.
     */
    @Test
    void whenConstraintViolationPathHasNoDot_expectFullPathUsed() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        jakarta.validation.Validator validator = factory.getValidator();

        Set<ConstraintViolation<SingleFieldBean>> violations = validator.validate(new SingleFieldBean(null));
        assertThat(violations).hasSize(1);

        String fullPath = violations.iterator().next().getPropertyPath().toString();
        assertThat(fullPath).doesNotContain(".");

        ConstraintViolationException exception = new ConstraintViolationException(violations);
        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleConstraintViolationException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ArrayNode errors = (ArrayNode) response.getBody().getProperties().get("errors");
        assertThat(errors).hasSize(1);
        // handler stores propertyPath as "method" — must equal the unchanged full path
        assertThat(errors.get(0).get("method").asString()).isEqualTo(fullPath);
    }

    /**
     * dotIndex >= 0: method-parameter constraint produces a path like "process.name", ]
     * so the handler truncates at the dot and stores only "process"
     */
    @Test
    void whenConstraintViolationPathHasDot_expectPathTruncatedBeforeDot() throws NoSuchMethodException {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        ExecutableValidator executableValidator = factory.getValidator().forExecutables();

        // validateParameters with null triggers @NotNull; Hibernate Validator builds a path
        // of the form "process.name" (or "process.arg0" without -parameters flag) — either
        // way the dot is present, which is the condition under test.
        ConstrainedParamService service = new ConstrainedParamService();
        Method method = ConstrainedParamService.class.getMethod("process", String.class);
        Set<ConstraintViolation<ConstrainedParamService>> violations =
                executableValidator.validateParameters(service, method, new Object[] {null});

        assertThat(violations).hasSize(1);
        String fullPath = violations.iterator().next().getPropertyPath().toString();
        assertThat(fullPath).contains(".");

        ConstraintViolationException exception = new ConstraintViolationException(violations);
        ResponseEntity<ProblemDetail> response =
                exceptionHandlerUnderTest.handleConstraintViolationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).isNotNull();
        ArrayNode errors = (ArrayNode) response.getBody().getProperties().get("errors");
        assertThat(errors).hasSize(1);
        // handler must store only the part before the first dot
        String expectedTruncated = fullPath.substring(0, fullPath.indexOf('.'));
        assertThat(errors.get(0).get("method").asString()).isEqualTo(expectedTruncated);
    }

    /* =======================================================================================
     * HELPER METHODS
     * ======================================================================================= */

    static Stream<Arguments> provideSampleObject() {
        // example of bad data
        SampleObject sample1 = new SampleObject("abc123", 12, false, "1234568", -1);
        // example of null data
        SampleObject sample2 = new SampleObject(null, 12, false, null, -1);

        return Stream.of(Arguments.of(sample1), Arguments.of(sample2));
    }

    private static ConstraintViolationException getConstraintViolationException(
            ValidatorFactory factory, SampleObject ts) {
        jakarta.validation.Validator validator = factory.getValidator();
        // Invoke validate() to trigger the validation
        Set<ConstraintViolation<SampleObject>> violations = validator.validate(ts);
        // Create an exception that wraps the constraint violations
        return new ConstraintViolationException(violations);
    }

    public static class SingleFieldBean {
        @NotNull(message = "name must not be null")
        final String name;

        SingleFieldBean(String name) {
            this.name = name;
        }
    }

    public static class ConstrainedParamService {
        public void process(@NotNull(message = "name must not be null") String name) {
            // no-op method
        }
    }

    /**
     * A sample class that contains validation annotations.
     * Instances of this class can be used to verify how validation errors are handled
     * and how the ProblemDetail is populated.
     * <p>
     * This is borrowed from:
     * https://www.geeksforgeeks.org/spring-boot-data-and-field-validation-using-jakarta-validation-constraints/
     */
    public static class SampleObject {
        public interface AllLevels {}

        public interface Junior {}

        public interface MidSenior {}

        public interface Senior {}

        @Size(min = 5, max = 20, message = "Name must be between 5 and 20 characters long")
        @Pattern(regexp = "[0-9]", message = "Name must not contain numbers")
        @NotBlank(message = "Name is a mandatory field")
        final String name;

        @Min(value = 5, groups = Junior.class, message = "Junior level requires at least 5 years of experience")
        @Min(
                value = 10,
                groups = MidSenior.class,
                message = "Mid-Senior level requires at least 10 years of experience")
        @Min(value = 15, groups = Senior.class, message = "Senior level requires at least 15 years of experience")
        final int exp;

        @AssertTrue(message = "You are not an admin")
        final boolean isAdmin;

        @Pattern(regexp = "\\d{10}", message = "Mobile number must have exactly 10 digits")
        final String mobilNumber;

        final Optional<@Min(value = 0, message = "Weight must be at least 0") Long> weight;

        public SampleObject(String name, int exp, boolean isAdmin, String mobilNumber, long weight) {
            this.name = name;
            this.exp = exp;
            this.isAdmin = isAdmin;
            this.mobilNumber = mobilNumber;
            this.weight = Optional.of(weight);
        }
    }
}
