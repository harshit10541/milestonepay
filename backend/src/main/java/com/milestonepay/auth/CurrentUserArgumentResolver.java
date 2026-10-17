package com.milestonepay.auth;

import com.milestonepay.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Fills {@link CurrentUser} controller parameters using whichever {@link CallerAuthenticator} is active. */
@Component
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectProvider<CallerAuthenticator> authenticator;

    CurrentUserArgumentResolver(ObjectProvider<CallerAuthenticator> authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CallerAuthenticator auth = authenticator.getIfAvailable();
        if (auth == null) {
            throw new UnauthorizedException("No auth method is configured yet (run with the dev profile)");
        }
        return new CurrentUser(auth.authenticate(webRequest.getNativeRequest(HttpServletRequest.class)));
    }
}
