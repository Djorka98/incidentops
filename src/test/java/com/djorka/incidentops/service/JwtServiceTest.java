package com.djorka.incidentops.service;

import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private Jwt encodedJwt;
    @InjectMocks
    private JwtService jwtService;

    @Test
    void generatesTokenWithIdentityRoleAndExpirationClaims() {
        ReflectionTestUtils.setField(jwtService, "expirationSeconds", 3600L);
        User user = User.builder()
                .id(7L).name("Admin").email("admin@example.com").role(UserRole.ADMIN).build();
        when(jwtEncoder.encode(org.mockito.ArgumentMatchers.any())).thenReturn(encodedJwt);
        when(encodedJwt.getTokenValue()).thenReturn("signed-token");

        String token = jwtService.generateToken(user);

        assertThat(token).isEqualTo("signed-token");
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        var claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat((Long) claims.getClaim("userId")).isEqualTo(7L);
        assertThat((String) claims.getClaim("name")).isEqualTo("Admin");
        assertThat((String) claims.getClaim("role")).isEqualTo("ADMIN");
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plusSeconds(3600));
    }
}
