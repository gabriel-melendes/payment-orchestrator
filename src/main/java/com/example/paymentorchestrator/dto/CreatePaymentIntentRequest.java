package com.example.paymentorchestrator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentIntentRequest(
        @NotNull(message = "O valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "O valor deve ser no minimo 0.01")
        @Digits(integer = 17, fraction = 2, message = "Use ate 17 digitos inteiros e 2 casas decimais")
        BigDecimal amount,
        @NotBlank(message = "A moeda e obrigatoria")
        @Pattern(regexp = "BRL", message = "A moeda suportada nesta etapa e BRL")
        String currency,
        @Size(max = 255, message = "A descricao deve ter no maximo 255 caracteres")
        String description) {
}
