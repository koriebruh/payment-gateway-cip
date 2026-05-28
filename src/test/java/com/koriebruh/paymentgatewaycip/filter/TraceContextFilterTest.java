package com.koriebruh.paymentgatewaycip.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TraceContextFilterTest {

    private TraceContextFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TraceContextFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void doFilterInternal_ShouldUseExistingTraceId() throws ServletException, IOException {
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-123");
        
        filter.doFilterInternal(request, response, filterChain);
        
        // Cannot assert MDC within the filter chain execution easily without a custom Answer,
        // but we can verify it clears MDC after.
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("X-Trace-Id", "trace-123");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void doFilterInternal_ShouldGenerateNewTraceId() throws ServletException, IOException {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        assertThat(MDC.get("traceId")).isNull();
    }
}
