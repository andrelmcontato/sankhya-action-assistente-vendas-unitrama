# Assistente de Vendas Unitrama (Extensão Sankhya ERP)

Extensão Java e Web nativa para a Central de Vendas do SankhyaW, desenvolvida para potencializar as vendas consultivas e o cross-selling da **Unitrama**. Apresenta sugestões contextualizadas com base em mineração de dados (*Market Basket Analysis* / LIFT) e histórico de recompra do cliente, incorporando negociação interativa em tempo real com recálculo bidirecional entre **Preço Unitário (R$)** e **Margem Alvo (%)**.

---

## 🚀 Principais Funcionalidades

1. **Recomendações Contextuais de Vendas**:
   - Análise de afinidade estatística (*Market Basket Analysis*) sobre os itens presentes no pedido atual.
   - Histórico de recorrência do parceiro (recompra habitual baseada nos últimos pedidos).
   - Indicação de disponibilidade de estoque físico imediato (`TGFEST`).

2. **Negociação Interativa em Tempo Real (Preço $\leftrightarrow$ Margem)**:
   - Permite ao vendedor ajustar o **Preço Unitário (R$)** ou a **Margem Alvo (%)** diretamente no card do produto sugerido antes da inclusão.
   - Recálculo reativo bidirecional instantâneo via constante fatorada ($K$), garantindo conformidade matemática com a política comercial da Unitrama.

3. **Integração Nativa com o Ecossistema Unitrama (Zero Duplicação)**:
   - **IPI Embutido**: Opera em total harmonia com o `RecalculoIpiEmbutidoListener` ativo em produção na Unitrama. O assistente entrega o preço bruto negociado (`VLRUNIT`), permitindo que o listener nativo deduza o IPI e preencha `BASEIPI`/`VLRIPI` automaticamente no `beforeInsert`.
   - **Fórmula de Margem**: Replica com 100% de fidelidade a matemática da trigger `TRG_INC_UPD_TGFITE_MRG.sql`, considerando fator de desconto simulado ($F_{sim}$), descontos de cabeçalho (`VLRDESCTOT`, `VLRDESCTOTITEM`), Suframa (`CODSUFRAMA`) e regras de incidência de IPI.

4. **Sigilo Comercial Estrito**:
   - O custo variável (`CUSVAR`) do produto é utilizado exclusivamente no servidor para determinar a constante $K$ e é sanitizado (`null`) antes do envio ao frontend. O custo nunca é exposto na tela, no DOM ou no tráfego JSON.

5. **Interface Flutuante Não-Bloqueante**:
   - Widget flutuante em formato de pílula arrastável (*Opportunity Pill*) sobre a Central de Vendas, com alça de 6 pontinhos (`::`) e *drag & drop* livre.
   - Painel lateral deslizante (*Slide-over Drawer*) para conferência de itens recomendados, ajuste de quantidade, edição de preços e inclusão atômica no pedido.

---

## 📐 Arquitetura Matemática (Preço e Margem)

A relação entre o Preço Unitário ($P$) e a Margem Alvo ($m$) é governada pela dedução formal da trigger `TRG_INC_UPD_TGFITE_MRG.sql`:

$$P = \frac{C_{var}}{(1 - m) \cdot \Omega} \iff P = \frac{K}{1 - (m / 100)}$$

Onde a constante invariante de precificação $K$ é definida por:

$$K = \frac{C_{var}}{\Omega}$$

E o fator de acoplamento fiscal $\Omega$ é dado por:

$$\Omega = \frac{F_{sim} \cdot (1 - \text{percDesc}) + (fatorIpi - 1)}{fatorIpi}$$

Com essa parametrização, o frontend realiza a conversão bidirecional em $O(1)$:
- **Ao alterar o Preço ($P$)**:
  $$m = \left( 1 - \frac{K}{P} \right) \times 100$$
- **Ao alterar a Margem ($m$)**:
  $$P = \frac{K}{1 - (m / 100)}$$

---

## 📂 Estrutura do Projeto

```
AssistenteVendasUnitrama_github/
├── src/
│   ├── br/com/a3consultec/assistentevendas/
│   │   ├── action/
│   │   │   ├── AdicionarItemAction.java        # Inclusão atômica via CACHelper (VLRUNIT customizado)
│   │   │   └── AssistenteVendasAction.java      # Botão de Ação Java / Dispatcher de Operações
│   │   ├── event/
│   │   │   └── ItemNotaMineradorEvento.java     # Listener de mineração incremental em TGFITE
│   │   ├── miner/
│   │   │   └── MarketBasketMinerJob.java        # Rotina de mineração em lote (TVIMBR)
│   │   ├── model/
│   │   │   ├── AssistenteResponseDTO.java       # Resposta estruturada do assistente
│   │   │   └── SugestaoProdutoDTO.java          # DTO com margemSugerida, fatorK e aliquotaIpi
│   │   ├── service/
│   │   │   ├── AssistenteVendasService.java     # Orquestrador de busca e enriquecimento
│   │   │   └── CalculoMargemTriggerService.java # Cálculo de Fsim, Omega, K e bidirecionalidade
│   │   └── util/
│   │       ├── AssistenteConfig.java            # Parâmetros e limites operacionais
│   │       ├── ConstrutorPopUp.java             # Sanitização e montagem do popup AngularJS
│   │       └── MessageUtils.java                # Resposta via ServiceContext
│   └── popUp/
│       ├── Assistente.css                       # Estilos nativos SankhyaW e cards com inputs
│       ├── Assistente.html                      # Layout do drawer e pill arrastável
│       └── Assistente.js                        # Controladora reativa DOM/AngularJS
├── test/                                        # Suíte de testes unitários (JUnit 4.12)
├── build_and_compile.py                         # Compilação JDK 8 e geração do JAR
├── build_and_test.py                            # Execução automatizada da suíte de testes
└── specs/                                       # Especificação técnica (SDD / Plan / Tasks)
```

---

## 🛠️ Requisitos e Build

- **Java JDK**: 1.8 (Java 8 compilado com `-source 1.8 -target 1.8`)
- **Bibliotecas Sankhya**: Fornecidas pelo WildFly (`C:\sk-java\Binários para Projetos` ou diretório do servidor)
- **JUnit**: 4.12 com Hamcrest Core 1.3

### Executar Testes Unitários
```bash
python build_and_test.py
```

### Compilar e Gerar Pacote JAR
```bash
python build_and_compile.py
```
O artefato final será gerado em:
`out/artifacts/AssistenteVendasUnitrama_jar/AssistenteVendasUnitrama.jar`

---

## ⚙️ Configuração no SankhyaW

### 1. Botão de Ação (Central de Vendas)
- **Módulo**: Comercial > Central de Vendas
- **Tabela / Instância**: `CabecalhoNota` (`TGFCAB`)
- **Tipo de Ação**: Rotina Java
- **Classe Java**: `br.com.a3consultec.assistentevendas.action.AssistenteVendasAction`
- **Descrição**: Assistente de Vendas Unitrama
- **Exibição**: Barra de ferramentas da Central de Vendas

### 2. Evento de Tabela (Opcional - Mineração Contínua)
- **Tabela**: `TGFITE` (Item da Nota)
- **Tipo**: Java
- **Classe**: `br.com.a3consultec.assistentevendas.event.ItemNotaMineradorEvento`
- **Momentos**: Depois de Inserir, Depois de Atualizar, Depois de Excluir

---

## 📄 Licença e Confidencialidade

Desenvolvido exclusivamente para o ecossistema ERP Sankhya da **Unitrama**. Todos os direitos reservados.
