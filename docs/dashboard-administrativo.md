# Dashboard Administrativo — Pediu Patrão

**Disciplina:** Atividades Avaliativas M2 · Atividade Prática: Dashboard Administrativo\
**Autora:** Juliana Zanelato\
**Data:** 10/11/2026\
**Branch da implementação:** `feature/dashboard-api` (gitflow), integrada em `develop` e publicada em `main` pela `release/1.1.0`

---

## 1. Visão geral

O Dashboard Administrativo é um painel web integrado ao sistema de pedidos Pediu Patrão. Ele permite que o responsável pelo estabelecimento acompanhe o desempenho das vendas, a situação dos pedidos e as tendências da operação (dias e horários de maior movimento, categorias e produtos que mais vendem), apoiando a tomada de decisões.

A solução tem duas partes:

- **API REST** (`/api/dashboard/...`), implementada em Spring Boot, que consulta os pedidos gravados no MongoDB e devolve os dados já consolidados em JSON.
- **Tela do dashboard** (`/dashboard`), em Thymeleaf + Bootstrap 5 + Chart.js, que consome a API e exibe indicadores, gráficos, ranking e listas.

```
Navegador (dashboard.html + dashboard.js)
   │  fetch JSON (sessão autenticada)
   ▼
DashboardController     (/api/dashboard)
   ▼
DashboardService        (regras e cálculos)
   ▼
PedidoRepository / ProdutoRepository  (MongoDB)
```

---

## 2. Requisitos funcionais

| Código | Requisito | Como foi atendido |
| --- | --- | --- |
| RF01 | Indicadores gerais de vendas | Cards com faturamento, pedidos válidos, ticket médio, itens vendidos, pedidos em andamento, finalizados, cancelamentos (quantidade e taxa) e descontos concedidos. Faturamento e pedidos mostram a variação em relação ao período anterior de mesmo tamanho. Endpoint `GET /api/dashboard/resumo`. |
| RF02 | Análise de vendas (gráficos) | Gráfico combinado de faturamento (barras) e quantidade de pedidos (linha) por dia, semana ou mês; gráfico de pedidos por hora do dia com destaque do horário de pico; gráfico de faturamento por categoria de produto. Endpoints `GET /api/dashboard/vendas`, `/vendas/por-hora` e `/vendas/por-categoria`. |
| RF03 | Situação dos pedidos | Quantidade e percentual de pedidos em cada status (Recebido, Em preparação, Pronto, Saiu para entrega, Retirado, Finalizado, Cancelado) e lista dos pedidos mais recentes. Endpoints `GET /api/dashboard/pedidos/status` e `/pedidos/recentes`. |
| RF04 | Produtos mais vendidos | Ranking (top 10) ordenável por quantidade ou faturamento, com categoria e participação no faturamento; aceita filtro por categoria. Endpoint `GET /api/dashboard/produtos/mais-vendidos`. |
| RF05 | Filtros de consulta | Período (data inicial e final), tipo de pedido (entrega ou retirada), situação do pedido e agrupamento da série (dia, semana, mês), além de atalhos (hoje, 7, 30 e 90 dias, mês atual). As opções vêm de `GET /api/dashboard/filtros`. Padrão: últimos 30 dias. |
| RF06 | Integração com a API | A tela não acessa o banco diretamente: todos os blocos são carregados por chamadas `fetch` à API, que lê as coleções `pedidos` e `produtos` do MongoDB. |
| RF07 | Atualização e tratamento de dados | Botão "Atualizar", atualização automática a cada 60 segundos (pausada com a aba oculta), horário da última atualização, preenchimento de dias e horas sem venda com zero, valores nulos tratados como zero, itens sem produto agrupados em "Sem categoria", validação de filtros e mensagens de erro por bloco, sem derrubar o restante do painel. |

---

## 3. Requisitos não funcionais

