package com.n11bootcamp.order.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;


public abstract class BaseExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());


    protected ProblemDetail problemDetail(HttpStatus status, String detail, String typeKey) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:problem:" + typeKey));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }


    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        ProblemDetail pd = problemDetail(HttpStatus.BAD_REQUEST,
            "Zorunlu header eksik: " + ex.getHeaderName(), "missing-header");
        pd.setProperty("headerName", ex.getHeaderName());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                fe -> fe.getField(),
                fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                (first, second) -> first  // aynı alan için birden fazla hata → ilkini al
            ));

        ProblemDetail pd = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", "validation-error");
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }


    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        ProblemDetail pd = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            ex.getClass().getSimpleName() + ": " + ex.getMessage(), "internal-error");
        // TODO: production'da kaldır — sadece debug için
        pd.setProperty("exceptionClass", ex.getClass().getName());
        if (ex.getCause() != null) pd.setProperty("cause", ex.getCause().getMessage());
        return pd;
    }
}
