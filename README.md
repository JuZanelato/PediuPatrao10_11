# PediuPatrão — Controle de perfis, pedidos e auditoria

Evolução do sistema **PediuPatrão** (pizzaria) com perfis de acesso, fluxo controlado de pedidos,
descontos e cancelamentos exclusivos do gerente e auditoria persistente no MongoDB.

**Tecnologias:** Java 21 · Spring Boot 3.4 · Spring Security 6 · Spring Data MongoDB (Atlas) · Thymeleaf · Maven

> Aluno(a): _seu nome completo_ — RGM: _seu RGM_
> Vídeo de demonstração: _cole aqui o link do vídeo_

---

## 1. Como executar

### Pré-requisitos
- JDK 21
- Maven 3.9+ (ou o wrapper `mvnw` incluído no projeto) — o NetBeans já traz um Maven embutido
- Acesso à internet (o banco fica no MongoDB Atlas)

### Configuração do banco
Em `src/main/resources/application.properties`:

```properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb+srv://...}
spring.data.mongodb.database=${MONGODB_DATABASE:pizzaria_julianazanelato}
```

O banco padrão é próprio (`pizzaria_<nome>`) para não misturar dados com outros alunos no cluster
compartilhado. Também é possível definir as variáveis de ambiente `MONGODB_URI` e `MONGODB_DATABASE`.

### Executar
- **NetBeans:** abrir o projeto → botão direito → *Run* (ou F6).
- **Terminal:**
  ```bash
  ./mvnw spring-boot:run        # Linux/Mac
  mvnw.cmd spring-boot:run      # Windows
  ```
- Acessar **http://localhost:8080**

### Usuários de demonstração (criados automaticamente no primeiro start)

| Usuário       | Perfil    | Senha inicial |
|---------------|-----------|---------------|
| `admin`       | ADMIN     | `Pizza@2026`  |
| `gerente01`   | GERENTE   | `Pizza@2026`  |
| `atendente01` | ATENDENTE | `Pizza@2026`  |

A senha inicial pode ser trocada pela variável `SENHA_INICIAL`. Também são cadastrados 5 produtos de exemplo
se o cardápio estiver vazio.

### Testes automatizados
```bash
./mvnw test
```
- `StatusPedidoTest` — máquina de estados (transições válidas e bloqueadas).
- `PedidoServiceTest` — cálculo no servidor, desconto ≤ 20%, cancelamento com justificativa, bloqueios.
- `ClienteServiceTest` — auditoria campo a campo da alteração de endereço.
- `AuditoriaServiceTest` — usuário/perfil/data no registro e senha nunca gravada.
- `PedidoControllerSecurityTest` — chamadas diretas à API bloqueadas por perfil (403), sem login (401) e sem CSRF (403).

---

## 2. Requisitos implementados

### RF01 — Usuários e permissões
| Perfil | Pode |
|---|---|
| **ADMIN** | Administrar usuários e perfis, consultar pedidos, consultar auditoria |
| **GERENTE** | Gerenciar pedidos (registrar/avançar status), **aplicar descontos**, **cancelar pedidos**, consultar clientes (e excluir), consultar auditoria |
| **ATENDENTE** | Cadastrar e atualizar clientes, registrar pedidos e acompanhar/avançar o andamento |

- `UsuarioDetailsService` atribui `ROLE_ADMIN`, `ROLE_GERENTE` ou `ROLE_ATENDENTE` conforme o perfil gravado
  (antes atribuía sempre `USER`). Usuário sem perfil válido não autentica.
- **Autorização no backend em duas camadas:** regras por rota/método HTTP no `SecurityConfig` **e**
  `@PreAuthorize` nos serviços (`@EnableMethodSecurity`). Esconder botões é só conveniência visual.
