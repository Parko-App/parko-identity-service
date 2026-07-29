package com.parko.identity.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

public class UserOwnershipAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String PREFIX = "/api/user/";

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        String firebaseUid = extractFirebaseUid(context.getRequest());
        boolean granted = auth != null && auth.isAuthenticated()
                && firebaseUid != null && firebaseUid.equals(auth.getName());
        return new AuthorizationDecision(granted);
    }

    private String extractFirebaseUid(HttpServletRequest request) {
        String path = request.getRequestURI();
        int start = path.indexOf(PREFIX);
        if (start < 0) {
            return null;
        }
        String remainder = path.substring(start + PREFIX.length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(0, slash);
    }
}
