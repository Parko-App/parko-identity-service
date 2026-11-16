package com.parko.identity.service.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.parko.identity.service.controller.UserController;
import com.parko.identity.service.controller.VehicleController;
import com.parko.identity.service.exception.GlobalExceptionHandler;
import com.parko.identity.service.service.UserService;
import com.parko.identity.service.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, VehicleController.class})
@Import({IdentitySecurityConfig.class, GlobalExceptionHandler.class})
class IdentitySecurityConfigTest {

    private static final String VALID_USER_BODY = "{\"fullName\":\"a\",\"email\":\"a@b.c\",\"studentId\":\"1\","
            + "\"password\":\"x\",\"institutionalDomain\":null,\"termsAndConditionsAccepted\":true}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private VehicleService vehicleService;

    @BeforeEach
    void setUpToken() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid-1");
        when(firebaseAuth.verifyIdToken("good")).thenReturn(token);
    }

    @Test
    void vehicleEndpoint_withoutToken_returns401_andCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/vehicle/user/x"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void userEndpoint_withoutToken_returns401_andCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/user/x"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void unknownPath_withoutToken_returns401_insteadOfBeingPublic() throws Exception {
        mockMvc.perform(get("/api/v1/ruta/que/no/existe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCollectionPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registration_withoutToken_stillPublic() throws Exception {
        mockMvc.perform(post("/api/v1/user").contentType("application/json").content(VALID_USER_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void vehicleEndpoint_withValidToken_isAllowedAndCreatesNoSession() throws Exception {
        when(vehicleService.getVehiclesByUser("x", "uid-1")).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/v1/vehicle/user/x").header("Authorization", "Bearer good"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void userEndpoint_ownershipStillEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/user/other-uid").header("Authorization", "Bearer good"))
                .andExpect(status().isForbidden());
    }
}
