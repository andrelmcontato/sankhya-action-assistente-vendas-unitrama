# Plano Técnico & Portões de Fase -1: Assistente de Vendas Unitrama

**Feature ID**: `001-assistente-vendas-unitrama`  
**Status**: AJUSTADO COM AS PREMISSAS UNITRAMA (ZERO REINVENÇÃO)  

---

## 📌 Premissas Mandatórias da Unitrama (Zero Reinvenção)

1. **IPI Embutido Já Ativo no ERP (`RecalculoIpiEmbutidoListener`)**:
   - O ERP da Unitrama já possui o ouvinte oficial ativo na `TGFITE`.
   - **Papel do Assistente de Vendas**: Apenas entregar o `VLRUNIT` (preço bruto negociado/digitado pelo vendedor) e a `QTDNEG` para a gravação do item.
   - **O que NÃO fazer**: Não deduzir IPI manualmente nem duplicar cálculos fiscais na Action; o listener nativo da Unitrama intercepta a inclusão e deduz o IPI automaticamente no banco.

2. **Margem Baseada na Personalização Existente (`RecalculoIPIMargem`)**:
   - Replicar estritamente o serviço [`CalculoMargemTriggerService`](file:///C:/Users/andre/IdeaProjects/RecalculoIPIMargem/src/br/com/a3consultec/recalculomargem/service/CalculoMargemTriggerService.java) já homologado e em produção na Unitrama.
   - Apenas fornecer a bidirecionalidade na tela:
     - Vendedor digita o Preço $\to$ calcula a Margem usando a fórmula exata da Unitrama;
     - Vendedor digita a Margem $\to$ calcula o Preço usando a fórmula exata da Unitrama.

---

## 🚪 Portões de Fase -1 (Pre-Implementation Gates)

### 1. Portão da Simplicidade (*Simplicity Gate*) — [APROVADO]
- **Solução Mínima e Direta**: Sem código redundante de tributação de IPI. A Action apenas entrega `VLRUNIT` acordado pelo vendedor.
- **Reaproveitamento 100% Fiel**: Reutiliza `CalculoMargemTriggerService` idêntico ao projeto `RecalculoIPIMargem`.
- **Zero Tabelas Novas**: Opera sobre a estrutura nativa existente da Unitrama (`TGFITE`, `TGFCAB`, `TGFPAR`, `TVIMBR`).

### 2. Portão Anti-Abstração (*Anti-Abstraction Gate*) — [APROVADO]
- **JAPE Nativo**: Inclusão de itens via `CACHelper.incluirAlterarItem` com `JapeSessionContext` simulando a digitação manual de tela da Central de Compras/Vendas.
- **Totalização e Tributos**: Chamada oficial de `ImpostosHelpper.calcularImpostos`, `totalizarNota` e `CentralFinanceiro.refazerFinanceiro` preservando `VLRFRETE`.

### 3. Portão de Contratos e Testes (*Contract & Test Gate*) — [APROVADO]
- **Contratos DTO Tipados**:
  - `SugestaoProdutoDTO`: campos `codProd`, `descrProd`, `vlrVenda` (preço sugerido), `margemSugerida`, `aliquotaIpi`, `fatorSimulado`, `fatorIpi`, `custoVariavel` (utilizado exclusivamente no backend e nulificado antes do envio ao cliente).
  - `NotaFiscalContexto`: parâmetros do pedido (`nuNota`, `percDescCabecalho`, `usaDescEspecial`, `percDescParceiro`, `vinculo`, `clienteTemIpi`, `isClienteSuframa`, `topRecalculaIpi`, `vlrFrete`).
- **Testes Unitários Automatizados**:
  - Suíte JUnit 4.12 cobrindo cálculos de margem direta, preço a partir de margem alvo, conciliação com IPI embutido, isolamento de custos e renderização da modal.

### 4. Portão de Segurança & Sigilo (*Security & Privacy Gate*) — [APROVADO]
- **Blindagem de Custo**: O DTO calcula os fatores matemáticos e a margem inicial no servidor e zera o custo (`dto.setCustoVariavel(null)`) antes de gerar o JSON entregue ao cliente. O frontend manipula os fatores pré-calculados $\Omega$ e $C_{var}$ para a reatividade ou faz chamadas sob demanda sem vazar números de margem bruta sensíveis.

---

## 🧮 Modelagem Matemática (Harmonia Unitrama: Trigger x Listener de IPI)

### 1. Fator Simulado $F_{sim}$ (Desconto Especial)
$$F_{sim} = \begin{cases} \frac{1}{\text{percDescParceiro}/100}, & \text{se nota de venda com desconto especial} \\ \frac{1}{1 - (\text{percDescParceiro}/100)}, & \text{se outro vínculo com desconto especial} \\ 1, & \text{caso padrão} \end{cases}$$

### 2. Fator de Acoplamento $\Omega$ (Trigger x Listener)
$$\Omega = \frac{F_{sim} \times (1 - \text{descCab}) + (\text{fatorIpi} - 1)}{\text{fatorIpi}}$$

### 3. Cálculo de Preço a partir da Margem Alvo ($m$):
$$\text{Preço de Venda Bruto} = \frac{C_{var}}{(1 - m) \times \Omega}$$

### 4. Cálculo de Margem a partir do Preço de Venda Digitado ($P$):
$$\text{Receita Simulada com IPI} = P \times \Omega$$
$$\text{Margem (\%)} = \frac{\text{Receita Simulada com IPI} - C_{var}}{\text{Receita Simulada com IPI}} \times 100$$

---

## 🏗️ Estrutura de Arquivos do Novo Projeto

```
C:\Users\andre\IdeaProjects\AssistenteVendasUnitrama_github\
├── src\
│   └── br\com\a3consultec\assistentevendas\
│       ├── action\
│       │   ├── AssistenteVendasAction.java        # Entrada da Ação na Central de Vendas
│       │   └── AdicionarItemAction.java           # Pipeline CACHelper + Impostos + Financeiro
│       ├── service\
│       │   ├── AssistenteVendasService.java       # Motor de recomendações de vendas
│       │   ├── CalculoMargemTriggerService.java   # Engenharia reversa Unitrama (Preço x Margem)
│       │   └── RecalculoNotaService.java          # Totalização oficial preservando frete
│       ├── model\
│       │   ├── AssistenteResponseDTO.java
│       │   ├── SugestaoProdutoDTO.java
│       │   └── ItemMargemDTO.java
│       ├── util\
│       │   ├── AssistenteConfig.java              # Parametrização via TSIPAR
│       │   ├── ConstrutorPopUp.java
│       │   └── MessageUtils.java
│       └── popUp\
│           ├── Assistente.html                    # Drawer + Balão com inputs de Preço e Margem
│           ├── Assistente.css                     # Estilos Unitrama Corporate
│           └── Assistente.js                      # Lógica de reatividade bidirecional (Preço <-> Margem)
├── test\
│   └── br\com\a3consultec\assistentevendas\
│       ├── CalculoMargemUnitramaTest.java        # Validação matemática dos 2 sentidos
│       ├── AssistenteVendasServiceTest.java       # Teste de busca de recomendações
│       └── ConstrutorPopUpTest.java              # Validação de injeção e sigilo de custo
├── build_and_compile.py                          # Compilador e empacotador JAR (JDK 8)
├── build_and_test.py                             # Executor da suíte de testes JUnit 4.12
├── .gitignore
└── README.md
```
