package com.djorka.incidentops.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void usesVercelControlledHeaderInVercelEnvironment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("x-vercel-forwarded-for", "203.0.113.8");
        request.addHeader("x-forwarded-for", "198.51.100.2");

        assertThat(new ClientIpResolver("1").resolve(request)).contains("203.0.113.8");
    }

    @Test
    void usesRemoteAddressOutsideVercelAndRejectsForwardedLists() {
        MockHttpServletRequest localRequest = new MockHttpServletRequest();
        localRequest.setRemoteAddr("127.0.0.1");
        assertThat(new ClientIpResolver("").resolve(localRequest)).contains("127.0.0.1");

        MockHttpServletRequest vercelRequest = new MockHttpServletRequest();
        vercelRequest.addHeader("x-vercel-forwarded-for", "203.0.113.8, 198.51.100.2");
        assertThat(new ClientIpResolver("1").resolve(vercelRequest)).isEmpty();
    }
}
