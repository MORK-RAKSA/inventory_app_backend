package com.retailflow.globalExceptional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  public record ApiError(
      String error, String code, int status, String path, OffsetDateTime timestamp) {}

  private ResponseEntity<ApiError> make(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                message, code, status.value(), request.getRequestURI(), OffsetDateTime.now()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> bad(IllegalArgumentException e, HttpServletRequest request) {
    return make(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalid(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("Invalid request fields");
    return make(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> mismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    return make(
        HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid parameter: " + e.getName(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> conflict(
      DataIntegrityViolationException e, HttpServletRequest request) {
    return make(
        HttpStatus.CONFLICT, "DATA_CONFLICT", "A conflicting record already exists", request);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest request) {
    return make(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> conflict(ConflictException e, HttpServletRequest request) {
    return make(HttpStatus.CONFLICT, "CONFLICT", e.getMessage(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> stale(
      ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
    return make(
        HttpStatus.CONFLICT,
        "STALE_DATA",
        "This item was changed by someone else. Refresh and try again.",
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    return make(
        HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed or unreadable request body", request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> tooLarge(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    return make(
        HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Image must be 5 MB or smaller", request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
    return make(
        status,
        status.name(),
        e.getReason() == null ? status.getReasonPhrase() : e.getReason(),
        request);
  }
}
