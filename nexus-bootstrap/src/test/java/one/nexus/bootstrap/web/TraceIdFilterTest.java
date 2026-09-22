package one.nexus.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.io.IOException;
import one.nexus.common.trace.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {
    @Test
    void echoesValidInboundTraceId() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIds.HEADER, "0199a1c0-0000-7000-8000-000000000099");
        var response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(request, response, new MockFilterChain());
        assertEquals("0199a1c0-0000-7000-8000-000000000099", response.getHeader(TraceIds.HEADER));
    }

    @Test
    void assignsTraceIdWhenMissing() throws ServletException, IOException {
        var response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        assertTrue(TraceIds.isValid(response.getHeader(TraceIds.HEADER)));
    }
}
