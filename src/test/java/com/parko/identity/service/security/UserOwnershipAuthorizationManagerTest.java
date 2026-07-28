package com.parko.identity.service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserOwnershipAuthorizationManagerTest {

    private final UserOwnershipAuthorizationManager manager = new UserOwnershipAuthorizationManager();

    @Test
    void grants_whenAuthenticatedUidMatchesPathId() {
        AuthorizationDecision decision = manager.authorize(
                () -> authFor("firebase-uid-123"),
                contextForUri("/api/user/firebase-uid-123/balance"));

        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void denies_whenAuthenticatedUidDoesNotMatchPathId() {
        AuthorizationDecision decision = manager.authorize(
                () -> authFor("someone-else-uid"),
                contextForUri("/api/user/firebase-uid-123"));

        assertThat(decision.isGranted()).isFalse();
    }

    @Test
    void denies_whenNoAuthentication() {
        AuthorizationDecision decision = manager.authorize(
                () -> null,
                contextForUri("/api/user/firebase-uid-123"));

        assertThat(decision.isGranted()).isFalse();
    }

    private Authentication authFor(String firebaseUid) {
        return new UsernamePasswordAuthenticationToken(firebaseUid, null, List.of());
    }

    private RequestAuthorizationContext contextForUri(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return new RequestAuthorizationContext(request);
    }
}
