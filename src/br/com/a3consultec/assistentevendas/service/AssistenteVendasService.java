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
 * Modelo Unitrama: Preço de tabela e custos resolvidos por Empresa (CODEMP).
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

            // 3. Buscar Recomendações de Cross-Selling com Preço por Empresa (Unitrama)
            if (!itensNoCarrinho.isEmpty()) {
                List<SugestaoProdutoDTO> sugestoes = buscarSugestoesCrossSell(conn, nuNota, itensNoCarrinho);
                response.setSugestoes(sugestoes);
            }

            // 4. Buscar Itens de Recompra Habitual do Cliente com Preço por Empresa (Unitrama)
            if (response.getCodParc() != null) {
                List<SugestaoProdutoDTO> habituais = buscarItensHabituais(conn, nuNota, response.getCodParc(), itensNoCarrinho);
                response.setItensFrequentes(habituais);

                // Fallback Inteligente (Sankhya Native): Se a TVIMBR ainda estiver vazia, 
                // apresenta os produtos mais comprados pelo parceiro para o assistente nunca ficar em branco
                if (response.getSugestoes().isEmpty() && !habituais.isEmpty()) {
                    response.setSugestoes(habituais);
                }
            }

            // 5. Enriquecer com Margem Real da Tabela, FatorK e IPI da Unitrama (Sigilo Estrito de Custo)
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
        String sql = "SELECT C.CODPARC, P.NOMEPARC, C.CODEMP "
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

        int limiteItens = Math.min(itensCarrinho.size(), 100);
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < limiteItens; i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append(itensCarrinho.get(i).toPlainString());
        }

        // Query ultra-otimizada com CTEs e Resolução Precisa de Preço por Empresa (Unitrama)
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
                   + "            WHERE EXC.CODPROD = R.CODPROD_B "
                   + "              AND EXC.NUTAB IN (SELECT DISTINCT ITE.NUTAB FROM TGFITE ITE WHERE ITE.NUNOTA = ? AND ITE.NUTAB IS NOT NULL) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = R.CODPROD_B "
                   + "              AND TAB.CODTAB IN ( "
                   + "                  SELECT NVL(EMP.CODTAB, EMP.CODTABCALC) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFEMP EMP ON EMP.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND TAB.DTVIGOR <= SYSDATE "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = R.CODPROD_B "
                   + "              AND TAB.CODTAB IN ( "
                   + "                  SELECT NVL(EMP.CODTAB, EMP.CODTABCALC) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFEMP EMP ON EMP.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(ITE.VLRUNIT) KEEP (DENSE_RANK LAST ORDER BY CAB.DTNEG, CAB.NUNOTA) "
                   + "            FROM TGFITE ITE "
                   + "            INNER JOIN TGFCAB CAB ON CAB.NUNOTA = ITE.NUNOTA "
                   + "            WHERE ITE.CODPROD = R.CODPROD_B "
                   + "              AND CAB.CODEMP = (SELECT CODEMP FROM TGFCAB WHERE NUNOTA = ?) "
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
            ps.setBigDecimal(4, nuNota);
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

        // CTE otimizada para histórico de compras recorrentes do cliente com Preço por Empresa (Unitrama)
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
                   + "            WHERE EXC.CODPROD = IR.CODPROD "
                   + "              AND EXC.NUTAB IN (SELECT DISTINCT ITE.NUTAB FROM TGFITE ITE WHERE ITE.NUNOTA = ? AND ITE.NUTAB IS NOT NULL) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = IR.CODPROD "
                   + "              AND TAB.CODTAB IN ( "
                   + "                  SELECT NVL(EMP.CODTAB, EMP.CODTABCALC) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFEMP EMP ON EMP.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND TAB.DTVIGOR <= SYSDATE "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(EXC.VLRVENDA) "
                   + "            FROM TGFEXC EXC "
                   + "            INNER JOIN TGFTAB TAB ON TAB.NUTAB = EXC.NUTAB "
                   + "            WHERE EXC.CODPROD = IR.CODPROD "
                   + "              AND TAB.CODTAB IN ( "
                   + "                  SELECT NVL(EMP.CODTAB, EMP.CODTABCALC) "
                   + "                  FROM TGFCAB CAB "
                   + "                  INNER JOIN TGFEMP EMP ON EMP.CODEMP = CAB.CODEMP "
                   + "                  WHERE CAB.NUNOTA = ? "
                   + "              ) "
                   + "              AND EXC.VLRVENDA > 0 "
                   + "           ), "
                   + "           (SELECT MAX(ITE.VLRUNIT) KEEP (DENSE_RANK LAST ORDER BY CAB.DTNEG, CAB.NUNOTA) "
                   + "            FROM TGFITE ITE "
                   + "            INNER JOIN TGFCAB CAB ON CAB.NUNOTA = ITE.NUNOTA "
                   + "            WHERE ITE.CODPROD = IR.CODPROD "
                   + "              AND CAB.CODEMP = (SELECT CODEMP FROM TGFCAB WHERE NUNOTA = ?) "
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
            ps.setBigDecimal(5, nuNota);
            ps.setBigDecimal(6, nuNota);
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

        String sql = "SELECT CAB.CODEMP, CAB.DTNEG, CAB.VLRNOTA, NVL(CAB.VLRIPI, 0) AS VLRIPI_CAB, "
                   + "       NVL(CAB.VLRDESCTOT, 0) AS VLRDESCTOT, NVL(CAB.VLRDESCTOTITEM, 0) AS VLRDESCTOTITEM, "
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
                BigDecimal codEmp = rs.getBigDecimal("CODEMP");
                if (codEmp != null) ctx.codEmp = codEmp;
                ctx.dtNeg = rs.getTimestamp("DTNEG");

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

                BigDecimal vlrIpiCab = rs.getBigDecimal("VLRIPI_CAB");
                String temIpiStr = rs.getString("TEMIPI");
                boolean temIpiParc = "S".equalsIgnoreCase(temIpiStr != null ? temIpiStr.trim() : "");
                boolean temIpiNota = vlrIpiCab != null && vlrIpiCab.compareTo(BigDecimal.ZERO) > 0;
                ctx.clienteTemIpi = temIpiParc || temIpiNota;

                String suframa = rs.getString("CODSUFRAMA");
                ctx.isClienteSuframa = suframa != null && !suframa.trim().isEmpty();

                String recalcIpiStr = rs.getString("AD_RECALCIPI");
                ctx.topRecalculaIpi = "S".equalsIgnoreCase(recalcIpiStr != null ? recalcIpiStr.trim() : "");

                if (!ctx.clienteTemIpi && !ctx.topRecalculaIpi) {
                    String sqlIpiIte = "SELECT 1 FROM TGFITE WHERE NUNOTA = ? AND VLRIPI > 0 AND ROWNUM = 1";
                    PreparedStatement psIpi = null;
                    ResultSet rsIpi = null;
                    try {
                        psIpi = conn.prepareStatement(sqlIpiIte);
                        psIpi.setBigDecimal(1, nuNota);
                        rsIpi = psIpi.executeQuery();
                        if (rsIpi.next()) {
                            ctx.clienteTemIpi = true;
                        }
                    } catch (Exception ignored) {
                    } finally {
                        fechar(psIpi, rsIpi);
                    }
                }
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
            BigDecimal codLocal = obterCodLocalProduto(conn, sug.getCodProd(), ctx.codEmp, ctx.nuNota);
            BigDecimal cusVar = obterCustoVariavelProduto(conn, sug.getCodProd(), ctx.codEmp, codLocal, ctx.dtNeg);

            BigDecimal fatorK = BigDecimal.ZERO;
            BigDecimal margem = BigDecimal.ZERO;

            if (cusVar != null && cusVar.compareTo(BigDecimal.ZERO) > 0) {
                // Simulação matemática exata da trigger TRG_INC_UPD_TGFITE_MRG a partir do Custo Variável oficial da Unitrama
                margem = calculoMargemService.calcularMargemRealTrigger(ctx, sug.getVlrVenda(), cusVar, aliqIpi);
                if (sug.getVlrVenda() != null && sug.getVlrVenda().compareTo(BigDecimal.ZERO) > 0 && margem.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal umMenosMargem = BigDecimal.ONE.subtract(margem.divide(CalculoMargemTriggerService.CEM, 6, RoundingMode.HALF_UP));
                    fatorK = sug.getVlrVenda().multiply(umMenosMargem).setScale(4, RoundingMode.HALF_UP);
                } else {
                    fatorK = calculoMargemService.calcularFatorK(ctx, cusVar, aliqIpi);
                }
            } else if (sug.getVlrVenda() != null && sug.getVlrVenda().compareTo(BigDecimal.ZERO) > 0) {
                // Consulta margem padrão cadastrada para a empresa (AD_MARGEMPOREMPRESA / TGFPRO.MARGLUCRO)
                BigDecimal margemPadrao = obterMargemPadraoProduto(conn, sug.getCodProd(), ctx.nuNota);
                if (margemPadrao.compareTo(BigDecimal.ZERO) > 0) {
                    margem = margemPadrao;
                    BigDecimal umMenosMargem = BigDecimal.ONE.subtract(margemPadrao.divide(CalculoMargemTriggerService.CEM, 6, RoundingMode.HALF_UP));
                    fatorK = sug.getVlrVenda().multiply(umMenosMargem).setScale(4, RoundingMode.HALF_UP);
                } else {
                    // Fallback estrito de segurança quando não há custo nem margem no ERP
                    margem = new BigDecimal("25.00");
                    fatorK = sug.getVlrVenda().multiply(new BigDecimal("0.75")).setScale(4, RoundingMode.HALF_UP);
                }
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

    private BigDecimal obterCodLocalProduto(Connection conn, BigDecimal codProd, BigDecimal codEmp, BigDecimal nuNota) {
        if (codProd == null) return BigDecimal.ZERO;
        if (codEmp == null) codEmp = BigDecimal.ONE;

        // 1. Local padrão dos itens já presentes no pedido atual (se houver)
        if (nuNota != null && nuNota.compareTo(BigDecimal.ZERO) > 0) {
            String sqlLocalPedido = "SELECT NVL(MAX(CODLOCALORIG), 0) AS CODLOCAL FROM TGFITE WHERE NUNOTA = ? AND CODLOCALORIG > 0";
            PreparedStatement psLp = null;
            ResultSet rsLp = null;
            try {
                psLp = conn.prepareStatement(sqlLocalPedido);
                psLp.setBigDecimal(1, nuNota);
                rsLp = psLp.executeQuery();
                if (rsLp.next()) {
                    BigDecimal cl = rsLp.getBigDecimal("CODLOCAL");
                    if (cl != null && cl.compareTo(BigDecimal.ZERO) > 0) return cl;
                }
            } catch (Exception ignored) {
            } finally {
                fechar(psLp, rsLp);
            }
        }

        // 2. Local com maior saldo de estoque disponível (TGFEST) na empresa
        String sqlEst = "SELECT CODLOCAL FROM ("
                      + "  SELECT CODLOCAL FROM TGFEST "
                      + "  WHERE CODPROD = ? AND CODEMP = ? AND (ESTOQUE - RESERVADO) > 0 "
                      + "  ORDER BY (ESTOQUE - RESERVADO) DESC"
                      + ") WHERE ROWNUM = 1";
        PreparedStatement psEst = null;
        ResultSet rsEst = null;
        try {
            psEst = conn.prepareStatement(sqlEst);
            psEst.setBigDecimal(1, codProd);
            psEst.setBigDecimal(2, codEmp);
            rsEst = psEst.executeQuery();
            if (rsEst.next()) {
                BigDecimal cl = rsEst.getBigDecimal("CODLOCAL");
                if (cl != null && cl.compareTo(BigDecimal.ZERO) > 0) return cl;
            }
        } catch (Exception ignored) {
        } finally {
            fechar(psEst, rsEst);
        }

        // 3. Qualquer local cadastrado com registro em TGFEST para este produto e empresa
        String sqlEstQualquer = "SELECT CODLOCAL FROM ("
                              + "  SELECT CODLOCAL FROM TGFEST "
                              + "  WHERE CODPROD = ? AND CODEMP = ? "
                              + "  ORDER BY ESTOQUE DESC"
                              + ") WHERE ROWNUM = 1";
        PreparedStatement psEq = null;
        ResultSet rsEq = null;
        try {
            psEq = conn.prepareStatement(sqlEstQualquer);
            psEq.setBigDecimal(1, codProd);
            psEq.setBigDecimal(2, codEmp);
            rsEq = psEq.executeQuery();
            if (rsEq.next()) {
                BigDecimal cl = rsEq.getBigDecimal("CODLOCAL");
                if (cl != null && cl.compareTo(BigDecimal.ZERO) > 0) return cl;
            }
        } catch (Exception ignored) {
        } finally {
            fechar(psEq, rsEq);
        }

        // 4. Local padrão cadastrado na TGFPRO do produto
        String sqlProd = "SELECT NVL(CODLOCALPADRAO, 0) AS CODLOCAL FROM TGFPRO WHERE CODPROD = ?";
        PreparedStatement psProd = null;
        ResultSet rsProd = null;
        try {
            psProd = conn.prepareStatement(sqlProd);
            psProd.setBigDecimal(1, codProd);
            rsProd = psProd.executeQuery();
            if (rsProd.next()) {
                BigDecimal cl = rsProd.getBigDecimal("CODLOCAL");
                if (cl != null && cl.compareTo(BigDecimal.ZERO) > 0) return cl;
            }
        } catch (Exception ignored) {
        } finally {
            fechar(psProd, rsProd);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal obterCustoVariavelProduto(Connection conn, BigDecimal codProd, BigDecimal codEmp, BigDecimal codLocal, java.util.Date dtNeg) {
        if (codProd == null) return BigDecimal.ZERO;
        if (codEmp == null) codEmp = BigDecimal.ONE;
        if (codLocal == null) codLocal = BigDecimal.ZERO;
        java.sql.Date dataNeg = dtNeg != null ? new java.sql.Date(dtNeg.getTime()) : new java.sql.Date(System.currentTimeMillis());

        // 1. Função oficial do ERP Sankhya: OBTEMCUSTO_EDT (idêntica a ActionRecalculoIPIMargem e TRG_INC_UPD_TGFITE_MRG)
        String sqlEdt = "SELECT NVL(OBTEMCUSTO_EDT(?, GET_TSIPAR_LOGICO('CUSTOPOREMP'), ?, "
                      + "       GET_TSIPAR_LOGICO('CUSTOPORLOC'), ?, GET_TSIPAR_LOGICO('CUSTOPORCONT'), "
                      + "       ' ', ?, 2), 0) AS CUSVAR FROM DUAL";
        PreparedStatement psEdt = null;
        ResultSet rsEdt = null;
        try {
            psEdt = conn.prepareStatement(sqlEdt);
            psEdt.setBigDecimal(1, codProd);
            psEdt.setBigDecimal(2, codEmp);
            psEdt.setBigDecimal(3, codLocal);
            psEdt.setDate(4, dataNeg);
            rsEdt = psEdt.executeQuery();
            if (rsEdt.next()) {
                BigDecimal cv = rsEdt.getBigDecimal("CUSVAR");
                if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                    return cv;
                }
            }
        } catch (Exception e) {
            System.err.println("[AssistenteVendas Unitrama] Aviso OBTEMCUSTO_EDT para prod " + codProd + ": " + e.getMessage());
        } finally {
            fechar(psEdt, rsEdt);
        }

        // 2. Se OBTEMCUSTO_EDT com codLocal retornou 0 e codLocal > 0, tenta com codLocal = 0
        if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
            try {
                psEdt = conn.prepareStatement(sqlEdt);
                psEdt.setBigDecimal(1, codProd);
                psEdt.setBigDecimal(2, codEmp);
                psEdt.setBigDecimal(3, BigDecimal.ZERO);
                psEdt.setDate(4, dataNeg);
                rsEdt = psEdt.executeQuery();
                if (rsEdt.next()) {
                    BigDecimal cv = rsEdt.getBigDecimal("CUSVAR");
                    if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                        return cv;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                fechar(psEdt, rsEdt);
            }
        }

        // 3. Fallback TGFCUS por codLocal específico
        if (codLocal.compareTo(BigDecimal.ZERO) > 0) {
            String sqlCusLocal = "SELECT CUSVARIAVEL FROM ("
                               + "  SELECT CUSVARIAVEL FROM TGFCUS "
                               + "  WHERE CODPROD = ? AND CODEMP = ? AND CODLOCAL = ? AND CUSVARIAVEL > 0 "
                               + "  ORDER BY DTATUAL DESC"
                               + ") WHERE ROWNUM = 1";
            PreparedStatement psCl = null;
            ResultSet rsCl = null;
            try {
                psCl = conn.prepareStatement(sqlCusLocal);
                psCl.setBigDecimal(1, codProd);
                psCl.setBigDecimal(2, codEmp);
                psCl.setBigDecimal(3, codLocal);
                rsCl = psCl.executeQuery();
                if (rsCl.next()) {
                    BigDecimal cv = rsCl.getBigDecimal("CUSVARIAVEL");
                    if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                        return cv;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                fechar(psCl, rsCl);
            }
        }

        // 4. Fallback TGFCUS geral por empresa
        String sqlCusEmp = "SELECT CUSVARIAVEL FROM ("
                         + "  SELECT CUSVARIAVEL FROM TGFCUS "
                         + "  WHERE CODPROD = ? AND CODEMP = ? AND CUSVARIAVEL > 0 "
                         + "  ORDER BY DTATUAL DESC"
                         + ") WHERE ROWNUM = 1";
        PreparedStatement psCe = null;
        ResultSet rsCe = null;
        try {
            psCe = conn.prepareStatement(sqlCusEmp);
            psCe.setBigDecimal(1, codProd);
            psCe.setBigDecimal(2, codEmp);
            rsCe = psCe.executeQuery();
            if (rsCe.next()) {
                BigDecimal cv = rsCe.getBigDecimal("CUSVARIAVEL");
                if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                    return cv;
                }
            }
        } catch (Exception ignored) {
        } finally {
            fechar(psCe, rsCe);
        }

        // 5. Fallback CUSREP / CUSMED
        String sqlRepMed = "SELECT COALESCE(CUSREP, CUSMED, 0) AS CUS_ALT FROM ("
                         + "  SELECT CUSREP, CUSMED FROM TGFCUS "
                         + "  WHERE CODPROD = ? AND CODEMP = ? AND (CUSREP > 0 OR CUSMED > 0) "
                         + "  ORDER BY DTATUAL DESC"
                         + ") WHERE ROWNUM = 1";
        PreparedStatement psRep = null;
        ResultSet rsRep = null;
        try {
            psRep = conn.prepareStatement(sqlRepMed);
            psRep.setBigDecimal(1, codProd);
            psRep.setBigDecimal(2, codEmp);
            rsRep = psRep.executeQuery();
            if (rsRep.next()) {
                BigDecimal cv = rsRep.getBigDecimal("CUS_ALT");
                if (cv != null && cv.compareTo(BigDecimal.ZERO) > 0) {
                    return cv;
                }
            }
        } catch (Exception ignored) {
        } finally {
            fechar(psRep, rsRep);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal obterCustoVariavelProduto(Connection conn, BigDecimal codProd, BigDecimal nuNota) {
        CalculoMargemTriggerService.NotaFiscalContexto ctx = carregarContextoNota(conn, nuNota);
        BigDecimal codLocal = obterCodLocalProduto(conn, codProd, ctx.codEmp, nuNota);
        return obterCustoVariavelProduto(conn, codProd, ctx.codEmp, codLocal, ctx.dtNeg);
    }

    private BigDecimal obterMargemPadraoProduto(Connection conn, BigDecimal codProd, BigDecimal nuNota) {
        String sql = "SELECT NVL("
                   + "  (SELECT MAX(MARGLUCRO) FROM AD_MARGEMPOREMPRESA WHERE CODPROD = ? AND CODEMP = (SELECT CODEMP FROM TGFCAB WHERE NUNOTA = ?)), "
                   + "  (SELECT NVL(MARGLUCRO, 0) FROM TGFPRO WHERE CODPROD = ?)"
                   + ") AS MARGLUCRO FROM DUAL";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, codProd);
            ps.setBigDecimal(2, nuNota);
            ps.setBigDecimal(3, codProd);
            rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal m = rs.getBigDecimal("MARGLUCRO");
                if (m != null && m.compareTo(BigDecimal.ZERO) > 0) {
                    return m;
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
