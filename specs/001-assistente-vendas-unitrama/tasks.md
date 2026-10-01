# Checklist de Tarefas: Assistente de Vendas Unitrama

**Feature**: `001-assistente-vendas-unitrama`  
**Status**: PLANEJADO

---

## 📋 Matriz de Tarefas por Papel Especializado

### 1. Dados & Matemática de Margem (`Oracle Database Specialist` / `Sankhya Backend Specialist`)
- [ ] Portar e adaptar `CalculoMargemTriggerService.java` de `RecalculoIPIMargem` com suporte à leitura de contexto da nota e cálculo bidirecional (Preço $\leftrightarrow$ Margem).
- [ ] Enriquecer `SugestaoProdutoDTO` para incluir `aliquotaIpi`, `fatorOmega`, `margemSugerida`, `vlrVenda` e custo variável blindado.
- [ ] Garantir que na montagem do JSON de sugestões o campo `custoVariavel` seja nulificado (`null`) para sigilo comercial estrito.

### 2. Pipeline de Inclusão & Ação Java (`Sankhya Backend Specialist`)
- [ ] Configurar `AssistenteVendasAction.java` com identidade Unitrama e rotas `ADICIONAR_ITEM` e `OBTER_SUGESTOES`.
- [ ] Atualizar `AdicionarItemAction.java` para aceitar `VLRUNIT` customizado informado pelo vendedor, mantendo a chamada ao `CACHelper` e recálculo de impostos.
- [ ] Preservar frete (`VLRFRETE`) e integridade financeira (`refazerFinanceiro`).

### 3. Interface Flutuante Reativa (`Frontend Specialist`)
- [ ] Adaptar `Assistente.html` com branding e títulos Unitrama.
- [ ] Nos cards de sugestão, adicionar os dois campos lado a lado:
  - Input `Preço Unitário (R$)`
  - Input `Margem Alvo (%)`
- [ ] Implementar em `Assistente.js` a função reativa:
  - `asstAlterarPreco(codProd)` $\to$ recalcula `asst-margem-[codProd]`
  - `asstAlterarMargem(codProd)` $\to$ recalcula `asst-preco-[codProd]`
- [ ] Manter exclusivamente o balãozinho menor arrastável com alça de 6 pontos (`::`).
- [ ] Enviar o preço editado no payload do botão "+ Adicionar".

### 4. Qualidade & Testes Automatizados (`QA Automation Specialist`)
- [ ] Desenvolver suíte `CalculoMargemUnitramaTest.java` com testes unitários para:
  - Preço $\to$ Margem com IPI e sem IPI.
  - Margem $\to$ Preço com Desconto Especial ($F_{sim}$).
  - Casos extremos (margem negativa, margem próxima de 100%, custo zerado).
- [ ] Atualizar testes de injeção HTML/JS comprovando sigilo de custos no JSON.

### 5. Build, Git & Governança (`DevOps & Deployment Specialist`)
- [ ] Configurar `build_and_compile.py` e `build_and_test.py` no novo diretório.
- [ ] Criar `README.md` corporativo da Unitrama com badges, DER e instruções de deploy.
- [ ] Inicializar repositório local Git (`git init`), branch `main` e commit inicial.
- [ ] Criar repositório remoto no GitHub (`sankhya-action-assistente-vendas-unitrama`) via `gh repo create` e fazer push da versão `v1.0.0`.
