package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NovoPedidoRequest(
        @NotBlank(message = "informe o cliente") String clienteId,
        @NotNull(message = "informe se é entrega ou retirada") TipoEntrega tipoEntrega,
        @Valid @NotEmpty(message = "o pedido precisa de pelo menos um item") List<ItemPedidoRequest> itens,
        @Size(max = 300, message = "a observação aceita no máximo 300 caracteres") String observacao) {
}
