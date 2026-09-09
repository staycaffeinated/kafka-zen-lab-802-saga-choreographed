/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.order.infrastructure.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zen.labs.order.domain.SagaDomainException;

@RestControllerAdvice
public class SagaExceptionHandler {

    @ExceptionHandler(SagaDomainException.class)
    public ResponseEntity<ProblemDetail> handleSagaDomainException(SagaDomainException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        pd.setTitle("Invalid saga state transition");
        pd.setDetail(ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(pd);
    }
}