| Código | Categoria | Requisito |
| --- | --- | --- |
| RNF01 | Segurança | Dashboard e API acessíveis apenas a usuários autenticados com perfil **ADMIN** ou **GERENTE**. Sem login a API responde 401; com outro perfil, 403. A API do dashboard é somente leitura: métodos de escrita são bloqueados. |
| RNF02 | Segurança | Credenciais do banco fora do código-fonte: a conexão vem da variável `MONGODB_URI` ou do arquivo local `application-local.properties`, ignorado pelo Git. |
| RNF03 | Desempenho | Consultas sempre limitadas por período (máximo de 366 dias) e índice no campo `dataHoraEntrada`. Rankings e listas limitados a 50 registros. |
| RNF04 | Usabilidade | Layout responsivo (Bootstrap 5), integrado ao menu lateral do sistema, valores em formato brasileiro (R$, datas dd/MM). |
| RNF05 | Confiabilidade | Erros retornam JSON padronizado (`timestamp`, `status`, `erro`, `mensagem`) e a tela trata cada bloco separadamente. |
| RNF06 | Interoperabilidade | API REST com JSON, datas no padrão ISO 8601 (`yyyy-MM-dd`) e valores monetários com duas casas decimais. |
| RNF07 | Consistência | Datas consideradas no fuso `America/Sao_Paulo`, o mesmo usado no registro dos pedidos. |
| RNF08 | Manutenibilidade | Organização em camadas (controller, service, repository, DTOs) seguindo o padrão já existente no projeto. |
| RNF09 | Testabilidade | Regras de cálculo isoladas no `DashboardService`, com relógio injetável para testes determinísticos, e testes automatizados de serviço e de API. |

---

## 4. Análise e regras de negócio

**Dados utilizados.** A coleção `pedidos` já guarda o necessário: cliente, tipo de entrega, itens (produto, quantidade, preço e subtotal), descontos, valor total, status e datas de entrada, saída, finalização e cancelamento. A coleção `produtos` fornece a categoria (campo `tipo`: Pizza, Esfiha, Bebida, Lanche).

**Regras adotadas:**

- O período considera a **data/hora de entrada** do pedido, incluindo a data final inteira.
- **Faturamento** é a soma do `valorTotal` dos pedidos **não cancelados** (já com desconto aplicado).
- **Pedidos válidos** são os não cancelados; **ticket médio** = faturamento ÷ pedidos válidos.
- **Em andamento** são os pedidos que ainda não estão Finalizados nem Cancelados.
- **Taxa de cancelamento** = cancelados ÷ total de pedidos do período × 100.
- **Variação** compara com o período anterior de mesma duração (ex.: 01 a 10/11 é comparado com 22 a 31/10). Quando o período anterior não tem vendas, a variação é `null`.
- Itens de pedidos cancelados não entram no ranking nem nos gráficos de vendas.
- Os filtros de tipo de pedido e situação valem para todos os endpoints; o filtro de categoria vale para o ranking de produtos.

---

## 5. Documentação dos endpoints

### 5.1 Informações gerais

- **URL base:** `http://localhost:8080/api/dashboard`
- **Formato:** JSON (UTF-8). Todos os endpoints usam `GET`.
- **Autenticação:** sessão do Spring Security (login em `/login`). Perfis permitidos: `ADMIN` e `GERENTE`.
- **Testar fora do navegador:** faça login no sistema, copie o cookie `JSESSIONID` (DevTools → Application → Cookies) e envie no cabeçalho, por exemplo:

```bash
curl -H "Cookie: JSESSIONID=SEU_ID_DE_SESSAO" \
  "http://localhost:8080/api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-10"
```

### 5.2 Parâmetros comuns (filtros)

Aceitos por todos os endpoints, exceto `/filtros`. Todos são opcionais.

| Parâmetro | Tipo | Padrão | Descrição |
| --- | --- | --- | --- |
| `dataInicio` | data `yyyy-MM-dd` | `dataFim` − 29 dias | Início do período (inclusive). |
| `dataFim` | data `yyyy-MM-dd` | hoje | Fim do período (inclusive). |
| `tipoEntrega` | `ENTREGA` \| `RETIRADA` | todos | Tipo do pedido. |
| `status` | `RECEBIDO` \| `EM_PREPARACAO` \| `PRONTO` \| `SAIU_PARA_ENTREGA` \| `RETIRADO` \| `FINALIZADO` \| `CANCELADO` | todos | Situação do pedido. |