- **Somente o GERENTE** cancela pedidos e aplica descontos — nem o ADMIN consegue.
- **Proteção contra autoelevação:** rotas `/usuarios/**` e `/api/usuarios/**` exclusivas do ADMIN (antes `/api/usuarios`
  era `permitAll`, permitindo que qualquer pessoa criasse um ADMIN); perfil validado contra o enum `Perfil`;
  o ADMIN não altera o próprio perfil nem se exclui; o sistema nunca fica sem ADMIN; formulários usam DTO
  (`UsuarioForm`) em vez da entidade, evitando sobrescrever outro usuário via `id`.
- **CSRF reativado** (estava `disable()`): formulários Thymeleaf enviam o token automaticamente e as chamadas
  `fetch` enviam o cabeçalho `X-CSRF-TOKEN` (`static/js/api.js`). Logout passou a ser `POST`.
- Tentativas de acesso negado retornam **403** (JSON nas APIs, página de erro nas telas), são logadas e
  registradas na auditoria como `ACESSO_NEGADO`. APIs sem login retornam **401**.
- A senha não é mais devolvida no JSON de usuários (`@JsonIgnore`).

### RF02 — Cadastro de pedidos
- `Pedido` passou a ter: itens (`ItemPedido`: produto, quantidade, preço unitário, subtotal), tipo (entrega/retirada),
  subtotal, percentual e valor do desconto, valor total, responsável e data/hora de entrada, saída, finalização e
  cancelamento, histórico de status e controle de versão (`@Version`).
- A requisição (`NovoPedidoRequest`) só aceita **cliente, tipo, produtos e quantidades**. Preços vêm do cadastro
  de produtos e o total é calculado no `PedidoService` — não há como o cliente HTTP definir o valor.
- Validações: cliente e produtos existentes, produto ativo e com preço, quantidade de 1 a 50, pelo menos 1 item.

### RF03 — Entrada, saída e transições
`RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA | RETIRADO → FINALIZADO`

- Implementado no enum `StatusPedido` (máquina de estados) e validado no `PedidoService`.
- Entrada (RECEBIDO) grava data/hora e responsável; a saída (SAIU_PARA_ENTREGA ou RETIRADO) grava
  obrigatoriamente data/hora e responsável; pedido de retirada não pode "sair para entrega" e vice-versa.
- Bloqueia transições inválidas (ex.: finalizar pedido em preparação → HTTP 422) e qualquer alteração após CANCELADO.

### RF04 — Cancelamentos e descontos (somente GERENTE)
- `POST /api/pedidos/{id}/cancelar` — justificativa obrigatória (mín. 10 caracteres), apenas antes da saída;
  preserva itens e valores; depois do cancelamento nada mais pode ser alterado.
- `POST /api/pedidos/{id}/desconto` — percentual > 0 e ≤ 20%, apenas antes da saída; recalcula o total no servidor.
- Ambos registrados na auditoria com usuário, data/hora, valores anteriores/novos e justificativa/motivo.

### RF05 — Auditoria
- Entidade `Auditoria` + `AuditoriaRepository` + `AuditoriaService`, coleção `auditoria` no MongoDB.
- Cada registro guarda: **usuário e perfil**, **data e hora**, **operação**, **entidade e id**,
  **campos alterados com valor anterior e novo**, **justificativa** (quando exigida) e detalhe.
- Auditados: inclusão e alteração de itens de pedidos, mudanças de status, descontos, cancelamentos,
  inclusão/alteração/exclusão de clientes (comparação campo a campo — endereço e telefone), gestão de usuários
  e tentativas de acesso negado.
- Consulta em **/auditoria** (tela com filtros) e **GET /api/auditoria**, apenas ADMIN e GERENTE.
- **Integridade:** o repositório estende apenas `Repository` e expõe somente `save` (inclusão) e consultas;
  não existem endpoints POST/PUT/DELETE de auditoria e a entidade não tem setters.
- **Proteção de dados:** senhas/credenciais nunca são registradas (aparecem como `[protegido]` → `[alterada]`);
  telefones são exibidos mascarados na consulta (`***-4321`); os logs da aplicação não trazem valores pessoais.

---

## 3. Principais alterações (arquivos)

