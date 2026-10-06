package org.krish.traffic.web;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.krish.traffic.rules.UnenforceableReadingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record FieldErrorDetail(String field, String message) {}

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrorMap = new TreeMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrorMap.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    List<FieldErrorDetail> errors =
        fieldErrorMap.entrySet().stream()
            .map(entry -> new FieldErrorDetail(entry.getKey(), entry.getValue()))
            .toList();
    return respond(HttpStatus.BAD_REQUEST, "Request validation failed", errors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleMalformedJson(HttpMessageNotReadableException ex) {
    return respond(HttpStatus.BAD_REQUEST, "Malformed JSON request", null);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    Class<?> required = ex.getRequiredType();
    String expected = required == null ? "value" : required.getSimpleName().toLowerCase();
    return respond(
        HttpStatus.BAD_REQUEST,
        "Request validation failed",
        List.of(new FieldErrorDetail(ex.getName(), "must be a valid " + expected)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    return respond(
        HttpStatus.BAD_REQUEST,
        "Request validation failed",
        List.of(new FieldErrorDetail("request", ex.getMessage())));
  }

  @ExceptionHandler(UnenforceableReadingException.class)
  public ResponseEntity<ProblemDetail> handleUnprocessable(UnenforceableReadingException ex) {
    return respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException ex) {
    return respond(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), null);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(NoResourceFoundException ex) {
    return respond(HttpStatus.NOT_FOUND, "No such page or endpoint", null);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleWrongMethod(HttpRequestMethodNotSupportedException ex) {
    return respond(
        HttpStatus.METHOD_NOT_ALLOWED, ex.getMethod() + " is not supported here", null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex) {
    // other framework 4xx errors (415, 406, missing parameter, ...) keep their own status
    if (ex instanceof ErrorResponse errorResponse) {
      HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
      ResponseEntity<ProblemDetail> body =
          respond(status, errorResponse.getBody().getDetail(), null);
      return ResponseEntity.status(status)
          .headers(errorResponse.getHeaders())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body.getBody());
    }
    log.error("Unexpected error", ex);
    return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
  }

  private static ResponseEntity<ProblemDetail> respond(
      HttpStatus status, String detail, List<FieldErrorDetail> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    if (errors != null) {
      problem.setProperty("errors", errors);
    }
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(problem);
  }
}
