/*
 * Dashboard administrativo — consome a API /api/dashboard (RF06) e atualiza a tela (RF07).
 * Depende de api.js (apiFetch, esc, formatarMoeda, formatarDataHora) e de Chart.js 2.x.
 */
(function () {
    const API = '/api/dashboard';
    const INTERVALO_ATUALIZACAO_MS = 60000;

    const CORES = {
        primaria: '#0d6efd',
        primariaClara: 'rgba(13, 110, 253, 0.25)',
        destaque: '#fd7e14',
        texto: '#6c757d'
    };
    const COR_STATUS = {
        RECEBIDO: '#0dcaf0',
        EM_PREPARACAO: '#ffc107',
        PRONTO: '#20c997',
        SAIU_PARA_ENTREGA: '#0d6efd',
        RETIRADO: '#6610f2',
        FINALIZADO: '#198754',
        CANCELADO: '#dc3545'
    };

    const el = (id) => document.getElementById(id);
    const graficos = {};
    let temporizador = null;
    let carregando = false;
    let padrao = null;

    // ------------------------------------------------------------------ utilidades

    function hojeIso() {
        return new Date().toLocaleDateString('sv-SE'); // yyyy-MM-dd no fuso do navegador
    }

    function somarDias(iso, dias) {
        const d = new Date(iso + 'T12:00:00');
        d.setDate(d.getDate() + dias);
        return d.toLocaleDateString('sv-SE');
    }

    function numero(v) {
        return Number(v || 0).toLocaleString('pt-BR');
    }

    function percentual(v) {
        return Number(v || 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + '%';
    }

    function variacaoHtml(v, rotulo) {
        if (v === null || v === undefined) {
            return '<span class="text-muted">sem base no período anterior</span>';
        }
        const n = Number(v);
        const classe = n > 0 ? 'text-success' : (n < 0 ? 'text-danger' : 'text-muted');
        const icone = n > 0 ? 'bi-arrow-up-right' : (n < 0 ? 'bi-arrow-down-right' : 'bi-dash');
        const sinal = n > 0 ? '+' : '';
        return '<span class="' + classe + '"><i class="bi ' + icone + '"></i> ' + sinal + percentual(n) +
            '</span> <span class="text-muted">' + esc(rotulo) + '</span>';
    }

    function mostrarMensagem(texto, tipo) {
        el('dashboard-msg').innerHTML = texto
            ? '<div class="alert alert-' + (tipo || 'danger') + ' alert-dismissible fade show py-2" role="alert">' +
              esc(texto) + '<button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fechar"></button></div>'
            : '';
    }

    function filtrosAtuais() {
        const p = new URLSearchParams();
        p.set('dataInicio', el('fDataInicio').value);
        p.set('dataFim', el('fDataFim').value);
        if (el('fTipoEntrega').value) p.set('tipoEntrega', el('fTipoEntrega').value);
        if (el('fStatus').value) p.set('status', el('fStatus').value);
        return p;
    }

    function validarFiltros() {
        const inicio = el('fDataInicio').value;
        const fim = el('fDataFim').value;
        if (!inicio || !fim) return 'Informe a data inicial e a data final.';
        if (inicio > fim) return 'A data inicial não pode ser posterior à data final.';
        return null;
    }

    function url(caminho, extras) {
        const p = filtrosAtuais();
        Object.entries(extras || {}).forEach(([k, v]) => { if (v !== null && v !== undefined && v !== '') p.set(k, v); });
        return API + caminho + '?' + p.toString();
    }

    function criarOuAtualizarGrafico(id, config) {
        if (graficos[id]) {
            graficos[id].data = config.data;
            graficos[id].options = config.options;
            graficos[id].update();
            return;
        }
        graficos[id] = new Chart(el(id).getContext('2d'), config);
    }

    // Função (e não objeto compartilhado) porque o Chart.js altera a configuração de eixo que recebe.
    const eixoMoeda = (extra) => Object.assign({
        ticks: {
            beginAtZero: true,
            callback: (v) => 'R$ ' + Number(v).toLocaleString('pt-BR', { maximumFractionDigits: 0 })
        }
    }, extra || {});

    // ------------------------------------------------------------------ renderização

    function renderResumo(r) {
        el('kpiFaturamento').textContent = formatarMoeda(r.faturamento);
        el('kpiFaturamentoVar').innerHTML = variacaoHtml(r.variacaoFaturamento, 'vs. período anterior');
        el('kpiPedidos').textContent = numero(r.pedidosValidos);
        el('kpiPedidosVar').innerHTML = variacaoHtml(r.variacaoPedidos, 'vs. período anterior');
        el('kpiTicket').textContent = formatarMoeda(r.ticketMedio);
        el('kpiItens').textContent = numero(r.itensVendidos) + ' itens vendidos';
        el('kpiAndamento').textContent = numero(r.pedidosEmAndamento);
        el('kpiFinalizados').textContent = numero(r.pedidosFinalizados) + ' finalizados';
        el('kpiCancelados').textContent = numero(r.pedidosCancelados);
        el('kpiTaxaCancelamento').textContent = percentual(r.taxaCancelamento) + ' dos pedidos';
        el('kpiDescontos').textContent = formatarMoeda(r.totalDescontos);
    }

    function renderVendas(serie) {
        const pontos = serie.pontos || [];
        criarOuAtualizarGrafico('graficoVendas', {
            type: 'bar',
            data: {
                labels: pontos.map((p) => p.rotulo),
                datasets: [
                    {
                        type: 'line',
                        label: 'Pedidos',
                        data: pontos.map((p) => p.quantidadePedidos),
                        yAxisID: 'pedidos',
                        borderColor: CORES.destaque,
                        backgroundColor: CORES.destaque,
                        fill: false,
                        lineTension: 0.25,
                        pointRadius: 2
                    },
                    {
                        label: 'Faturamento',
                        data: pontos.map((p) => Number(p.faturamento)),
                        yAxisID: 'valor',
                        backgroundColor: CORES.primariaClara,
                        borderColor: CORES.primaria,
                        borderWidth: 1
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { position: 'bottom' },
                tooltips: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        label: (item, data) => {
                            const ds = data.datasets[item.datasetIndex];
                            return ds.label + ': ' + (ds.yAxisID === 'valor' ? formatarMoeda(item.yLabel) : numero(item.yLabel));
                        }
                    }
                },
                scales: {
                    xAxes: [{ gridLines: { display: false } }],
                    yAxes: [
                        eixoMoeda({ id: 'valor', position: 'left' }),
                        { id: 'pedidos', position: 'right', gridLines: { drawOnChartArea: false },
                          ticks: { beginAtZero: true, precision: 0 } }
                    ]
                }
            }
        });
    }

    function renderStatus(lista) {
        const total = lista.reduce((s, x) => s + x.quantidade, 0);
        if (total === 0) {
            el('listaStatus').innerHTML = '<div class="text-muted small">Nenhum pedido no período.</div>';
            return;
        }
        el('listaStatus').innerHTML = lista.map((s) => {
            const cor = COR_STATUS[s.status] || CORES.texto;
            return '<div class="mb-2">' +
                '<div class="d-flex justify-content-between small">' +
                '<span><span class="status-ponto" style="background:' + cor + '"></span>' + esc(s.descricao) + '</span>' +
                '<span class="fw-semibold">' + numero(s.quantidade) + ' <span class="text-muted fw-normal">(' + percentual(s.percentual) + ')</span></span>' +
                '</div>' +
                '<div class="progress" style="height:6px">' +
                '<div class="progress-bar" role="progressbar" style="width:' + Number(s.percentual) + '%;background:' + cor + '"></div>' +
                '</div></div>';
        }).join('');
    }

    function renderHoras(lista) {
        const pico = lista.reduce((m, h) => (h.quantidadePedidos > (m ? m.quantidadePedidos : 0) ? h : m), null);
        el('horarioPico').textContent = pico
            ? '— pico às ' + String(pico.hora).padStart(2, '0') + 'h (' + numero(pico.quantidadePedidos) + ' pedidos)'
            : '';
        criarOuAtualizarGrafico('graficoHoras', {
            type: 'bar',
            data: {
                labels: lista.map((h) => String(h.hora).padStart(2, '0') + 'h'),
                datasets: [{
                    label: 'Pedidos',
                    data: lista.map((h) => h.quantidadePedidos),
                    backgroundColor: lista.map((h) => (pico && h.hora === pico.hora ? CORES.destaque : CORES.primariaClara)),
                    borderColor: CORES.primaria,
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                scales: {
                    xAxes: [{ gridLines: { display: false } }],
                    yAxes: [{ ticks: { beginAtZero: true, precision: 0 } }]
                }
            }
        });
    }

    function renderCategorias(lista) {
        criarOuAtualizarGrafico('graficoCategorias', {
            type: 'horizontalBar',
            data: {
                labels: lista.map((c) => c.categoria),
                datasets: [{
                    label: 'Faturamento',
                    data: lista.map((c) => Number(c.faturamento)),
                    backgroundColor: CORES.primariaClara,
                    borderColor: CORES.primaria,
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                legend: { display: false },
                tooltips: {
                    callbacks: {
                        label: (item) => {
                            const c = lista[item.index];
                            return formatarMoeda(c.faturamento) + ' · ' + numero(c.quantidade) + ' itens · ' + percentual(c.participacao);
                        }
                    }
                },
                scales: {
                    xAxes: [eixoMoeda()],
                    yAxes: [{ gridLines: { display: false } }]
                }
            }
        });
    }

    function renderProdutos(lista) {
        if (!lista.length) {
            el('tabelaProdutos').innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Nenhum produto vendido no período.</td></tr>';
            return;
        }
        el('tabelaProdutos').innerHTML = lista.map((p) =>
            '<tr>' +
            '<td class="ps-3 fw-semibold">' + p.posicao + 'º</td>' +
            '<td>' + esc(p.nome) + '</td>' +
            '<td><span class="badge bg-light text-dark">' + esc(p.categoria) + '</span></td>' +
            '<td class="text-end">' + numero(p.quantidade) + '</td>' +
            '<td class="text-end">' + formatarMoeda(p.faturamento) + '</td>' +
            '<td class="text-end pe-3 text-muted">' + percentual(p.participacao) + '</td>' +
            '</tr>').join('');
    }

    function renderRecentes(lista) {
        if (!lista.length) {
            el('tabelaRecentes').innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Nenhum pedido no período.</td></tr>';
            return;
        }
        el('tabelaRecentes').innerHTML = lista.map((p) => {
            const cor = COR_STATUS[p.status] || CORES.texto;
            return '<tr>' +
                '<td class="ps-3 small">' + esc(formatarDataHora(p.dataHoraEntrada)) + '</td>' +
                '<td>' + esc(p.clienteNome) + '</td>' +
                '<td class="small">' + esc(p.tipoEntrega || '—') + '</td>' +
                '<td><span class="badge" style="background:' + cor + '">' + esc(p.statusDescricao || '—') + '</span></td>' +
                '<td class="text-end pe-3">' + formatarMoeda(p.valorTotal) + '</td>' +
                '</tr>';
        }).join('');
    }

    // ------------------------------------------------------------------ carga dos dados

    async function carregar() {
        const erroFiltro = validarFiltros();
        if (erroFiltro) {
            mostrarMensagem(erroFiltro, 'warning');
            return;
        }
        if (carregando) return;
        carregando = true;
        el('btnAtualizar').disabled = true;
        el('ultimaAtualizacao').textContent = 'Atualizando…';

        const ordenarPor = document.querySelector('input[name="ordenarPor"]:checked').value;
        const tarefas = [
            ['indicadores', apiFetch(url('/resumo')), renderResumo],
            ['vendas no período', apiFetch(url('/vendas', { agrupamento: el('fAgrupamento').value })), renderVendas],
            ['situação dos pedidos', apiFetch(url('/pedidos/status')), renderStatus],
            ['pedidos por horário', apiFetch(url('/vendas/por-hora')), renderHoras],
            ['vendas por categoria', apiFetch(url('/vendas/por-categoria')), renderCategorias],
            ['produtos mais vendidos', apiFetch(url('/produtos/mais-vendidos', { limite: 10, ordenarPor: ordenarPor })), renderProdutos],
            ['pedidos recentes', apiFetch(url('/pedidos/recentes', { limite: 8 })), renderRecentes]
        ];

        // Cada bloco é tratado de forma independente: uma falha não derruba o restante do painel.
        const resultados = await Promise.allSettled(tarefas.map((t) => t[1]));
        const falhas = [];
        resultados.forEach((res, i) => {
            const [nome, , render] = tarefas[i];
            if (res.status === 'fulfilled') {
                try {
                    render(res.value || []);
                } catch (e) {
                    falhas.push(nome + ' (erro ao exibir)');
                }
            } else {
                falhas.push(nome + ': ' + res.reason.message);
                if (res.reason.status === 401) pararAtualizacaoAutomatica();
            }
        });

        if (falhas.length) {
            mostrarMensagem('Não foi possível carregar ' + falhas.join('; '));
        } else {
            mostrarMensagem(null);
        }
        el('ultimaAtualizacao').textContent = 'Atualizado às ' +
            new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        el('btnAtualizar').disabled = false;
        carregando = false;
    }

    async function carregarOpcoesFiltro() {
        try {
            const opcoes = await apiFetch(API + '/filtros');
            padrao = { inicio: opcoes.dataInicioPadrao, fim: opcoes.dataFimPadrao };
            (opcoes.tiposEntrega || []).forEach((o) => el('fTipoEntrega').add(new Option(o.descricao, o.valor)));
            (opcoes.status || []).forEach((o) => el('fStatus').add(new Option(o.descricao, o.valor)));
        } catch (e) {
            // Sem as opções da API, a tela continua funcionando com o período padrão local.
            padrao = { inicio: somarDias(hojeIso(), -29), fim: hojeIso() };
            mostrarMensagem('Não foi possível carregar as opções de filtro: ' + e.message, 'warning');
        }
        el('fDataInicio').value = padrao.inicio;
        el('fDataFim').value = padrao.fim;
    }

    // ------------------------------------------------------------------ atualização automática (RF07)

    function iniciarAtualizacaoAutomatica() {
        pararAtualizacaoAutomatica();
        if (el('autoAtualizar').checked) {
            temporizador = setInterval(() => { if (!document.hidden) carregar(); }, INTERVALO_ATUALIZACAO_MS);
        }
    }

    function pararAtualizacaoAutomatica() {
        if (temporizador) clearInterval(temporizador);
        temporizador = null;
    }

    // ------------------------------------------------------------------ eventos

    function aplicarAtalho(botao) {
        const hoje = hojeIso();
        if (botao.dataset.mes === 'atual') {
            el('fDataInicio').value = hoje.slice(0, 8) + '01';
        } else {
            el('fDataInicio').value = somarDias(hoje, -(Number(botao.dataset.dias) - 1));
        }
        el('fDataFim').value = hoje;
        carregar();
    }

    document.addEventListener('DOMContentLoaded', async () => {
        el('formFiltros').addEventListener('submit', (ev) => { ev.preventDefault(); carregar(); });
        el('btnAtualizar').addEventListener('click', carregar);
        el('btnLimpar').addEventListener('click', () => {
            el('fDataInicio').value = padrao.inicio;
            el('fDataFim').value = padrao.fim;
            el('fTipoEntrega').value = '';
            el('fStatus').value = '';
            el('fAgrupamento').value = 'DIA';
            carregar();
        });
        document.querySelectorAll('.atalho-periodo').forEach((b) => b.addEventListener('click', () => aplicarAtalho(b)));
        document.querySelectorAll('input[name="ordenarPor"]').forEach((r) => r.addEventListener('change', async () => {
            try {
                renderProdutos(await apiFetch(url('/produtos/mais-vendidos', { limite: 10, ordenarPor: r.value })));
            } catch (e) {
                mostrarMensagem('Não foi possível carregar produtos mais vendidos: ' + e.message);
            }
        }));
        el('autoAtualizar').addEventListener('change', iniciarAtualizacaoAutomatica);
        document.addEventListener('visibilitychange', () => { if (!document.hidden && el('autoAtualizar').checked) carregar(); });

        await carregarOpcoesFiltro();
        await carregar();
        iniciarAtualizacaoAutomatica();
    });
})();
