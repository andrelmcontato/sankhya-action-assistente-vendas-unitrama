package br.com.a3consultec.assistentevendas.util;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitário central de configurações e parâmetros do Assistente de Vendas (Unitrama).
 * Realiza a leitura dinâmica dos parâmetros na tabela nativa TSIPAR do Sankhya,
 * com cache em memória (TTL: 60s) para garantir tempo de resposta ultrarrápido (< 1ms).
 * 
 * Parâmetros suportados na TSIPAR:
 * - ASSTDIASHIST : Período em dias para mineração de afinidade e recompra (Padrão: 180)
 * - ASSTMINLIFT  : Valor mínimo de Lift para correlação positiva (Padrão: 1.20)
 * - ASSTMINCONF  : Confiança estatística mínima (Padrão: 0.10)
 * - ASSTMAXSUG   : Quantidade máxima de sugestões exibidas na tela (Padrão: 6)
 */
public class AssistenteConfig {

    public static final int DEFAULT_DIAS_HISTORICO = 180;
    public static final double DEFAULT_MIN_LIFT = 1.20;
    public static final double DEFAULT_MIN_CONFIANCA = 0.10;
    public static final int DEFAULT_MAX_SUGESTOES = 6;

    private static final long CACHE_TTL_MS = 60000L;
    private static volatile ConfigData cachedConfig = null;

    private static class ConfigData {
        final int diasHistorico;
        final double minLift;
        final double minConfianca;
        final int maxSugestoes;
        final long timestamp;

        ConfigData(int diasHistorico, double minLift, double minConfianca, int maxSugestoes) {
            this.diasHistorico = diasHistorico;
            this.minLift = minLift;
            this.minConfianca = minConfianca;
            this.maxSugestoes = maxSugestoes;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValido() {
            return (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS;
        }
    }

    public static int getDiasHistorico() {
        return getConfig().diasHistorico;
    }

    public static double getMinLift() {
        return getConfig().minLift;
    }

    public static double getMinConfianca() {
        return getConfig().minConfianca;
    }

    public static int getMaxSugestoes() {
        return getConfig().maxSugestoes;
    }

    public static void limparCache() {
        cachedConfig = null;
    }

    public static void setConfigForTesting(int diasHistorico, double minLift, double minConfianca, int maxSugestoes) {
        cachedConfig = new ConfigData(diasHistorico, minLift, minConfianca, maxSugestoes);
    }

    private static ConfigData getConfig() {
        ConfigData current = cachedConfig;
        if (current != null && current.isValido()) {
            return current;
        }

        synchronized (AssistenteConfig.class) {
            if (cachedConfig != null && cachedConfig.isValido()) {
                return cachedConfig;
            }

            int diasHistorico = DEFAULT_DIAS_HISTORICO;
            double minLift = DEFAULT_MIN_LIFT;
            double minConfianca = DEFAULT_MIN_CONFIANCA;
            int maxSugestoes = DEFAULT_MAX_SUGESTOES;

            JdbcWrapper jdbc = null;
            try {
                EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
                if (dwfFacade != null) {
                    jdbc = dwfFacade.getJdbcWrapper();
                    jdbc.openSession();
                    Connection conn = jdbc.getConnection();

                    String sql = "SELECT CHAVE, INTEIRO, NUMDEC, TEXTO FROM TSIPAR "
                               + "WHERE CHAVE IN ('ASSTDIASHIST', 'ASSTMINLIFT', 'ASSTMINCONF', 'ASSTMAXSUG')";
                    PreparedStatement ps = null;
                    ResultSet rs = null;
                    try {
                        ps = conn.prepareStatement(sql);
                        rs = ps.executeQuery();
                        Map<String, BigDecimal> valores = new HashMap<String, BigDecimal>();
                        while (rs.next()) {
                            String chave = rs.getString("CHAVE");
                            if (chave != null) {
                                chave = chave.trim().toUpperCase();
                                BigDecimal val = rs.getBigDecimal("NUMDEC");
                                if (val == null) {
                                    val = rs.getBigDecimal("INTEIRO");
                                }
                                if (val == null) {
                                    String txt = rs.getString("TEXTO");
                                    if (txt != null && !txt.trim().isEmpty()) {
                                        try {
                                            val = new BigDecimal(txt.trim().replace(",", "."));
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                                if (val != null) {
                                    valores.put(chave, val);
                                }
                            }
                        }

                        if (valores.containsKey("ASSTDIASHIST")) {
                            int v = valores.get("ASSTDIASHIST").intValue();
                            if (v > 0) diasHistorico = v;
                        }
                        if (valores.containsKey("ASSTMINLIFT")) {
                            double v = valores.get("ASSTMINLIFT").doubleValue();
                            if (v > 0) minLift = v;
                        }
                        if (valores.containsKey("ASSTMINCONF")) {
                            double v = valores.get("ASSTMINCONF").doubleValue();
                            if (v > 0) minConfianca = v;
                        }
                        if (valores.containsKey("ASSTMAXSUG")) {
                            int v = valores.get("ASSTMAXSUG").intValue();
                            if (v > 0) maxSugestoes = v;
                        }
                    } finally {
                        if (rs != null) try { rs.close(); } catch (Exception ignored) {}
                        if (ps != null) try { ps.close(); } catch (Exception ignored) {}
                    }
                }
            } catch (Throwable t) {
                // Silently fallback to defaults
            } finally {
                if (jdbc != null) {
                    try {
                        jdbc.closeSession();
                    } catch (Exception ignored) {
                    }
                }
            }

            cachedConfig = new ConfigData(diasHistorico, minLift, minConfianca, maxSugestoes);
            return cachedConfig;
        }
    }
}
