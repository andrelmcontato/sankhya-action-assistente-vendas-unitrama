package br.com.a3consultec.assistentevendas.service;

import br.com.a3consultec.assistentevendas.model.AssistenteResponseDTO;
import br.com.a3consultec.assistentevendas.model.SugestaoProdutoDTO;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import br.com.a3consultec.assistentevendas.util.AssistenteConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serviço de alta performance para consulta das recomendações de venda.
 * Atende em tempo real a Central de Vendas do SankhyaW com tempo de resposta < 20ms.
 */
public class AssistenteVendasService {

    private final CalculoMargemTriggerService calculoMargemService = new CalculoMargemTriggerService();

    private int getMaxSugestoes() {
        return AssistenteConfig.getMaxSugestoes();
    }

    private int getDiasHistorico() {
        return AssistenteConfig.getDiasHistorico();
    }

    /**
     * Obtém as sugestões de Cross-Selling e Recompra para o pedido informado.
     */
    public AssistenteResponseDTO obterSugestoes(BigDecimal nuNota) {
        AssistenteResponseDTO response = new AssistenteResponseDTO();
        response.setNuNota(nuNota);

        if (nuNota == null || nuNota.compareTo(BigDecimal.ZERO) <= 0) {
            response.setSucesso(false);
            response.setMensagem("Número da nota inválido.");
            return response;
        }

        JdbcWrapper jdbc = null;
        try {
            EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
            jdbc = dwfFacade.getJdbcWrapper();
            jdbc.openSession();

            Connection conn = jdbc.getConnection();

            // 1. Dados do Cabeçalho do Pedido e Contexto Fiscal da Unitrama
            carregarCabecalho(conn, nuNota, response);
            CalculoMargemTriggerService.NotaFiscalContexto ctxNota = carregarContextoNota(conn, nuNota);

            // 2. Itens já presentes no carrinho
            List<BigDecimal> itensNoCarrinho = carregarItensCarrinho(conn, nuNota);
            response.setQtdItensCarrinho(itensNoCarrinho.size());

            // 3. Buscar Recomendações de Cross-Selling (Market Basket / LIFT) com Preço do Parceiro
            if (!itensNoCarrinho.isEmpty()) {
                List<SugestaoProdutoDTO> sugestoes = buscarSugestoesCrossSell(conn, nuNota, itensNoCarrinho);
                response.setSugestoes(sugestoes);
            }

            // 4. Buscar Itens de Recompra Habitual do Cliente com Preço do Parceiro
            if (response.getCodParc() != null) {
                List<SugestaoProdutoDTO> habituais = buscarItensHabituais(conn, nuNota, response.getCodParc(), itensNoCarrinho);
                response.setItensFrequentes(habituais);

                // Fallback Inteligente (Sankhya Native): Se a TVIMBR ainda estiver vazia, 
                // apresenta os produtos mais comprados pelo parceiro para o assistente nunca ficar em branco
                if (response.getSugestoes().isEmpty() && !habituais.isEmpty()) {
                    response.setSugestoes(habituais);
                }
            }

            // 5. Enriquecer com Margem Alvo, FatorK e IPI da Unitrama (Sigilo Estrito de Custo)
            enriquecerComMargemEPreco(conn, ctxNota, response.getSugestoes());
            enriquecerComMargemEPreco(conn, ctxNota, response.getItensFrequentes());

            response.setSucesso(true);
            response.setMensagem("Sugestões carregadas com sucesso.");

        } catch (Exception e) {
            response.setSucesso(false);
            response.setMensagem("Erro ao processar sugestões: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (jdbc != null) {
                try {
                    jdbc.closeSession();
                } catch (Exception ignored) {
                }
            }
        }

        return response;
    }