### 5.3 Erros possíveis

| HTTP | Quando ocorre | Exemplo de `mensagem` |
| --- | --- | --- |
| 400 Bad Request | `dataInicio` posterior a `dataFim` | "A data inicial não pode ser posterior à data final." |
| 400 Bad Request | Intervalo maior que 366 dias | "O intervalo máximo de consulta é de 366 dias." |
| 400 Bad Request | `limite` fora de 1 a 50 | "O limite deve estar entre 1 e 50." |
| 400 Bad Request | Data em formato inválido ou valor inexistente em `status`, `tipoEntrega`, `agrupamento` ou `ordenarPor` | "Requisição inválida. Verifique os dados enviados." |
| 401 Unauthorized | Requisição sem login (sessão ausente ou expirada) | (sem corpo) |
| 403 Forbidden | Usuário com perfil sem permissão (ex.: ATENDENTE) ou método diferente de GET | "Acesso negado: perfil sem permissão." |

Formato do corpo de erro:

```json
{
  "timestamp": "2026-11-10T15:42:10.512",
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "A data inicial não pode ser posterior à data final."
}
```

### 5.4 `GET /api/dashboard/resumo` — Indicadores gerais (RF01)

**Parâmetros:** apenas os comuns.

**Requisição:**

```
GET /api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-10&tipoEntrega=ENTREGA
```

**Resposta 200:**

```json
{
  "dataInicio": "2026-11-01",
  "dataFim": "2026-11-10",
  "totalPedidos": 48,
  "pedidosValidos": 44,
  "pedidosFinalizados": 39,
  "pedidosEmAndamento": 5,
  "pedidosCancelados": 4,
  "faturamento": 3874.60,
  "ticketMedio": 88.06,
  "totalDescontos": 61.30,
  "itensVendidos": 131,
  "taxaCancelamento": 8.3,
  "faturamentoPeriodoAnterior": 3420.00,
  "variacaoFaturamento": 13.3,
  "variacaoPedidos": 10.0
}
```

| Campo | Descrição |
| --- | --- |
| `totalPedidos` | Todos os pedidos do período, inclusive cancelados. |
| `pedidosValidos` | Pedidos não cancelados. |
| `pedidosEmAndamento` | Pedidos ainda não finalizados nem cancelados. |
| `faturamento` | Soma do valor total dos pedidos válidos (R$). |
| `ticketMedio` | Faturamento ÷ pedidos válidos (R$). |
| `totalDescontos` | Soma dos descontos concedidos nos pedidos válidos (R$). |
| `itensVendidos` | Soma das quantidades de itens dos pedidos válidos. |
| `taxaCancelamento` | Percentual de pedidos cancelados. |
| `faturamentoPeriodoAnterior` | Faturamento do período anterior de mesma duração. |
| `variacaoFaturamento`, `variacaoPedidos` | Variação percentual em relação ao período anterior; `null` sem base de comparação. |

### 5.5 `GET /api/dashboard/vendas` — Vendas por período (RF02)

| Parâmetro | Tipo | Padrão | Descrição |
| --- | --- | --- | --- |
| `agrupamento` | `DIA` \| `SEMANA` \| `MES` | `DIA` | Granularidade da série. Semanas começam na segunda-feira. |

Períodos sem venda aparecem com zero, para o gráfico não ter lacunas.

**Requisição:** `GET /api/dashboard/vendas?dataInicio=2026-11-08&dataFim=2026-11-10&agrupamento=DIA`

**Resposta 200:**

```json
{
  "agrupamento": "DIA",
  "pontos": [
    { "inicio": "2026-11-08", "rotulo": "08/11", "quantidadePedidos": 6, "faturamento": 512.40, "ticketMedio": 85.40 },
    { "inicio": "2026-11-09", "rotulo": "09/11", "quantidadePedidos": 0, "faturamento": 0.00,   "ticketMedio": 0.00 },
    { "inicio": "2026-11-10", "rotulo": "10/11", "quantidadePedidos": 9, "faturamento": 801.00, "ticketMedio": 89.00 }
  ]
}
```

