package com.koriebruh.paymentgatewaycip.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseFactoryTest {

    private final ApiResponseFactory factory = new ApiResponseFactory();

    @Test
    void success_ShouldCreateSuccessResponse() {
        ApiResponse<String> response = factory.success("TestData");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.data()).isEqualTo("TestData");
    }

    @Test
    void successWithMessage_ShouldCreateSuccessResponse() {
        ApiResponse<String> response = factory.success("TestData", "Custom Msg");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("Custom Msg");
        assertThat(response.data()).isEqualTo("TestData");
    }

    @Test
    void errorWithList_ShouldCreateFailedResponse() {
        ApiResponse<Void> response = factory.error("Error Msg", List.of("ERR-1"));
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Error Msg");
        assertThat(response.errors()).contains("ERR-1");
    }

    @Test
    void errorWithData_ShouldCreateFailedResponse() {
        ApiResponse<String> response = factory.error("Data", "Error Msg");
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Error Msg");
        assertThat(response.data()).isEqualTo("Data");
    }
}
