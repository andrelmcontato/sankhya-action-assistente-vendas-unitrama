package br.com.a3consultec.assistentevendas.miner;

import br.com.a3consultec.assistentevendas.util.AssistenteConfig;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import org.cuckoo.core.ScheduledAction;
import org.cuckoo.core.ScheduledActionContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Motor Matemático de Mineração de Regras de Associação (Market Basket Analysis).
 * Executado periodicamente pelo Agendador Cuckoo (ScheduledAction).
 * 
 * Extrai histórico de vendas de TGFCAB + TGFITE e calcula:
 * - Suporte(A -> B) = cnt(A, B) / N_total
 * - Confiança(A -> B) = cnt(A, B) / cnt(A)
 * - Lift(A -> B) = (cnt(A, B) * N_total) / (cnt(A) * cnt(B))
 * 
 * Persiste na tabela nativa TVIMBR para consulta instantânea O(1).
 */
public class MarketBasketMinerJob implements ScheduledAction {

    private static final int MIN_COOCORRENCIA = 2; // Mínimo de 2 pedidos com o par

    private int getDiasHistorico() {
        return AssistenteConfig.getDiasHistorico();
    }

    private double getMinLift() {
        return AssistenteConfig.getMinLift();
    }

    private double getMinConfianca() {
        return AssistenteConfig.getMinConfianca();
    }

    @Override
    public void onTime(ScheduledActionContext context) {
        System.out.println("[AssistenteVendas - Miner] Iniciando Job de Mineração Market Basket...");
        long inicio = System.currentTimeMillis();

        // Tabela oficial nativa TVIMBR já existe no banco de dados Sankhya

        JdbcWrapper jdbc = null;
        try {
            EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
            jdbc = dwfFacade.getJdbcWrapper();
            jdbc.openSession();

            Connection conn = jdbc.getConnection();

            // 1. Total de pedidos faturados nos últimos N dias
            long totalPedidos = contarTotalPedidos(conn);
            if (totalPedidos < 5) {
                System.out.println("[AssistenteVendas - Miner] Volume insuficiente de pedidos (" + totalPedidos + "). Abortando mineração.");
                return;
            }
            System.out.println("[AssistenteVendas - Miner] Total de pedidos analisados: " + totalPedidos);

            // 2. Frequência individual de cada produto
            Map<Long, Long> freqProdutos = carregarFrequenciaProdutos(conn);
            System.out.println("[AssistenteVendas - Miner] Produtos ativos encontrados: " + freqProdutos.size());

            // 3. Minerar pares coocorrentes e calcular Suporte, Confiança e Lift
            minerarESalvarRegras(conn, totalPedidos, freqProdutos);

            long tempoTotal = System.currentTimeMillis() - inicio;
            System.out.println("[AssistenteVendas - Miner] Mineração concluída com sucesso em " + tempoTotal + " ms.");

        } catch (Exception e) {
            System.err.println("[AssistenteVendas - Miner] Erro na mineração de regras de associação: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (jdbc != null) {
                try {
                    jdbc.closeSession();
                } catch (Exception e) {
                    System.err.println("[AssistenteVendas - Miner] Erro ao fechar sessão JDBC: " + e.getMessage());
                }
            }
        }
    }

