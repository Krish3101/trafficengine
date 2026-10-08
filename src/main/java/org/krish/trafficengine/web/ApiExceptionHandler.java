package org.krish.trafficengine.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  public record FieldErrorDetail(String field, String message) {}

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    List<FieldErrorDetail> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
            .toList();
    return problem(HttpStatus.BAD_REQUEST, "Request validation failed", errors);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Request validation failed",
        List.of(new FieldErrorDetail("request", ex.getMessage())));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleStatus(ResponseStatusException ex) {
    return problem(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), null);
  }

  private static ProblemDetail problem(
      HttpStatus status, String detail, List<FieldErrorDetail> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(status.getReasonPhrase());
    if (errors != null) {
      problem.setProperty("errors", errors);
    }
    return problem;
  }
}