### 5.6 `GET /api/dashboard/vendas/por-hora` — Pedidos por horário (RF02)

**Parâmetros:** apenas os comuns. Retorna sempre as 24 horas (0 a 23).

**Resposta 200 (trecho):**

```json
[
  { "hora": 18, "quantidadePedidos": 7,  "faturamento": 598.50 },
  { "hora": 19, "quantidadePedidos": 15, "faturamento": 1320.00 },
  { "hora": 20, "quantidadePedidos": 12, "faturamento": 1044.90 }
]
```

### 5.7 `GET /api/dashboard/vendas/por-categoria` — Vendas por categoria (RF02)

**Parâmetros:** apenas os comuns. Ordenado do maior para o menor faturamento.

**Resposta 200:**

```json
[
  { "categoria": "Pizza",  "quantidade": 58, "faturamento": 3120.30, "participacao": 80.5 },
  { "categoria": "Bebida", "quantidade": 41, "faturamento": 574.00,  "participacao": 14.8 },
  { "categoria": "Esfiha", "quantidade": 28, "faturamento": 182.00,  "participacao": 4.7 }
]
```

### 5.8 `GET /api/dashboard/pedidos/status` — Situação dos pedidos (RF03)

**Parâmetros:** apenas os comuns. Sempre retorna todos os status, inclusive os com zero.

**Resposta 200 (trecho):**

```json
[
  { "status": "RECEBIDO",      "descricao": "Recebido",      "quantidade": 2,  "percentual": 4.2 },
  { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 3,  "percentual": 6.3 },
  { "status": "FINALIZADO",    "descricao": "Finalizado",    "quantidade": 39, "percentual": 81.3 },
  { "status": "CANCELADO",     "descricao": "Cancelado",     "quantidade": 4,  "percentual": 8.3 }
]
```

### 5.9 `GET /api/dashboard/pedidos/recentes` — Pedidos recentes (RF03)

| Parâmetro | Tipo | Padrão | Descrição |
| --- | --- | --- | --- |
| `limite` | inteiro de 1 a 50 | 10 | Quantidade de pedidos retornados, do mais novo para o mais antigo. |

**Resposta 200:**

```json
[
  {
    "id": "6730f1c2a9e4b52d1c8e7f10",
    "clienteNome": "Maria Souza",
    "tipoEntrega": "Entrega",
    "status": "SAIU_PARA_ENTREGA",
    "statusDescricao": "Saiu para entrega",
    "quantidadeItens": 3,
    "valorTotal": 123.80,
    "dataHoraEntrada": "2026-11-10T19:42:05.123"
  }
]
```

### 5.10 `GET /api/dashboard/produtos/mais-vendidos` — Ranking de produtos (RF04)

| Parâmetro | Tipo | Padrão | Descrição |
| --- | --- | --- | --- |
| `limite` | inteiro de 1 a 50 | 10 | Tamanho do ranking. |
| `ordenarPor` | `QUANTIDADE` \| `FATURAMENTO` | `QUANTIDADE` | Critério de ordenação. |
| `categoria` | texto | todas | Filtra por categoria (sem diferenciar maiúsculas). |

**Requisição:** `GET /api/dashboard/produtos/mais-vendidos?limite=3&ordenarPor=FATURAMENTO`

**Resposta 200:**

```json
[
  { "posicao": 1, "produtoId": "672f...a1", "nome": "Pizza Calabresa",  "categoria": "Pizza",  "quantidade": 30, "faturamento": 1647.00, "participacao": 42.5 },
  { "posicao": 2, "produtoId": "672f...a3", "nome": "Pizza Portuguesa", "categoria": "Pizza",  "quantidade": 14, "faturamento": 819.00,  "participacao": 21.1 },
  { "posicao": 3, "produtoId": "672f...a5", "nome": "Refrigerante 2L",  "categoria": "Bebida", "quantidade": 41, "faturamento": 574.00,  "participacao": 14.8 }
]
```

