package com.koriebruh.paymentgatewaycip.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseFactoryTest {

    private final ApiResponseFactory factory = new ApiResponseFactory();

    @Test
    void success_ShouldCreateSuccessResponse() {
        ApiResponse<String> response = factory.success("TestData");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getMessage()).isEqualTo("Operation successful");
        assertThat(response.getData()).isEqualTo("TestData");
    }

    @Test
    void successWithMessage_ShouldCreateSuccessResponse() {
        ApiResponse<String> response = factory.success("TestData", "Custom Msg");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getMessage()).isEqualTo("Custom Msg");
        assertThat(response.getData()).isEqualTo("TestData");
    }

    @Test
    void errorWithList_ShouldCreateFailedResponse() {
        ApiResponse<Void> response = factory.error("Error Msg", List.of("ERR-1"));
        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getMessage()).isEqualTo("Error Msg");
        assertThat(response.getErrors()).contains("ERR-1");
    }

    @Test
    void errorWithData_ShouldCreateFailedResponse() {
        ApiResponse<String> response = factory.error("Data", "Error Msg");
        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getMessage()).isEqualTo("Error Msg");
        assertThat(response.getData()).isEqualTo("Data");
    }
}
