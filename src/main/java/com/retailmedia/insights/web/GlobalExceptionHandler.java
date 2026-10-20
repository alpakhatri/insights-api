package com.retailmedia.insights.web;

import com.retailmedia.insights.domain.CampaignNotFoundException;
import com.retailmedia.insights.domain.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 7807 problem responses ({@code application/problem+json}).
 *
 * <p>The parent class already handles Spring MVC errors such as a malformed date or a failed
 * {@code @Pattern} check (both 400). Internal details are logged, never returned to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CampaignNotFoundException.class)
    public ProblemDetail campaignNotFound(CampaignNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Campaign not found", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail invalidRequest(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail dataStoreUnavailable(DataAccessException e) {
        log.error("Metrics store query failed", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Insights temporarily unavailable",
                "The insights store did not respond. Please retry shortly.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