### 5.11 `GET /api/dashboard/filtros` — Opções de filtro (RF05)

Não recebe parâmetros. Devolve os valores aceitos pelos filtros e o período padrão.

**Resposta 200:**

```json
{
  "status": [ { "valor": "RECEBIDO", "descricao": "Recebido" }, { "valor": "CANCELADO", "descricao": "Cancelado" } ],
  "tiposEntrega": [ { "valor": "ENTREGA", "descricao": "Entrega" }, { "valor": "RETIRADA", "descricao": "Retirada no balcão" } ],
  "agrupamentos": [ { "valor": "DIA", "descricao": "Por dia" }, { "valor": "SEMANA", "descricao": "Por semana" }, { "valor": "MES", "descricao": "Por mês" } ],
  "categorias": [ "Bebida", "Esfiha", "Pizza" ],
  "dataInicioPadrao": "2026-10-12",
  "dataFimPadrao": "2026-11-10",
  "intervaloMaximoDias": 366
}
```

*(listas de status resumidas no exemplo; a resposta real traz todos os status)*

---

## 6. Dashboard administrativo

- **Endereço:** `http://localhost:8080/dashboard`, também acessível pelo item **Dashboard** do menu lateral (visível para ADMIN e GERENTE).
- **Blocos da tela:** filtros e atalhos de período; seis indicadores (RF01); gráfico de vendas no período; situação dos pedidos; pedidos por horário; faturamento por categoria; ranking de produtos; pedidos recentes.
- **Atualização:** manual (botão) ou automática a cada 60 segundos.

**Arquivos principais:**

| Camada | Arquivo |
| --- | --- |
| API | `controller/DashboardController.java` |
| Regras | `service/DashboardService.java` |
| Dados | `repository/PedidoRepository.java` (consulta por período), `entity/Pedido.java` (índice) |
| DTOs | `dto/dashboard/*.java` |
| Erros | `exception/FiltroInvalidoException.java`, `exception/ApiExceptionHandler.java` |
| Segurança | `config/SecurityConfig.java` |
| Tela | `templates/dashboard.html`, `static/js/dashboard.js`, `static/css/style.css` |

---

## 7. Gitflow

| Branch | Papel |
| --- | --- |
| `main` | Versão publicada. Recebe a `release/1.1.0` (tag `v1.1.0`). |
| `develop` | Integração das funcionalidades. |
| `feature/dashboard-api` | Implementação da API, do dashboard, dos testes e desta documentação. |
| `release/1.1.0` | Preparação da versão com o dashboard, mesclada em `main` e `develop`. |

---

## 8. Testes

### 8.1 Testes automatizados

Executar na pasta do projeto:

```bash
./mvnw test
```

(no Windows: `mvnw.cmd test`)

**`DashboardServiceTest`** — regras de negócio (testes unitários com Mockito):

| Teste | O que verifica |
| --- | --- |
| `filtroSemDatasUsaUltimos30Dias` | Período padrão de 30 dias terminando hoje. |
| `dataInicialPosteriorAFinalGeraErro` | Período invertido é rejeitado. |
| `intervaloMaiorQueUmAnoGeraErro` | Intervalo acima de 366 dias é rejeitado. |
| `limiteForaDaFaixaGeraErro` | `limite` fora de 1 a 50 é rejeitado. |
| `resumoIgnoraCanceladosNoFaturamentoECalculaIndicadores` | Faturamento, ticket médio, descontos, itens e taxa de cancelamento. |
| `resumoCalculaVariacaoEmRelacaoAoPeriodoAnterior` | Variação de faturamento e de pedidos. |
| `resumoSemPedidosRetornaZerosESemVariacao` | Período vazio não gera erro nem divisão por zero. |
| `resumoTrataValoresNulosComoZero` | Pedidos com valores, itens ou data nulos. |
| `filtroPorTipoEntregaEStatusEhAplicado` | Filtros de tipo de pedido e situação. |
| `vendasPorDiaPreencheDiasSemVendaComZero` | Série diária sem lacunas. |
| `vendasPorMesAgrupaNoPrimeiroDiaDoMes` | Agrupamento mensal. |
| `vendasPorHoraRetorna24HorasComPico` | Distribuição por hora, sem cancelados. |
| `vendasPorCategoriaUsaTipoDoProduto` | Categorias e "Sem categoria". |
| `pedidosPorStatusListaTodasAsSituacoes` | Contagem e percentual por status. |
| `pedidosRecentesOrdenaDoMaisNovoParaOMaisAntigo` | Ordenação e limite. |
| `rankingPorQuantidadeEFaturamento` | Ranking pelos dois critérios, sem cancelados. |
| `rankingFiltraPorCategoria` | Filtro de categoria no ranking. |

