package com.buildsmart.siteops.validator.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = WordCountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface WordCount {

    int min() default 20;
    int max() default 50;

    String message() default "Must be between {min} and {max} words";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

