package com.example.paymentorchestrator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusServiceTest {

    private final StatusService statusService = new StatusService();

    @Test
    void shouldReturnUpStatus() {
        var response = statusService.getStatus();

        assertEquals("UP", response.status());
    }
}
