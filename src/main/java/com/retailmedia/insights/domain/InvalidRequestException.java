package com.retailmedia.insights.domain;

/** The caller asked for something that can never succeed as written (mapped to HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
