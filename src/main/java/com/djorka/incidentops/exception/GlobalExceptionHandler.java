package com.djorka.incidentops.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleNotFound(
                        ResourceNotFoundException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.NOT_FOUND.value());
                error.put("error", "Not Found");
                error.put("message", exception.getMessage());
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(
                        MethodArgumentNotValidException exception,
                        HttpServletRequest request) {

                Map<String, String> validationErrors = new HashMap<>();

                exception.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> validationErrors.put(
                                                error.getField(),
                                                error.getDefaultMessage()));

                Map<String, Object> response = new HashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("status", HttpStatus.BAD_REQUEST.value());
                response.put("error", "Validation failed");
                response.put("fields", validationErrors);
                response.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(response);
        }

        @ExceptionHandler(InvalidStatusTransitionException.class)
        public ResponseEntity<Map<String, Object>> handleInvalidStatusTransition(
                        InvalidStatusTransitionException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.BAD_REQUEST.value());
                error.put("error", "Invalid status transition");
                error.put("message", exception.getMessage());
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(error);
        }

        @ExceptionHandler(ResourceConflictException.class)
        public ResponseEntity<Map<String, Object>> handleConflict(
                        ResourceConflictException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.CONFLICT.value());
                error.put("error", "Conflict");
                error.put("message", exception.getMessage());
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(error);
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Map<String, Object>> handleDataConflict(
                        DataIntegrityViolationException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.CONFLICT.value());
                error.put("error", "Conflict");
                error.put("message", "The request conflicts with existing data.");
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(error);
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleAuthentication(
                        AuthenticationException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.UNAUTHORIZED.value());
                error.put("error", "Unauthorized");
                error.put("message", "Authentication failed.");
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(error);
        }

        @ExceptionHandler({
                        ConstraintViolationException.class,
                        IllegalArgumentException.class
        })
        public ResponseEntity<Map<String, Object>> handleInvalidArgument(
                        RuntimeException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.BAD_REQUEST.value());
                error.put("error", "Invalid request");
                error.put("message", "One or more request parameters are invalid.");
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(error);
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<Map<String, Object>> handleMissingParameter(
                        MissingServletRequestParameterException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.BAD_REQUEST.value());
                error.put("error", "Invalid parameter");
                error.put("message", "Required parameter is missing: " + exception.getParameterName());
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(error);
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Map<String, Object>> handleTypeMismatch(
                        MethodArgumentTypeMismatchException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.BAD_REQUEST.value());
                error.put("error", "Invalid parameter");
                error.put(
                                "message",
                                "Invalid value for parameter: " + exception.getName());
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(error);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleInvalidJson(
                        HttpMessageNotReadableException exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.BAD_REQUEST.value());
                error.put("error", "Invalid request body");
                error.put("message", "The request body is invalid or malformed.");
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(error);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleGenericException(
                        Exception exception,
                        HttpServletRequest request) {

                Map<String, Object> error = new HashMap<>();

                error.put("timestamp", LocalDateTime.now());
                error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
                error.put("error", "Internal Server Error");
                error.put("message", "An unexpected error occurred.");
                error.put("path", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(error);
        }
}
