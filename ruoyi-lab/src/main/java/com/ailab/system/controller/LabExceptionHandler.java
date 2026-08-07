package com.ailab.system.controller;

import com.ailab.system.exception.LabValidationException;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts structured laboratory validation errors into the standard RuoYi envelope. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ailab.system.controller")
public class LabExceptionHandler {
    @ExceptionHandler(LabValidationException.class)
    public AjaxResult handleValidation(LabValidationException exception) {
        return AjaxResult.error(exception.getMessage()).put("fieldErrors", exception.getFieldErrors());
    }
}
