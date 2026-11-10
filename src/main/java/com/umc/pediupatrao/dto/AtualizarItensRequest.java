package com.umc.pediupatrao.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AtualizarItensRequest(
        @Valid @NotEmpty(message = "o pedido precisa de pelo menos um item") List<ItemPedidoRequest> itens) {
}
