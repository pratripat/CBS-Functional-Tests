package com.banking.history.exception;

import com.banking.history.dto.TransactionDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<TransactionDtos.ErrorResponse> handleRuntime(RuntimeException ex) {
        // Map "Transaction not found" to 404 instead of 500
        if (ex.getMessage() != null && ex.getMessage().startsWith("Transaction not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TransactionDtos.ErrorResponse.builder()
                            .errorCode("TRANSACTION_NOT_FOUND")
                            .message(ex.getMessage())
                            .timestamp(LocalDateTime.now())
                            .build());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionDtos.ErrorResponse.builder()
                        .errorCode("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransactionDtos.ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionDtos.ErrorResponse.builder()
                        .errorCode("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
