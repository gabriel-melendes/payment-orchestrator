package com.example.paymentorchestrator.controller;

import com.example.paymentorchestrator.dto.CreatePaymentIntentRequest;
import com.example.paymentorchestrator.dto.PaymentIntentResponse;
import com.example.paymentorchestrator.entity.PaymentIntentStatus;
import com.example.paymentorchestrator.exception.ApiExceptionHandler;
import com.example.paymentorchestrator.exception.PaymentIntentNotFoundException;
import com.example.paymentorchestrator.service.PaymentIntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentIntentControllerTest {

    private PaymentIntentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PaymentIntentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentIntentController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void shouldReturn201WithSavedIntent() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(new PaymentIntentResponse(id,
                new BigDecimal("125.50"), "BRL", "Pedido 123", PaymentIntentStatus.CREATED,
                Instant.parse("2026-01-01T10:00:00Z")));

        mvc.perform(post("/api/payment-intents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":125.50,\"currency\":\"BRL\",\"description\":\"Pedido 123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.description").value("Pedido 123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists());
        verify(service).create(new CreatePaymentIntentRequest(new BigDecimal("125.50"), "BRL", "Pedido 123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"amount\":null,\"currency\":\"BRL\"}",
            "{\"amount\":0,\"currency\":\"BRL\"}",
            "{\"amount\":-1,\"currency\":\"BRL\"}",
            "{\"amount\":1.001,\"currency\":\"BRL\"}",
            "{\"amount\":100000000000000000,\"currency\":\"BRL\"}",
            "{\"amount\":1,\"currency\":null}",
            "{\"amount\":1,\"currency\":\"\"}",
            "{\"amount\":1,\"currency\":\"USD\"}",
            "{\"amount\":1,\"currency\":\"brl\"}"
    })
    void shouldRejectInvalidFieldsWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/payment-intents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectLongDescription() throws Exception {
        String body = "{\"amount\":1,\"currency\":\"BRL\",\"description\":\"" + "a".repeat(256) + "\"}";
        mvc.perform(post("/api/payment-intents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"amount\":\"abc\",\"currency\":\"BRL\"}"})
    void shouldRejectUnreadableBody(String body) throws Exception {
        mvc.perform(post("/api/payment-intents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Corpo da requisicao invalido"));
        verifyNoInteractions(service);
    }
    @Test
    void shouldReturn200WithExistingIntent() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(new PaymentIntentResponse(id,
                new BigDecimal("12.34"), "BRL", "Pedido 42", PaymentIntentStatus.CREATED,
                Instant.parse("2026-01-01T10:00:00Z")));

        mvc.perform(get("/api/payment-intents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.description").value("Pedido 42"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").exists());
        verify(service).findById(id);
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldReturn404WhenIntentDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new PaymentIntentNotFoundException(id));

        mvc.perform(get("/api/payment-intents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Intencao de pagamento nao encontrada"))
                .andExpect(jsonPath("$.detail").value("Intencao de pagamento nao encontrada: " + id))
                .andExpect(jsonPath("$.instance").value("/api/payment-intents/" + id));
        verify(service).findById(id);
    }

    @Test
    void shouldReturn400ForInvalidUuidWithoutCallingService() throws Exception {
        mvc.perform(get("/api/payment-intents/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Parametro invalido"))
                .andExpect(jsonPath("$.detail").value("O parametro 'id' possui formato invalido."));
        verifyNoInteractions(service);
    }
}
