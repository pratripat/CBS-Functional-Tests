package com.banking.transfer.exception;

import com.banking.transfer.client.CbsClient;
import com.banking.transfer.dto.TransferDtos;
import com.banking.transfer.service.FundsTransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FundsTransferService.TransferFailedException.class)
    public ResponseEntity<TransferDtos.ErrorResponse> handleTransferFailed(
            FundsTransferService.TransferFailedException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case "INSUFFICIENT_FUNDS", "ACCOUNT_DORMANT" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "INVALID_ACCOUNT" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(TransferDtos.ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @ExceptionHandler(CbsClient.CbsTimeoutException.class)
    public ResponseEntity<TransferDtos.ErrorResponse> handleCbsTimeout(
            CbsClient.CbsTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(TransferDtos.ErrorResponse.builder()
                        .errorCode("CBS_TIMEOUT")
                        .message(ex.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TransferDtos.ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(TransferDtos.ErrorResponse.builder()
                        .errorCode("VALIDATION_ERROR")
                        .message(message)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransferDtos.ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransferDtos.ErrorResponse.builder()
                        .errorCode("INTERNAL_ERROR")
                        .message("An unexpected error occurred")
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
