package com.buildsmart.finance.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {

    private String errorCode;
    private String message;
    private String fieldName;
    private List<String> errors;
    private LocalDateTime timestamp;
    private String path;
    private int status;
}
