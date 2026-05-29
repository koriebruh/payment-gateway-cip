package com.koriebruh.paymentgatewaycip.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koriebruh.paymentgatewaycip.dto.ApiResponseFactory;
import com.koriebruh.paymentgatewaycip.dto.PaymentRequest;
import com.koriebruh.paymentgatewaycip.dto.PaymentResponse;
import com.koriebruh.paymentgatewaycip.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass Spring Security for unit tests
@Import(ApiResponseFactory.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private io.micrometer.observation.ObservationRegistry observationRegistry;

    @Test
    void processPayment_WhenSuccessful_ShouldReturn201() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "SUCCESS", "CB-1", "BL-1", "Success");

        when(paymentService.processPayment(any(PaymentRequest.class), eq("IDEMP-1"))).thenReturn(response);

        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "IDEMP-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.order_id").value("ORD-1"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void processPayment_WhenFailed_ShouldReturn400() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "FAILED", null, null, "Failed");

        when(paymentService.processPayment(any(PaymentRequest.class), eq("IDEMP-1"))).thenReturn(response);

        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "IDEMP-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void processPayment_WhenCoreBankFailed_ShouldReturn422() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "FAILED", null, null, "CoreBank: Insufficient balance");

        when(paymentService.processPayment(any(PaymentRequest.class), eq("IDEMP-1"))).thenReturn(response);

        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "IDEMP-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void processPayment_WhenBillerFailed_ShouldReturn502() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "FAILED", null, null, "Biller: Biller service unavailable");

        when(paymentService.processPayment(any(PaymentRequest.class), eq("IDEMP-1"))).thenReturn(response);

        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "IDEMP-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void processPayment_WhenIdempotentSuccess_ShouldReturn200() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "SUCCESS", "CB-1", "BL-1", "Payment processed successfully (Idempotent response)");

        when(paymentService.processPayment(any(PaymentRequest.class), eq("IDEMP-1"))).thenReturn(response);

        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "IDEMP-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void processPayment_WhenMissingIdempotencyKey_ShouldReturn400() throws Exception {
        PaymentRequest request = new PaymentRequest("ORD-1", "MOBILE_BANKING", BigDecimal.TEN, "IDR", "VA");

        mockMvc.perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_WhenFound_ShouldReturn200() throws Exception {
        PaymentResponse response = new PaymentResponse("TX-1", "ORD-1", "SUCCESS", "CB-1", "BL-1", "Success");

        when(paymentService.getStatus("ORD-1")).thenReturn(response);

        mockMvc.perform(get("/payments/ORD-1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_id").value("ORD-1"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
