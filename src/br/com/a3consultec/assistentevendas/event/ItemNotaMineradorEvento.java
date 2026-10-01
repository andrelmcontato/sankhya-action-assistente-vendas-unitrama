package br.com.a3consultec.assistentevendas.event;

import br.com.a3consultec.assistentevendas.service.AssistenteVendasService;
import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Evento de Tabela programável em Java na entidade ItemNota (TGFITE).
 * Dispara automaticamente a mineração e o recálculo de sugestões de vendas
 * sempre que um item for inserido, alterado ou excluído de um Pedido de Venda.
 *
 * Filtros de Proteção Cirúrgicos (Zero-Overhead):
 * - TGFCAB.TIPMOV = 'P' (Apenas Pedidos de Venda)
 * - TGFCAB.STATUSNOTA <> 'L' (Apenas pedidos em elaboração, ignorando notas liberadas/confirmadas)
 * - TGFTOP.GOLSINAL = -1 (Apenas operações comerciais de saída/venda)
 * - Ligação: TGFCAB.CODTIPOPER = TGFTOP.CODTIPOPER AND TGFCAB.DHTIPOPER = TGFTOP.DHALTER
 */
public class ItemNotaMineradorEvento implements EventoProgramavelJava {

    private AssistenteVendasService service;

    public ItemNotaMineradorEvento() {
        this.service = new AssistenteVendasService();
    }

    public ItemNotaMineradorEvento(AssistenteVendasService service) {
        this.service = service != null ? service : new AssistenteVendasService();
    }

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {
        processarAlteracaoItem(event);
    }

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {
        processarAlteracaoItem(event);
    }

    @Override
    public void afterDelete(PersistenceEvent event) throws Exception {
        processarAlteracaoItem(event);
    }

    /**
     * Valida os critérios de negócio solicitados:
     * 1. TIPMOV = 'P' (Pedido de Venda)
     * 2. STATUSNOTA <> 'L' (Não liberado)
     * 3. GOLSINAL = -1 (Operação comercial de saída/venda)
     */
    public static boolean validarCriterios(String tipMov, String statusNota, int golSinal) {
        if (!"P".equalsIgnoreCase(tipMov)) {
            return false;
        }
        if ("L".equalsIgnoreCase(statusNota)) {
            return false;
        }
        if (golSinal != -1) {
            return false;
        }
        return true;
    }

    /**
     * Processa a inserção/alteração/exclusão do item na TGFITE com fail-fast.
     */
    public void processarAlteracaoItem(PersistenceEvent event) {
        if (event == null || event.getVo() == null) {
            return;
        }

        try {
            DynamicVO itemVO = (DynamicVO) event.getVo();
            BigDecimal nuNota = itemVO.asBigDecimal("NUNOTA");
            if (nuNota == null || nuNota.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            // Otimização Fast-Fail O(1) em memória RAM:
            // Se o DynamicVO já tiver o Cabeçalho carregado no contexto JAPE, valida antes de qualquer SELECT
            DynamicVO cabVO = null;
            try {
                Object cabObj = itemVO.getProperty("CabecalhoNota");
                if (cabObj instanceof DynamicVO) {
                    cabVO = (DynamicVO) cabObj;
                }
            } catch (Exception ignored) {}

            if (cabVO != null) {
                String tipMov = cabVO.asString("TIPMOV");
                String statusNota = cabVO.asString("STATUSNOTA");
                if (tipMov != null && !"P".equalsIgnoreCase(tipMov)) {
                    return;
                }
                if ("L".equalsIgnoreCase(statusNota)) {
                    return;
                }
            }

            // Validação completa contra TGFCAB e TGFTOP
            if (!isPedidoValido(nuNota)) {
                return;
            }

            System.out.println("[ItemNotaMineradorEvento] >>> Evento disparado com sucesso para Pedido #" + nuNota + "! Calculando mineração em memória...");

            // Dispara o cálculo e cache das recomendações de venda
            service.obterSugestoes(nuNota);

            System.out.println("[ItemNotaMineradorEvento] <<< Mineração e cache atualizados para Pedido #" + nuNota);

        } catch (Throwable t) {
            // Guardrail estrito: NUNCA abortar a transação do vendedor por falha no assistente
            System.err.println("[ItemNotaMineradorEvento] Erro não impeditivo ao processar item: " + t.getMessage());
        }
    }

    /**
     * Consulta TGFCAB e TGFTOP com join pelas chaves compostas oficiais:
     * TGFCAB.CODTIPOPER = TGFTOP.CODTIPOPER AND TGFCAB.DHTIPOPER = TGFTOP.DHALTER
     */
    public boolean isPedidoValido(BigDecimal nuNota) {
        if (nuNota == null || nuNota.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        String sql = "SELECT C.TIPMOV, C.STATUSNOTA, COALESCE(T.GOLSINAL, 0) AS GOLSINAL "
                   + "FROM TGFCAB C "
                   + "INNER JOIN TGFTOP T ON T.CODTIPOPER = C.CODTIPOPER AND T.DHALTER = C.DHTIPOPER "
                   + "WHERE C.NUNOTA = ?";

        JdbcWrapper jdbc = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
            jdbc = dwf.getJdbcWrapper();
            jdbc.openSession();
            Connection conn = jdbc.getConnection();

            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, nuNota);
            rs = ps.executeQuery();

            if (rs.next()) {
                String tipMov = rs.getString("TIPMOV");
                String statusNota = rs.getString("STATUSNOTA");
                int golSinal = rs.getInt("GOLSINAL");
                return validarCriterios(tipMov, statusNota, golSinal);
            }
            return false;
        } catch (Exception e) {
            System.err.println("[ItemNotaMineradorEvento] Erro ao validar pedido #" + nuNota + ": " + e.getMessage());
            return false;
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception ignored) {}
            }
            if (ps != null) {
                try { ps.close(); } catch (Exception ignored) {}
            }
            if (jdbc != null) {
                jdbc.closeSession();
            }
        }
    }

    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {
        // Sem operação antes da inserção
    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {
        // Sem operação antes da atualização
    }

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {
        // Sem operação antes da exclusão
    }

    @Override
    public void beforeCommit(TransactionContext tranCtx) throws Exception {
        // Sem operação antes do commit
    }
}
