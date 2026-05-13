package com.buildsmart.projectmanager.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDate;

public class InvalidDateRangeException extends BuildSmartException {

    private static final String ERROR_CODE = "ERR_INVALID_DATES";

    public InvalidDateRangeException(LocalDate startDate, LocalDate endDate) {
        super(
            ERROR_CODE,
            "Invalid date range: End date must be after start date.",
            String.format("Start Date: %s, End Date: %s. The end date cannot be before or equal to the start date.", startDate, endDate),
            HttpStatus.BAD_REQUEST
        );
    }
}