| Camada | Arquivos |
|---|---|
| Configuração | `SecurityConfig` (perfis, rotas, CSRF, 401/403), `DataInitializer` (usuários/produtos de demo), `MongoInitConfig` |
| Segurança | `security/AcessoNegadoHandler`, `security/UsuarioLogado` |
| Entidades | `Pedido`, `ItemPedido`, `HistoricoStatus`, `StatusPedido`, `TipoEntrega`, `Perfil`, `Usuario`, `Cliente`, `Auditoria`, `AlteracaoCampo`, `TipoOperacao` |
| DTOs | `NovoPedidoRequest`, `ItemPedidoRequest`, `AtualizarItensRequest`, `CancelamentoRequest`, `DescontoRequest`, `UsuarioForm`, `AuditoriaView` |
| Repositórios | `AuditoriaRepository` (novo), `PedidoRepository`, `UsuarioRepository` |
| Serviços | `PedidoService`, `ClienteService`, `UsuarioService`, `UsuarioDetailsService`, `AuditoriaService` (novo), `ProdutoService` |
| Controladores | `PedidoController`, `ClienteController`, `UsuarioController`, `AuditoriaController` (novo), `HomeController`, `DadosUsuarioAdvice` |
| Erros | `exception/RegraNegocioException`, `RecursoNaoEncontradoException`, `ApiExceptionHandler` |
| Telas | `layout`, `sidebar`, `pedidos`, `clientes/*`, `usuarios/*`, `produtos/lista`, `auditoria/lista` (nova), `login`, `home`, `static/js/api.js` |

---

## 4. Endpoints

| Método | Rota | Perfis |
|---|---|---|
| GET | `/api/pedidos`, `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE |
| POST | `/api/pedidos` | ATENDENTE, GERENTE |
| PUT | `/api/pedidos/{id}/status?status=EM_PREPARACAO` | ATENDENTE, GERENTE |
| PUT | `/api/pedidos/{id}/itens` (somente RECEBIDO) | ATENDENTE, GERENTE |
| POST | `/api/pedidos/{id}/cancelar` `{"justificativa": "..."}` | **GERENTE** |
| POST | `/api/pedidos/{id}/desconto` `{"percentual": 10, "motivo": "..."}` | **GERENTE** |
| GET | `/api/clientes` | ATENDENTE, GERENTE |
| POST/PUT | `/api/clientes`, `/api/clientes/{id}` | ATENDENTE |
| DELETE | `/api/clientes/{id}` | GERENTE |
| * | `/api/usuarios/**` | ADMIN |
| GET | `/api/auditoria?entidade=&usuario=&operacao=&entidadeId=` | ADMIN, GERENTE |

Códigos de resposta: `401` sem login · `403` sem permissão ou sem token CSRF · `404` não encontrado ·
`409` conflito de versão · `422` regra de negócio violada · `400` dados inválidos.

---

## 5. Roteiro do vídeo de demonstração
1. Login como **atendente01**: cadastrar cliente, registrar pedido (2 itens), avançar até PRONTO.
2. Ainda como atendente: tentar cancelar via console do navegador → **403**; ver que não há menu Auditoria.
3. Editar o endereço/telefone do cliente.
4. Login como **gerente01**: aplicar desconto de 10% (e tentar 25% → bloqueado), cancelar outro pedido com justificativa,
   tentar avançar o pedido cancelado → bloqueado; levar um pedido até SAIU_PARA_ENTREGA (saída registrada) e FINALIZADO;
   tentar finalizar pedido em preparação → bloqueado.
5. Login como **admin**: gerenciar usuários/perfis, tentar alterar o próprio perfil → bloqueado; tentar aplicar desconto
   via API → **403**.
6. Abrir **Auditoria** e mostrar os registros gerados durante o vídeo (inclusive os `ACESSO_NEGADO`).

## Dashboard administrativo

Painel de acompanhamento de vendas e pedidos em `/dashboard` (perfis ADMIN e GERENTE), alimentado pela API `/api/dashboard`.
Requisitos, documentação dos endpoints e testes: [docs/dashboard-administrativo.md](docs/dashboard-administrativo.md) (também em [Word](docs/dashboard-administrativo.docx)).
