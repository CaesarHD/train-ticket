package com.example.trainticket.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        List<String> errors,
        LocalDateTime timestamp
) {
    public ErrorResponse(int status, List<String> errors) {
        this(status, errors, LocalDateTime.now());
    }
}
