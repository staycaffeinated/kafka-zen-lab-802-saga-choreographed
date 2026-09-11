package zen.labs.adapters.common.handlers;

import jakarta.validation.ConstraintViolation;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import zen.labs.application.common.exceptions.BadRequestException;
import zen.labs.application.common.exceptions.ResourceNotFoundException;
import zen.labs.application.common.exceptions.UnprocessableRequestException;

/**
 * Handles turning exceptions into RFC-7807 problem/json responses,
 * so instead of an exception and its stack trace leaking back
 * to the client, an RFC-7807 problem description is returned.
 */
@SuppressWarnings("java:1102")
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final JsonMapper jsonMapper;

    public GlobalExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @ExceptionHandler(UnprocessableRequestException.class)
    public ResponseEntity<ProblemDetail> handleUnprocessableRequestException(UnprocessableRequestException exception) {
        return problemDescription("The request cannot be processed", exception);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception) {
        return problemDescription("The request contains invalid data", exception);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException(DataAccessException exception) {
        log.error("Data access error: {}", exception.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        pd.setTitle("Service temporarily unavailable");
        pd.setDetail("A database error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            jakarta.validation.ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        ArrayNode jsonArray = jsonMapper.createArrayNode();

        for (final var constraint : constraintViolations) {
            ObjectNode objectNode = jsonMapper.createObjectNode();

            String className = constraint.getLeafBean().getClass().getName();
            String message = constraint.getMessage();
            String fullPath = constraint.getPropertyPath().toString();
            int dotIndex = fullPath.indexOf('.');
            String propertyPath = dotIndex >= 0 ? fullPath.substring(0, dotIndex) : fullPath;
            Object invalidValue = constraint.getInvalidValue();

            log.error(
                    "Constraint violation: {} on property {} with value {} in class {}",
                    message,
                    propertyPath,
                    invalidValue,
                    className);

            objectNode.put("reason", message);
            // Since its common for REST parameters to be Optional, we unwrap the Optional for a cleaner message
            if (invalidValue != null) {
                objectNode.put("invalid value", invalidValue.toString());
            }
            objectNode.put("method", propertyPath);

            jsonArray.add(objectNode);
        }
        problemDetail.setProperty("errors", jsonArray);
        return ResponseEntity.of(problemDetail).build();
    }

    /**
     * Handle Exception, handle generic Exception.class
     *
     * @param ex the Exception
     * @return a ResponseEntity with a body containing the problem description
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = "Invalid parameter in the request";

        // If the value and class are provided, write a detailed message
        Object value = ex.getValue();
        Class<?> klass = ex.getRequiredType();
        if (value != null && klass != null)
            message = String.format(
                    "The parameter '%s' with value '%s' could not be converted to type '%s'",
                    ex.getName(), value, klass.getSimpleName());

        return problemDescription(message, ex);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ProblemDetail> handleTransactionSystemException(TransactionSystemException ex) {
        log.error(ex.getMessage());
        return problemDescription("The request could not be persisted", ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatusCode());
        pd.setDetail(ex.getReason());
        pd.setTitle(ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(pd);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setDetail(ex.getMessage());
        pd.setTitle("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequestException(BadRequestException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail(ex.getMessage());
        pd.setTitle("Bad request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    /**
     * Catch anything that falls through
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleUncaughtException(RuntimeException ex) {
        return problemDescription(ex.getMessage(), ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle MissingServletRequestParameterException. Triggered when a 'required' request parameter is missing.
     * The parent class implements a protected version of this method.
     * @param ex MissingServletRequestParameterException
     * @return the ApiError object
     */
    @Override
    public ResponseEntity<@NonNull Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String title = String.format("Missing request parameter '%s'", ex.getParameterName());
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(ex.getMessage());
        pd.setTitle(title);
        return ResponseEntity.status(status).body(pd);
    }

    /**
     * Build a Problem/JSON description with HttpStatus: 422 (unprocessable entity)
     *
     * @param throwable the exception received by the handler
     * @return a ResponseEntity with a body containing the problem description
     */
    private ResponseEntity<ProblemDetail> problemDescription(String title, Throwable throwable) {
        return problemDescription(title, throwable, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Build a Problem/JSON description with the given http status
     *
     * @param throwable the exception received by the handler
     * @return a ResponseEntity with a body containing the problem descriptionn
     */
    private ResponseEntity<ProblemDetail> problemDescription(String title, Throwable throwable, HttpStatus status) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(throwable.getMessage());
        pd.setTitle(title);
        return ResponseEntity.status(status).body(pd);
    }
}
