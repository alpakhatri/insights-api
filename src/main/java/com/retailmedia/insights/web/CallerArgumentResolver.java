package com.retailmedia.insights.web;

import com.retailmedia.insights.security.Caller;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controller methods declare a {@link Caller} parameter instead of parsing the token themselves. */
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object caller = webRequest.getAttribute(Caller.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (caller == null) {
            // Should not happen: the security chain authenticates every non-public endpoint first.
            throw new AuthenticationCredentialsNotFoundException("No authenticated caller");
        }
        return caller;
    }
}
