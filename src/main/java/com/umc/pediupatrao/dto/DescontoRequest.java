package com.umc.pediupatrao.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DescontoRequest(
        @NotNull(message = "informe o percentual")
        @DecimalMin(value = "0", inclusive = false, message = "o percentual deve ser maior que zero")
        @DecimalMax(value = "20", message = "o desconto máximo é de 20%")
        BigDecimal percentual,
        @Size(max = 300, message = "o motivo aceita no máximo 300 caracteres") String motivo) {
}
