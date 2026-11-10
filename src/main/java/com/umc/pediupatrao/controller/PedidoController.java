package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AtualizarItensRequest;
import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.NovoPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@Valid @RequestBody NovoPedidoRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(pedido));
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/itens")
    public Pedido atualizarItens(@PathVariable String id, @Valid @RequestBody AtualizarItensRequest dados) {
        return pedidoService.atualizarItens(id, dados.itens());
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status) {
        return pedidoService.atualizarStatus(id, status);
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @Valid @RequestBody CancelamentoRequest dados) {
        return pedidoService.cancelar(id, dados.justificativa());
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @Valid @RequestBody DescontoRequest dados) {
        return pedidoService.aplicarDesconto(id, dados.percentual(), dados.motivo());
    }
}
