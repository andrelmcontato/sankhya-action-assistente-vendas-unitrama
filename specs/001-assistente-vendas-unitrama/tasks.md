# Checklist de Tarefas: Assistente de Vendas Unitrama

**Feature**: `001-assistente-vendas-unitrama`  
**Status**: CONCLUÍDO

---

## 📋 Matriz de Tarefas por Papel Especializado

### 1. Dados & Matemática de Margem (`Oracle Database Specialist` / `Sankhya Backend Specialist`)
- [x] Portar e adaptar `CalculoMargemTriggerService.java` de `RecalculoIPIMargem` com suporte à leitura de contexto da nota e cálculo bidirecional (Preço $\leftrightarrow$ Margem).
- [x] Enriquecer `SugestaoProdutoDTO` para incluir `aliquotaIpi`, `fatorOmega`, `margemSugerida`, `vlrVenda` e custo variável blindado.
- [x] Garantir que na montagem do JSON de sugestões o campo `custoVariavel` seja nulificado (`null`) para sigilo comercial estrito.

### 2. Pipeline de Inclusão & Ação Java (`Sankhya Backend Specialist`)
- [x] Configurar `AssistenteVendasAction.java` com identidade Unitrama e rotas `ADICIONAR_ITEM` e `OBTER_SUGESTOES`.
- [x] Atualizar `AdicionarItemAction.java` para aceitar `VLRUNIT` customizado informado pelo vendedor, mantendo a chamada ao `CACHelper` e recálculo de impostos.
- [x] Preservar frete (`VLRFRETE`) e integridade financeira (`refazerFinanceiro`).

### 3. Interface Flutuante Reativa (`Frontend Specialist`)
- [x] Adaptar `Assistente.html` com branding e títulos Unitrama.
- [x] Nos cards de sugestão, adicionar os dois campos lado a lado:
  - Input `Preço Unitário (R$)`
  - Input `Margem Alvo (%)`
- [x] Implementar em `Assistente.js` a função reativa:
  - `asstAlterarPreco(codProd)` $\to$ recalcula `asst-margem-[codProd]`
  - `asstAlterarMargem(codProd)` $\to$ recalcula `asst-preco-[codProd]`
- [x] Manter exclusivamente o balãozinho menor arrastável com alça de 6 pontos (`::`).
- [x] Enviar o preço editado no payload do botão "+ Adicionar".

### 4. Qualidade & Testes Automatizados (`QA Automation Specialist`)
- [x] Desenvolver suíte `CalculoMargemTriggerServiceTest.java` com testes unitários para:
  - Preço $\to$ Margem com IPI e sem IPI.
  - Margem $\to$ Preço com Desconto Especial ($F_{sim}$).
  - Casos extremos (margem negativa, margem próxima de 100%, custo zerado).
- [x] Atualizar testes comprovando sigilo de custos no JSON.

### 5. Build, Git & Governança (`DevOps & Deployment Specialist`)
- [x] Configurar `build_and_compile.py` e `build_and_test.py` no novo diretório.
- [x] Criar `README.md` corporativo da Unitrama com arquitetura matemática e instruções de deploy.
- [x] Inicializar repositório local Git (`git init`), branch `main` e commit inicial.
- [x] Criar repositório remoto no GitHub (`sankhya-action-assistente-vendas-unitrama`) via `gh repo create` e fazer push da versão `v1.0.0` com release e artefato JAR.