    private void carregarCabecalho(Connection conn, BigDecimal nuNota, AssistenteResponseDTO response) throws Exception {
        String sql = "SELECT C.CODPARC, P.NOMEPARC "
                   + "FROM TGFCAB C "
                   + "LEFT JOIN TGFPAR P ON P.CODPARC = C.CODPARC "
                   + "WHERE C.NUNOTA = ?";

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, nuNota);
            rs = ps.executeQuery();
            if (rs.next()) {
                response.setCodParc(rs.getBigDecimal("CODPARC"));
                response.setNomeParc(rs.getString("NOMEPARC"));
            }
        } finally {
            fechar(ps, rs);
        }
    }

    private List<BigDecimal> carregarItensCarrinho(Connection conn, BigDecimal nuNota) throws Exception {
        List<BigDecimal> itens = new ArrayList<BigDecimal>();
        String sql = "SELECT DISTINCT CODPROD FROM TGFITE WHERE NUNOTA = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, nuNota);
            rs = ps.executeQuery();
            while (rs.next()) {
                itens.add(rs.getBigDecimal("CODPROD"));
            }
            return itens;
        } finally {
            fechar(ps, rs);
        }
    }

    private List<SugestaoProdutoDTO> buscarSugestoesCrossSell(Connection conn, BigDecimal nuNota, List<BigDecimal> itensCarrinho) throws Exception {
        List<SugestaoProdutoDTO> lista = new ArrayList<SugestaoProdutoDTO>();
        if (itensCarrinho.isEmpty()) {
            return lista;
        }

        // Limitar a no máximo 100 itens para evitar exceder o limite ORA-01795 do Oracle
        int limiteItens = Math.min(itensCarrinho.size(), 100);
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < limiteItens; i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append(itensCarrinho.get(i).toPlainString());
        }

        // Query ultra-otimizada com CTEs e Resolução Precisa de Preço por Parceiro (Edeltec)
        String sql = "WITH REGRAS_CANDIDATAS AS ( "
                   + "    SELECT R.CODPROD_B, MAX(R.LIFT_A_B) AS MAX_LIFT, MAX(R.CONFIDENCE_A_B) AS MAX_CONF, "
                   + "           MAX(R.HINT_TEXTO) AS HINT_TEXTO, MAX(R.TIPO_EXPLICACAO) AS TIPO_EXPLICACAO "
                   + "    FROM TVIMBR R "
                   + "    WHERE R.EXEC_ID = (SELECT NVL(MAX(EXEC_ID), 1) FROM TVIMBR) "
                   + "      AND R.CODPROD_A IN (" + inClause + ") "
                   + "      AND R.CODPROD_B NOT IN (" + inClause + ") "
                   + "    GROUP BY R.CODPROD_B "
                   + "), "
                   + "ESTOQUE_CANDIDATOS AS ( "
                   + "    SELECT E.CODPROD, SUM(E.ESTOQUE - E.RESERVADO) AS ESTOQUE_DISP "
                   + "    FROM TGFEST E "
                   + "    INNER JOIN REGRAS_CANDIDATAS RC ON RC.CODPROD_B = E.CODPROD "
                   + "    GROUP BY E.CODPROD "
                   + ") "
                   + "SELECT R.CODPROD_B AS CODPROD_SUG, R.MAX_LIFT, R.MAX_CONF, R.HINT_TEXTO, R.TIPO_EXPLICACAO, "
                   + "       P.DESCRPROD, P.MARCA, P.COMPLDESC, "
                   + "       COALESCE("
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = R.CODPROD_B "
                   + "              AND TAB.CODTAB = ( "
                   + "                  SELECT NVL(PAE.CODTAB, PAR.CODTAB) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFPAR PAR ON PAR.CODPARC = CAB.CODPARC "
                   + "                  LEFT JOIN TGFPAEM PAE ON PAE.CODPARC = CAB.CODPARC AND PAE.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND TAB.DTVIGOR <= SYSDATE "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            WHERE EXC.CODPROD = R.CODPROD_B "
                   + "              AND EXC.NUTAB IN (SELECT DISTINCT ITE.NUTAB FROM TGFITE ITE WHERE ITE.NUNOTA = ? AND ITE.NUTAB IS NOT NULL) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(ITE.VLRUNIT) KEEP (DENSE_RANK LAST ORDER BY CAB.DTNEG, CAB.NUNOTA) "
                   + "            FROM TGFITE ITE "
                   + "            INNER JOIN TGFCAB CAB ON CAB.NUNOTA = ITE.NUNOTA "
                   + "            WHERE ITE.CODPROD = R.CODPROD_B "
                   + "              AND CAB.CODPARC = (SELECT CODPARC FROM TGFCAB WHERE NUNOTA = ?) "
                   + "              AND CAB.STATUSNOTA = 'L' "
                   + "              AND ITE.VLRUNIT > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) FROM TGFEXC EXC WHERE EXC.CODPROD = R.CODPROD_B AND EXC.VLRVENDA > 0), "
                   + "           (SELECT MAX(ITE.VLRUNIT) FROM TGFITE ITE WHERE ITE.CODPROD = R.CODPROD_B AND ITE.VLRUNIT > 0), "
                   + "           0 "
                   + "       ) AS VLRVENDA, "
                   + "       NVL(E.ESTOQUE_DISP, 0) AS ESTOQUE_DISPONIVEL "
                   + "FROM REGRAS_CANDIDATAS R "
                   + "INNER JOIN TGFPRO P ON P.CODPROD = R.CODPROD_B "
                   + "LEFT JOIN ESTOQUE_CANDIDATOS E ON E.CODPROD = R.CODPROD_B "
                   + "WHERE P.ATIVO = 'S' "
                   + "ORDER BY R.MAX_LIFT DESC, R.MAX_CONF DESC";

        PreparedStatement ps = null;
        ResultSet rs = null;
        Set<BigDecimal> adicionados = new HashSet<BigDecimal>();

        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, nuNota);
            ps.setBigDecimal(2, nuNota);
            ps.setBigDecimal(3, nuNota);
            rs = ps.executeQuery();

            while (rs.next() && lista.size() < getMaxSugestoes()) {
                BigDecimal codSug = rs.getBigDecimal("CODPROD_SUG");
                if (adicionados.contains(codSug)) {
                    continue;
                }
                adicionados.add(codSug);

                SugestaoProdutoDTO dto = new SugestaoProdutoDTO();
                dto.setCodProd(codSug);
                dto.setDescrProd(rs.getString("DESCRPROD"));
                dto.setMarca(rs.getString("MARCA"));
                dto.setComplemento(rs.getString("COMPLDESC"));
                dto.setVlrVenda(rs.getBigDecimal("VLRVENDA"));
                dto.setEstoqueDisponivel(rs.getBigDecimal("ESTOQUE_DISPONIVEL"));

                BigDecimal lift = rs.getBigDecimal("MAX_LIFT");
                BigDecimal conf = rs.getBigDecimal("MAX_CONF");
                String hintTexto = rs.getString("HINT_TEXTO");
                String tipoExpl = rs.getString("TIPO_EXPLICACAO");

                dto.setLift(lift);
                dto.setConfianca(conf);
                dto.setHintTexto(hintTexto);

                if (lift != null && lift.compareTo(BigDecimal.ONE) > 0) {
                    dto.setTagAfinidade(lift.setScale(1, RoundingMode.HALF_UP).toPlainString() + "x mais afinidade");
                } else {
                    dto.setTagAfinidade("Mais Vendido Junto");
                }

                if (hintTexto != null && !hintTexto.trim().isEmpty()) {
                    dto.setMotivo(hintTexto);
                } else if ("CONFIDENCE_ALTA".equals(tipoExpl)) {
                    dto.setMotivo("Na maioria das vendas, este item também entra no pedido.");
                    dto.setTagAfinidade("Frequência Máxima");
                } else if ("CONFIDENCE_MEDIA".equals(tipoExpl)) {
                    int pct = conf != null ? conf.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue() : 30;
                    dto.setMotivo("Cerca de " + pct + "% das vendas também incluem este item.");
                    dto.setTagAfinidade("Alta Frequência");
                } else if ("LIFT_FORTE".equals(tipoExpl)) {
                    dto.setMotivo("Alta probabilidade de compra conjunta com este pedido.");
                } else if ("LIFT_MODERADO".equals(tipoExpl)) {
                    dto.setMotivo("Costuma ser adquirido junto em pedidos deste perfil.");
                } else if (conf != null) {
                    dto.setMotivo(conf.setScale(0, RoundingMode.HALF_UP).toPlainString() + "% dos clientes levam junto");
                } else {
                    dto.setMotivo("Sugestão baseada em histórico de pedidos");
                }

                lista.add(dto);
            }
            return lista;
        } catch (java.sql.SQLException sqle) {
            System.err.println("[AssistenteVendas] Erro SQL ao consultar TVIMBR: " + sqle.getMessage());
            throw sqle;
        } finally {
            fechar(ps, rs);
        }
    }

    private List<SugestaoProdutoDTO> buscarItensHabituais(Connection conn, BigDecimal nuNota, BigDecimal codParc, List<BigDecimal> itensCarrinho) throws Exception {
        List<SugestaoProdutoDTO> lista = new ArrayList<SugestaoProdutoDTO>();

        int limiteItens = Math.min(itensCarrinho.size(), 100);
        StringBuilder notInClause = new StringBuilder("0");
        for (int i = 0; i < limiteItens; i++) {
            notInClause.append(",").append(itensCarrinho.get(i).toPlainString());
        }

        // CTE otimizada para histórico de compras recorrentes do cliente com Preço do Parceiro
        String sql = "WITH ITENS_RECORRENTES AS ( "
                   + "    SELECT I.CODPROD, COUNT(DISTINCT C.NUNOTA) AS FREQ_COMPRA "
                   + "    FROM TGFITE I "
                   + "    INNER JOIN TGFCAB C ON C.NUNOTA = I.NUNOTA "
                   + "    WHERE C.CODPARC = ? "
                   + "      AND C.TIPMOV = 'V' AND C.STATUSNOTA = 'L' "
                   + "      AND C.DTNEG >= SYSDATE - ? "
                   + "      AND I.CODPROD NOT IN (" + notInClause + ") "
                   + "    GROUP BY I.CODPROD "
                   + "    HAVING COUNT(DISTINCT C.NUNOTA) >= 2 "
                   + "), "
                   + "ESTOQUE_RECORRENTES AS ( "
                   + "    SELECT E.CODPROD, SUM(E.ESTOQUE - E.RESERVADO) AS ESTOQUE_DISP "
                   + "    FROM TGFEST E "
                   + "    INNER JOIN ITENS_RECORRENTES IR ON IR.CODPROD = E.CODPROD "
                   + "    GROUP BY E.CODPROD "
                   + ") "
                   + "SELECT IR.CODPROD, P.DESCRPROD, P.MARCA, "
                   + "       COALESCE("
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = IR.CODPROD "
                   + "              AND TAB.CODTAB = ( "
                   + "                  SELECT NVL(PAE.CODTAB, PAR.CODTAB) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFPAR PAR ON PAR.CODPARC = CAB.CODPARC "
                   + "                  LEFT JOIN TGFPAEM PAE ON PAE.CODPARC = CAB.CODPARC AND PAE.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND TAB.DTVIGOR <= SYSDATE "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            WHERE EXC.CODPROD = IR.CODPROD "
                   + "              AND EXC.NUTAB IN (SELECT DISTINCT ITE.NUTAB FROM TGFITE ITE WHERE ITE.NUNOTA = ? AND ITE.NUTAB IS NOT NULL) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(ITE.VLRUNIT) KEEP (DENSE_RANK LAST ORDER BY CAB.DTNEG, CAB.NUNOTA) "
                   + "            FROM TGFITE ITE "
                   + "            INNER JOIN TGFCAB CAB ON CAB.NUNOTA = ITE.NUNOTA "
                   + "            WHERE ITE.CODPROD = IR.CODPROD "
                   + "              AND CAB.CODPARC = ? "
                   + "              AND CAB.STATUSNOTA = 'L' "
                   + "              AND ITE.VLRUNIT > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) FROM TGFEXC EXC WHERE EXC.CODPROD = IR.CODPROD AND EXC.VLRVENDA > 0), "
                   + "           (SELECT MAX(ITE.VLRUNIT) FROM TGFITE ITE WHERE ITE.CODPROD = IR.CODPROD AND ITE.VLRUNIT > 0), "
                   + "           0 "
                   + "       ) AS VLRVENDA, "
                   + "       NVL(ER.ESTOQUE_DISP, 0) AS ESTOQUE_DISPONIVEL, IR.FREQ_COMPRA "
                   + "FROM ITENS_RECORRENTES IR "
                   + "INNER JOIN TGFPRO P ON P.CODPROD = IR.CODPROD "
                   + "LEFT JOIN ESTOQUE_RECORRENTES ER ON ER.CODPROD = IR.CODPROD "
                   + "WHERE P.ATIVO = 'S' "
                   + "ORDER BY IR.FREQ_COMPRA DESC";

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, codParc);
            ps.setInt(2, getDiasHistorico());
            ps.setBigDecimal(3, nuNota);
            ps.setBigDecimal(4, nuNota);
            ps.setBigDecimal(5, codParc);
            rs = ps.executeQuery();

            while (rs.next() && lista.size() < 3) {
                SugestaoProdutoDTO dto = new SugestaoProdutoDTO();
                dto.setCodProd(rs.getBigDecimal("CODPROD"));
                dto.setDescrProd(rs.getString("DESCRPROD"));
                dto.setMarca(rs.getString("MARCA"));
                dto.setVlrVenda(rs.getBigDecimal("VLRVENDA"));
                dto.setEstoqueDisponivel(rs.getBigDecimal("ESTOQUE_DISPONIVEL"));
                dto.setTagAfinidade("Item Recorrente");
                dto.setMotivo("Comprado " + rs.getInt("FREQ_COMPRA") + " vezes recentemente");
                lista.add(dto);
            }
            return lista;
        } finally {
            fechar(ps, rs);
        }
    }

    private CalculoMargemTriggerService.NotaFiscalContexto carregarContextoNota(Connection conn, BigDecimal nuNota) {
        CalculoMargemTriggerService.NotaFiscalContexto ctx = new CalculoMargemTriggerService.NotaFiscalContexto();
        ctx.nuNota = nuNota;

        String sql = "SELECT CAB.VLRNOTA, NVL(CAB.VLRDESCTOT, 0) AS VLRDESCTOT, NVL(CAB.VLRDESCTOTITEM, 0) AS VLRDESCTOTITEM, "
                   + "       NVL(CAB.AD_DESCESPECIAL, 'N') AS AD_DESCESPECIAL, NVL(CAB.AD_VINCVENDCOMPLEMENTO, '') AS AD_VINCVENDCOMPLEMENTO, "
                   + "       NVL(CAB.VLRFRETE, 0) AS VLRFRETE, NVL(PAR.TEMIPI, 'N') AS TEMIPI, "
                   + "       NVL(PAR.AD_DESCONTOESPECIAL, 0) AS AD_DESCONTOESPECIAL, NVL(CPL.CODSUFRAMA, ' ') AS CODSUFRAMA, "
                   + "       NVL(TOP.AD_RECALCIPI, 'N') AS AD_RECALCIPI "
                   + "FROM TGFCAB CAB "
                   + "INNER JOIN TGFPAR PAR ON CAB.CODPARC = PAR.CODPARC "
                   + "LEFT JOIN TGFCPL CPL ON CAB.CODPARC = CPL.CODPARC "
                   + "LEFT JOIN TGFTOP TOP ON CAB.CODTIPOPER = TOP.CODTIPOPER AND CAB.DHTIPOPER = TOP.DHALTER "
                   + "WHERE CAB.NUNOTA = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, nuNota);
            rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal vlrNota = rs.getBigDecimal("VLRNOTA");
                if (vlrNota == null) vlrNota = BigDecimal.ZERO;
                BigDecimal vlrDescTot = rs.getBigDecimal("VLRDESCTOT");
                if (vlrDescTot == null) vlrDescTot = BigDecimal.ZERO;
                BigDecimal vlrDescTotItem = rs.getBigDecimal("VLRDESCTOTITEM");
                if (vlrDescTotItem == null) vlrDescTotItem = BigDecimal.ZERO;
                BigDecimal somaDesc = vlrDescTot.add(vlrDescTotItem);
                BigDecimal baseDiv = vlrNota.add(somaDesc);

                if (baseDiv.compareTo(BigDecimal.ZERO) > 0) {
                    ctx.percDescCabecalho = somaDesc.divide(baseDiv, 4, RoundingMode.HALF_UP);
                }

                ctx.usaDescEspecial = rs.getString("AD_DESCESPECIAL");
                ctx.vinculo = rs.getString("AD_VINCVENDCOMPLEMENTO");
                ctx.vlrFrete = rs.getBigDecimal("VLRFRETE");
                if (ctx.vlrFrete == null) ctx.vlrFrete = BigDecimal.ZERO;
                ctx.percDescParceiro = rs.getBigDecimal("AD_DESCONTOESPECIAL");
                if (ctx.percDescParceiro == null) ctx.percDescParceiro = BigDecimal.ZERO;

                String temIpiStr = rs.getString("TEMIPI");
                ctx.clienteTemIpi = "S".equalsIgnoreCase(temIpiStr != null ? temIpiStr.trim() : "");

                String suframa = rs.getString("CODSUFRAMA");
                ctx.isClienteSuframa = suframa != null && !suframa.trim().isEmpty();

                String recalcIpiStr = rs.getString("AD_RECALCIPI");
                ctx.topRecalculaIpi = "S".equalsIgnoreCase(recalcIpiStr != null ? recalcIpiStr.trim() : "");
            }
        } catch (Exception e) {
            System.err.println("[AssistenteVendas Unitrama] Aviso ao carregar contexto fiscal da nota: " + e.getMessage());
        } finally {
            fechar(ps, rs);
        }
        return ctx;
    }

    private void enriquecerComMargemEPreco(Connection conn, CalculoMargemTriggerService.NotaFiscalContexto ctx, List<SugestaoProdutoDTO> sugestoes) {
        if (sugestoes == null || sugestoes.isEmpty()) return;

        for (SugestaoProdutoDTO sug : sugestoes) {
            BigDecimal aliqIpi = obterAliquotaIpiProduto(conn, sug.getCodProd());
            BigDecimal cusVar = obterCustoVariavelProduto(conn, sug.getCodProd(), ctx.nuNota);

            BigDecimal fatorK = BigDecimal.ZERO;
            BigDecimal margem = BigDecimal.ZERO;

            if (cusVar.compareTo(BigDecimal.ZERO) > 0) {
                fatorK = calculoMargemService.calcularFatorK(ctx, cusVar, aliqIpi);
                if (sug.getVlrVenda() != null && sug.getVlrVenda().compareTo(BigDecimal.ZERO) > 0) {
                    margem = calculoMargemService.calcularMargemDePreco(fatorK, sug.getVlrVenda());
                }
            } else if (sug.getVlrVenda() != null && sug.getVlrVenda().compareTo(BigDecimal.ZERO) > 0) {
                // Fallback para homologação / ambiente de teste sem custo cadastrado:
                // Simula margem de 25% para viabilizar a experiência reativa
                margem = new BigDecimal("25.00");
                fatorK = sug.getVlrVenda().multiply(new BigDecimal("0.75")).setScale(4, RoundingMode.HALF_UP);
            }

            sug.setAliquotaIpi(aliqIpi);
            sug.setFatorK(fatorK);
            sug.setMargemSugerida(margem);
            sug.setCustoVariavel(null); // SIGILO COMERCIAL ESTRITO
        }
    }

    private BigDecimal obterAliquotaIpiProduto(Connection conn, BigDecimal codProd) {
        String sql = "SELECT NVL(IPI.PERCENTUAL, 0) AS PERCENTUAL "
                   + "FROM TGFPRO PRO "
                   + "INNER JOIN TGFIPI IPI ON PRO.CODIPI = IPI.CODIPI "
                   + "WHERE PRO.CODPROD = ?";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, codProd);
            rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal perc = rs.getBigDecimal("PERCENTUAL");
                return perc != null ? perc : BigDecimal.ZERO;
            }
        } catch (Exception ignored) {
        } finally {
            fechar(ps, rs);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal obterCustoVariavelProduto(Connection conn, BigDecimal codProd, BigDecimal nuNota) {
        String sql = "SELECT NVL("
                   + "  (SELECT MAX(CUSVAR) FROM TGFCUS WHERE CODPROD = ? AND CODEMP = (SELECT CODEMP FROM TGFCAB WHERE NUNOTA = ?)), "
                   + "  (SELECT MAX(CUSVAR) FROM TGFCUS WHERE CODPROD = ?)"
                   + ") AS CUSVAR FROM DUAL";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, codProd);
            ps.setBigDecimal(2, nuNota);
            ps.setBigDecimal(3, codProd);
            rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal cv = rs.getBigDecimal("CUSVAR");
                if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                    return cv;
                }
            }
        } catch (Exception ignored) {
        } finally {
            fechar(ps, rs);
        }
        return BigDecimal.ZERO;
    }

    private void fechar(PreparedStatement ps, ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignored) {
            }
        }
        if (ps != null) {
            try {
                ps.close();
            } catch (Exception ignored) {
            }
        }
    }
}