**`DashboardControllerTest`** — API e segurança (`@WebMvcTest` com MockMvc):

| Teste | Resultado esperado |
| --- | --- |
| `semLoginRecebe401` | 401 sem login. |
| `atendenteNaoAcessaDashboard` | 403 para ATENDENTE. |
| `adminAcessaDashboard` | 200 para ADMIN. |
| `metodosDeEscritaSaoBloqueados` | 403 em POST. |
| `resumoRetornaIndicadores` | 200 com os campos do resumo. |
| `vendasAceitaAgrupamento` | 200 com a série semanal. |
| `rankingRepassaParametros` | 200 com o ranking filtrado. |
| `periodoInvertidoRetorna400ComMensagem` | 400 com mensagem de erro. |
| `dataEmFormatoInvalidoRetorna400` | 400 para data `10/11/2026`. |
| `statusInexistenteRetorna400` | 400 para status inexistente. |
| `limiteInvalidoRetorna400` | 400 para `limite=500`. |

### 8.2 Evidências

As capturas de tela ficam na pasta `docs/evidencias/`:

| Nº | Evidência | Arquivo |
| --- | --- | --- |
| 1 | Resultado do `mvnw test` com todos os testes aprovados | `docs/evidencias/01-testes-automatizados.png` |
| 2 | Dashboard carregado com os indicadores e gráficos (perfil GERENTE) | `docs/evidencias/02-dashboard.png` |
| 3 | Dashboard com filtros aplicados (ex.: 7 dias, tipo Entrega) | `docs/evidencias/03-dashboard-filtrado.png` |
| 4 | Resposta JSON de `/api/dashboard/resumo` no navegador | `docs/evidencias/04-api-resumo.png` |
| 5 | Erro 400 com período invertido (`dataInicio` > `dataFim`) | `docs/evidencias/05-api-erro-400.png` |
| 6 | Acesso negado (403) com o usuário `atendente01` | `docs/evidencias/06-api-erro-403.png` |

### 8.3 Testes manuais

| Cenário | Passos | Resultado esperado |
| --- | --- | --- |
| Carga inicial | Entrar como `gerente01` e abrir **Dashboard** | Indicadores e gráficos dos últimos 30 dias. |
| Filtro de período | Clicar em "7 dias" | Todos os blocos recalculados para 7 dias. |
| Filtro inválido | Data inicial maior que a final e clicar em "Aplicar" | Aviso na tela; nenhuma consulta é feita. |
| Atualização | Registrar um pedido em outra aba e clicar em "Atualizar" (ou aguardar 60 s) | O pedido aparece em "Pedidos recentes" e nos indicadores. |
| Ranking | Alternar "Quantidade" / "Faturamento" | Ordem do ranking muda. |
| Permissão | Entrar como `atendente01` | Item Dashboard não aparece; `/api/dashboard/resumo` retorna 403. |

---

## 9. Como executar

1. Na pasta do projeto, copie `application-local.properties.example` para `application-local.properties` e informe o endereço do MongoDB em `MONGODB_URI` (ou defina a variável de ambiente `MONGODB_URI`).
2. Execute `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`) ou rode a classe `PizzariaPedidosApplication` pela IDE.
3. Acesse `http://localhost:8080`, entre com `gerente01` ou `admin` (senha definida em `app.seguranca.senha-inicial`) e clique em **Dashboard**.