    private long contarTotalPedidos(Connection conn) throws Exception {
        String sql = "SELECT COUNT(DISTINCT NUNOTA) AS TOTAL "
                   + "FROM TGFCAB "
                   + "WHERE TIPMOV = 'V' AND STATUSNOTA = 'L' "
                   + "  AND DTNEG >= SYSDATE - ?";

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setInt(1, getDiasHistorico());
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("TOTAL");
            }
            return 0;
        } finally {
            fecharRecursos(ps, rs);
        }
    }

    private Map<Long, Long> carregarFrequenciaProdutos(Connection conn) throws Exception {
        Map<Long, Long> map = new HashMap<Long, Long>();
        String sql = "WITH NOTAS_VALIDAS AS ( "
                   + "    SELECT NUNOTA "
                   + "    FROM TGFCAB "
                   + "    WHERE TIPMOV = 'V' AND STATUSNOTA = 'L' "
                   + "      AND DTNEG >= SYSDATE - ? "
                   + ") "
                   + "SELECT I.CODPROD, COUNT(DISTINCT I.NUNOTA) AS FREQ "
                   + "FROM TGFITE I "
                   + "INNER JOIN NOTAS_VALIDAS N ON N.NUNOTA = I.NUNOTA "
                   + "GROUP BY I.CODPROD";

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setInt(1, getDiasHistorico());
            rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getLong("CODPROD"), rs.getLong("FREQ"));
            }
            return map;
        } finally {
            fecharRecursos(ps, rs);
        }
    }

    private void minerarESalvarRegras(Connection conn, long totalPedidos, Map<Long, Long> freqProdutos) throws Exception {
        java.sql.Timestamp dataInicio = new java.sql.Timestamp(System.currentTimeMillis());

        // Otimização de Simetria e CTE: ITENS_UNICOS elimina distincts pesados no auto-join
        String sqlPares = "WITH NOTAS_VALIDAS AS ( "
                        + "    SELECT NUNOTA "
                        + "    FROM TGFCAB "
                        + "    WHERE TIPMOV = 'V' AND STATUSNOTA = 'L' "
                        + "      AND DTNEG >= SYSDATE - ? "
                        + "), "
                        + "ITENS_UNICOS AS ( "
                        + "    SELECT I.NUNOTA, I.CODPROD "
                        + "    FROM TGFITE I "
                        + "    INNER JOIN NOTAS_VALIDAS N ON N.NUNOTA = I.NUNOTA "
                        + "    GROUP BY I.NUNOTA, I.CODPROD "
                        + ") "
                        + "SELECT I1.CODPROD AS PROD_A, I2.CODPROD AS PROD_B, COUNT(1) AS FREQ_PAR "
                        + "FROM ITENS_UNICOS I1 "
                        + "INNER JOIN ITENS_UNICOS I2 ON I1.NUNOTA = I2.NUNOTA AND I1.CODPROD < I2.CODPROD "
                        + "GROUP BY I1.CODPROD, I2.CODPROD "
                        + "HAVING COUNT(1) >= ?";

        long execId = 1L; // Partição ativa de execução de regras na TVIMBR

        // Assegurar existência do registro pai (TVIHEX) para respeitar a constraint FK_TVIMBR_EXEC
        garantirExecucaoPai(conn, execId, dataInicio);

        String sqlMerge = "MERGE INTO TVIMBR R "
                        + "USING (SELECT ? AS EXEC_ID, ? AS CODPROD_A, ? AS CONTROLE_A, ? AS CODPROD_B, ? AS CONTROLE_B FROM DUAL) S "
                        + "ON (R.EXEC_ID = S.EXEC_ID AND R.CODPROD_A = S.CODPROD_A AND R.CONTROLE_A = S.CONTROLE_A AND R.CODPROD_B = S.CODPROD_B AND R.CONTROLE_B = S.CONTROLE_B) "
                        + "WHEN MATCHED THEN UPDATE SET "
                        + "  R.N_CESTAS = ?, R.CNT_A = ?, R.CNT_B = ?, R.CNT_AB = ?, "
                        + "  R.SUPPORT_A = ?, R.SUPPORT_B = ?, R.SUPPORT_AB = ?, "
                        + "  R.CONFIDENCE_A_B = ?, R.LIFT_A_B = ?, R.SCORE_COMPONENT = ?, "
                        + "  R.HINT_TEXTO = ?, R.DT_GERACAO = SYSDATE, R.TIPO_EXPLICACAO = 'MARKET_BASKET' "
                        + "WHEN NOT MATCHED THEN INSERT "
                        + "  (EXEC_ID, CODPROD_A, CONTROLE_A, CODPROD_B, CONTROLE_B, "
                        + "   N_CESTAS, CNT_A, CNT_B, CNT_AB, "
                        + "   SUPPORT_A, SUPPORT_B, SUPPORT_AB, "
                        + "   CONFIDENCE_A_B, LIFT_A_B, SCORE_COMPONENT, "
                        + "   HINT_TEXTO, DT_GERACAO, TIPO_EXPLICACAO) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATE, 'MARKET_BASKET')";

        PreparedStatement psPares = null;
        ResultSet rsPares = null;
        PreparedStatement psMerge = null;

        try {
            psPares = conn.prepareStatement(sqlPares);
            psPares.setInt(1, getDiasHistorico());
            psPares.setInt(2, MIN_COOCORRENCIA);
            rsPares = psPares.executeQuery();

            psMerge = conn.prepareStatement(sqlMerge);

            int batchCount = 0;
            int regrasInseridas = 0;

            while (rsPares.next()) {
                long prodA = rsPares.getLong("PROD_A");
                long prodB = rsPares.getLong("PROD_B");
                long freqPar = rsPares.getLong("FREQ_PAR");

                Long freqA = freqProdutos.get(prodA);
                Long freqB = freqProdutos.get(prodB);

                if (freqA == null || freqB == null || freqA <= 0 || freqB <= 0) {
                    continue;
                }

                // Avaliação direcional A -> B
                if (adicionarRegraSeValida(psMerge, execId, prodA, prodB, totalPedidos, freqA, freqB, freqPar)) {
                    regrasInseridas++;
                    batchCount++;
                }

                // Avaliação direcional B -> A (reciprocidade assimétrica)
                if (adicionarRegraSeValida(psMerge, execId, prodB, prodA, totalPedidos, freqB, freqA, freqPar)) {
                    regrasInseridas++;
                    batchCount++;
                }

                if (batchCount >= 100) {
                    psMerge.executeBatch();
                    if (!conn.getAutoCommit()) {
                        conn.commit();
                    }
                    batchCount = 0;
                }
            }

            if (batchCount > 0) {
                psMerge.executeBatch();
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
            }

            System.out.println("[AssistenteVendas - Miner] Regras de afinidade computadas e salvas na TVIMBR: " + regrasInseridas);

            // Limpar regras obsoletas de execuções anteriores que decaíram abaixo do corte estatístico
            limparRegrasObsoletas(conn, execId, dataInicio);

            // Finalizar cabeçalho na tabela mãe
            long duracaoMs = System.currentTimeMillis() - dataInicio.getTime();
            finalizarExecucaoPai(conn, execId, duracaoMs, regrasInseridas);

        } catch (java.sql.SQLException sqle) {
            System.err.println("[AssistenteVendas - Miner] Erro SQL ao persistir regras na TVIMBR: " + sqle.getMessage());
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (Exception ignored) {
            }
            throw sqle;
        } finally {
            fecharRecursos(psPares, rsPares);
            if (psMerge != null) {
                try {
                    psMerge.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean adicionarRegraSeValida(PreparedStatement psMerge, long execId, long prodA, long prodB,
                                          long totalPedidos, long freqA, long freqB, long freqPar) throws Exception {
        double suporteA = (double) freqA / (double) totalPedidos;
        double suporteB = (double) freqB / (double) totalPedidos;
        double suporteAB = (double) freqPar / (double) totalPedidos;
        double confianca = (double) freqPar / (double) freqA;
        if (suporteB <= 0 || confianca <= 0) {
            return false;
        }
        double lift = confianca / suporteB;

        if (Double.isNaN(lift) || Double.isInfinite(lift) || lift < getMinLift() || confianca < getMinConfianca()) {
            return false;
        }

        BigDecimal bdSupportA = BigDecimal.valueOf(suporteA).setScale(6, RoundingMode.HALF_UP);
        BigDecimal bdSupportB = BigDecimal.valueOf(suporteB).setScale(6, RoundingMode.HALF_UP);
        BigDecimal bdSupportAB = BigDecimal.valueOf(suporteAB).setScale(6, RoundingMode.HALF_UP);
        BigDecimal bdConfianca = BigDecimal.valueOf(confianca).setScale(6, RoundingMode.HALF_UP);
        BigDecimal bdLift = BigDecimal.valueOf(lift).setScale(6, RoundingMode.HALF_UP);
        BigDecimal bdScore = BigDecimal.valueOf(lift * confianca).setScale(6, RoundingMode.HALF_UP);

        String hintTexto = "Quem compra este item frequentemente também leva este produto complementar.";

        // USING (Chave Primária de 5 campos)
        psMerge.setLong(1, execId);
        psMerge.setLong(2, prodA);
        psMerge.setString(3, " ");
        psMerge.setLong(4, prodB);
        psMerge.setString(5, " ");

        // UPDATE
        psMerge.setLong(6, totalPedidos);
        psMerge.setLong(7, freqA);
        psMerge.setLong(8, freqB);
        psMerge.setLong(9, freqPar);
        psMerge.setBigDecimal(10, bdSupportA);
        psMerge.setBigDecimal(11, bdSupportB);
        psMerge.setBigDecimal(12, bdSupportAB);
        psMerge.setBigDecimal(13, bdConfianca);
        psMerge.setBigDecimal(14, bdLift);
        psMerge.setBigDecimal(15, bdScore);
        psMerge.setString(16, hintTexto);

        // INSERT
        psMerge.setLong(17, execId);
        psMerge.setLong(18, prodA);
        psMerge.setString(19, " ");
        psMerge.setLong(20, prodB);
        psMerge.setString(21, " ");
        psMerge.setLong(22, totalPedidos);
        psMerge.setLong(23, freqA);
        psMerge.setLong(24, freqB);
        psMerge.setLong(25, freqPar);
        psMerge.setBigDecimal(26, bdSupportA);
        psMerge.setBigDecimal(27, bdSupportB);
        psMerge.setBigDecimal(28, bdSupportAB);
        psMerge.setBigDecimal(29, bdConfianca);
        psMerge.setBigDecimal(30, bdLift);
        psMerge.setBigDecimal(31, bdScore);
        psMerge.setString(32, hintTexto);

        psMerge.addBatch();
        return true;
    }

    private void limparRegrasObsoletas(Connection conn, long execId, java.sql.Timestamp dataInicio) {
        String sql = "DELETE FROM TVIMBR WHERE EXEC_ID = ? AND (DT_GERACAO IS NULL OR DT_GERACAO < ?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setLong(1, execId);
            ps.setTimestamp(2, dataInicio);
            int removidas = ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            if (removidas > 0) {
                System.out.println("[AssistenteVendas - Miner] Regras obsoletas removidas da TVIMBR: " + removidas);
            }
        } catch (Exception e) {
            System.err.println("[AssistenteVendas - Miner] Aviso ao limpar regras obsoletas: " + e.getMessage());
        } finally {
            fecharRecursos(ps, null);
        }
    }

    private void garantirExecucaoPai(Connection conn, long execId, java.sql.Timestamp dataInicio) {
        String tabelaPai = "TVIHEX";
        PreparedStatement psFk = null;
        ResultSet rsFk = null;
        try {
            String sqlFk = "SELECT R.TABLE_NAME "
                         + "FROM USER_CONSTRAINTS C "
                         + "JOIN USER_CONSTRAINTS R ON C.R_CONSTRAINT_NAME = R.CONSTRAINT_NAME "
                         + "WHERE C.CONSTRAINT_NAME = 'FK_TVIMBR_EXEC'";
            psFk = conn.prepareStatement(sqlFk);
            rsFk = psFk.executeQuery();
            if (rsFk.next()) {
                String tbl = rsFk.getString(1);
                if (tbl != null && !tbl.trim().isEmpty()) {
                    tabelaPai = tbl.trim().toUpperCase();
                }
            }
        } catch (Exception ignored) {
            tabelaPai = "TVIHEX";
        } finally {
            fecharRecursos(psFk, rsFk);
        }

        String sqlMerge = "MERGE INTO " + tabelaPai + " H "
                        + "USING (SELECT ? AS EXEC_ID FROM DUAL) S "
                        + "ON (H.EXEC_ID = S.EXEC_ID) "
                        + "WHEN MATCHED THEN "
                        + "  UPDATE SET H.STATUS_EXECUCAO = 'EM_ANDAMENTO', H.DT_INICIO = ? "
                        + "WHEN NOT MATCHED THEN "
                        + "  INSERT (EXEC_ID, TIPO_SUGESTAO, TIPO_EXECUCAO, STATUS_EXECUCAO, DT_INICIO, FALLBACK_APLICADO, OBSERVACAO) "
                        + "  VALUES (?, 'MARKET_BASKET', 'AGENDADA', 'EM_ANDAMENTO', ?, 'N', 'Assistente de Vendas - Cuckoo Miner')";

        PreparedStatement psMerge = null;
        try {
            psMerge = conn.prepareStatement(sqlMerge);
            psMerge.setLong(1, execId);
            psMerge.setTimestamp(2, dataInicio);
            psMerge.setLong(3, execId);
            psMerge.setTimestamp(4, dataInicio);
            psMerge.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
            System.out.println("[AssistenteVendas - Miner] Registro mestre assegurado na tabela " + tabelaPai + " (EXEC_ID=" + execId + ")");
        } catch (Exception e) {
            System.err.println("[AssistenteVendas - Miner] Aviso ao registrar na tabela mãe " + tabelaPai + ": " + e.getMessage());
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (Exception ignored) {
            }
        } finally {
            fecharRecursos(psMerge, null);
        }
    }

    private void finalizarExecucaoPai(Connection conn, long execId, long duracaoMs, int regrasSalvas) {
        String tabelaPai = "TVIHEX";
        String sqlUpdate = "UPDATE " + tabelaPai + " "
                         + "SET DT_FIM = SYSDATE, STATUS_EXECUCAO = 'CONCLUIDA', DURACAO_MS = ?, "
                         + "    OBSERVACAO = 'Mineracao concluida. Regras ativas: ' || ? "
                         + "WHERE EXEC_ID = ?";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sqlUpdate);
            ps.setLong(1, duracaoMs);
            ps.setInt(2, regrasSalvas);
            ps.setLong(3, execId);
            ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (Exception e) {
            System.err.println("[AssistenteVendas - Miner] Aviso ao finalizar status na tabela mãe: " + e.getMessage());
        } finally {
            fecharRecursos(ps, null);
        }
    }

    private void fecharRecursos(PreparedStatement ps, ResultSet rs) {
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
