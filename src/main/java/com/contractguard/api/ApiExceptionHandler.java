package com.contractguard.api;

import com.contractguard.consumeranalysis.InvalidSourceBundleException;
import com.contractguard.history.AnalysisFailedException;
import com.contractguard.schema.InvalidAvroSchemaException;
import com.contractguard.shared.ConflictException;
import com.contractguard.shared.NotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps application exceptions to RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    @ExceptionHandler(InvalidAvroSchemaException.class)
    public ProblemDetail handleInvalidSchema(InvalidAvroSchemaException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Avro schema", e.getMessage());
    }

    @ExceptionHandler(InvalidSourceBundleException.class)
    public ProblemDetail handleInvalidSourceBundle(InvalidSourceBundleException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid consumer source upload", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "The request body is invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid path parameter",
                "'" + e.getName() + "' is not a valid " + e.getRequiredType().getSimpleName());
    }

    /** The run itself was persisted as FAILED; the id lets the client inspect it. */
    @ExceptionHandler(AnalysisFailedException.class)
    public ProblemDetail handleAnalysisFailed(AnalysisFailedException e) {
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis failed", e.getMessage());
        problem.setProperty("analysisId", e.getAnalysisId().toString());
        return problem;
    }

    /** Backstop for the unique constraints, which also guard against concurrent inserts. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
