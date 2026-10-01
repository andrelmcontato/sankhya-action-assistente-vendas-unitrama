# Especificação Funcional (SDD): Assistente de Vendas Unitrama com Ajuste de Preço e Margem

**Feature**: `001-assistente-vendas-unitrama`  
**Cliente**: Unitrama  
**Tipo**: Botão de Ação Java (`AcaoRotinaJava`) + Extensão Frontend Flutuante (HTML5 / AngularJS)  
**Versão Inicial**: `v1.0.0` (Unitrama Edition)

---

## 1. Visão Geral do Produto

O **Assistente de Vendas Unitrama** é um motor corporativo de recomendação de produtos e inteligência comercial acoplado à Central de Vendas do SankhyaW (`CentralNotas`). 

Diferente do modelo tradicional onde o preço sugerido é estático, a versão **Unitrama** capacita o vendedor a **negociar o Preço Unitário (`VLRUNIT`) e a Margem Alvo (`AD_MARGEMITEM`) diretamente no card de cada sugestão** antes da inclusão no pedido, com:
1. **Reatividade Bidirecional em Tempo Real**: Digitar o preço recalcula a margem correspondente; digitar a margem recalcula o preço unitário necessário.
2. **Sigilo Comercial Estrito**: O custo do produto (`AD_CUSVARIAVEL` / `CUSVAR`) **nunca é exibido ou trafegado para o frontend**, preservando o segredo industrial e comercial.
3. **Harmonia Fiscal com o Listener de IPI Embutido**: Integração com as regras fiscais do `RecalculoIpiEmbutidoListener` e a trigger de margem `TRG_INC_UPD_TGFITE_MRG.sql` da Unitrama.
4. **Interface Minimalista e Não-Bloqueante**: Balãozinho flutuante arrastável (*Pill* "Mais oportunidades!") e painel lateral deslizante com identidade visual corporativa da Unitrama.

---

## 2. Cenários & Critérios de Aceite (Given / When / Then)

### Cenário 1: Reatividade Bidirecional (Preço $\to$ Margem)
- **Dado que** o vendedor abriu o Assistente de Vendas em um pedido da Unitrama
- **E** o card do produto sugerido exibe Quantidade `1`, Preço Unitário `R$ 100,00` e Margem Alvo `25.00%`
- **Quando** o vendedor altera o campo Preço Unitário para `R$ 120,00`
- **Então** o campo Margem Alvo (%) deve ser recalculado imediatamente no card refletindo a nova margem proporcionada pelo preço de `R$ 120,00`, considerando os descontos da nota e alíquota de IPI.

### Cenário 2: Reatividade Bidirecional (Margem $\to$ Preço)
- **Dado que** o vendedor está negociando um item sugerido
- **Quando** o vendedor digita a Margem Alvo desejada de `30.00%`
- **Então** o campo Preço Unitário (R$) é recalculado instantaneamente no card com o valor bruto de venda necessário para atingir os `30%` de margem.

### Cenário 3: Inclusão Atômica com Preço Personalizado
- **Dado que** o vendedor ajustou a Quantidade para `5` e o Preço para `R$ 115,00` (margem correspondente calculada)
- **Quando** o vendedor clica no botão "+ Adicionar"
- **Então** o item deve ser incluído na `TGFITE` do pedido com `QTDNEG = 5` e `VLRUNIT` correspondente
- **E** o `RecalculoIpiEmbutidoListener` do ERP deve processar a dedução do IPI se o parceiro tiver IPI e não for Suframa
- **E** a trigger `TRG_INC_UPD_TGFITE_MRG` deve gravar a margem calculada e comissões
- **E** a nota deve ser totalizada preservando frete (`VLRFRETE`) e o financeiro regenerado
- **E** a grade de itens na Central de Vendas deve ser atualizada em tempo real sem fechar o painel.

### Cenário 4: Sigilo Comercial de Custo
- **Dado que** o payload de dados do assistente é transmitido ao navegador
- **Quando** o código do frontend ou o DOM for inspecionado
- **Então** nenhum atributo contendo custo variável, custo médio ou custo de reposição deve estar presente no JSON de sugestões ou nós HTML.

### Cenário 5: Interface Flutuante e Arrasto Livre
- **Dado que** o assistente é disparado na Central de Vendas
- **Então** deve exibir apenas o widget menor arrastável com alça de 6 pontinhos (`::`)
- **E** ao ser arrastado pelo usuário, deve fixar na nova coordenada sem sobrepor dados críticos do ERP.
