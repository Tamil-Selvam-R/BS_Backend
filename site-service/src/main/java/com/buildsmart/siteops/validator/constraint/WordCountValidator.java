package com.buildsmart.siteops.validator.constraint;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class WordCountValidator implements ConstraintValidator<WordCount, String> {

    private int min;
    private int max;

    @Override
    public void initialize(WordCount annotation) {
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // Let @NotBlank handle null/blank separately
            return false;
        }

        String[] words = value.trim().split("\\s+");
        int count = words.length;

        if (count < min || count > max) {
            // Build a clear custom message
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Must be between " + min + " and " + max + " words. You provided " + count + " word(s)."
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}

