package br.com.a3consultec.assistentevendas.action;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.PrePersistEntityState;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.comercial.CentralFinanceiro;
import br.com.sankhya.modelcore.comercial.UnidadeProdutoUtils;
import br.com.sankhya.modelcore.comercial.centrais.CACHelper;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Ação Java para inclusão de produto sugerido diretamente no pedido de venda (TGFITE)
 * utilizando o motor nativo comercial do ERP Sankhya (CACHelper / CACSP).
 * Simula fielmente a digitação manual de itens na Central de Vendas, calculando
 * automaticamente:
 * - Tabela de preços do parceiro / TOP
 * - Descontos, promoções e verbas
 * - Impostos de cada item (ICMS, IPI, PIS, COFINS, ST, CSTs, alíquotas e bases)
 * - Totalizadores do cabeçalho da nota (TGFCAB.VLRNOTA, bases e impostos)
 * - Parcelas do financeiro (TGFFIN via CentralFinanceiro)
 */
public class AdicionarItemAction implements AcaoRotinaJava {

    @Override
    public void doAction(ContextoAcao contexto) throws Exception {
        BigDecimal codProd = extrairParametroBigDecimal(contexto, "CODPROD");
        if (codProd == null) {
            contexto.mostraErro("Código do produto (CODPROD) não informado para inclusão.");
            return;
        }

        BigDecimal nuNota = extrairNuNota(contexto);
        if (nuNota == null) {
            contexto.mostraErro("Não foi possível identificar o número do pedido (NUNOTA).");
            return;
        }

        BigDecimal qtdNeg = extrairParametroBigDecimal(contexto, "QTDNEG");
        if (qtdNeg == null || qtdNeg.compareTo(BigDecimal.ZERO) <= 0) {
            qtdNeg = BigDecimal.ONE;
        }

        BigDecimal vlrUnit = extrairParametroBigDecimal(contexto, "VLRUNIT");

        incluirItemNoPedido(nuNota, codProd, qtdNeg, vlrUnit);
        // NOTA: Não chamamos contexto.setMensagemRetorno(...) para evitar que o snk.js
        // plote uma faixa de texto estática ("nome perdido em cima") no topo da Central de Vendas.
    }

    public static String incluirItemNoPedido(BigDecimal nuNota, BigDecimal codProd, BigDecimal qtdNeg) throws Exception {
        return incluirItemNoPedido(nuNota, codProd, qtdNeg, null);
    }

    /**
     * Inclusão nativa do item via motor comercial do CAC (CACHelper), idêntica à digitação manual
     * de um usuário na Central de Vendas do SankhyaW.
     * Suporta VLRUNIT customizado negociado pelo vendedor no Assistente de Vendas.
     */
    public static String incluirItemNoPedido(BigDecimal nuNota, BigDecimal codProd, BigDecimal qtdNeg, BigDecimal vlrUnitCustom) throws Exception {
        EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = null;

        String descrProd = "";
        String codVol = "UN";
        BigDecimal vlrUnit = BigDecimal.ZERO;
        BigDecimal agrupMin = BigDecimal.ONE;

        // 1. Obter dados cadastrais essenciais do produto (descrição, unidade de volume padrão, agrupamento mínimo e preço de referência)
        try {
            jdbc = dwfFacade.getJdbcWrapper();
            jdbc.openSession();
            Connection conn = jdbc.getConnection();

            String sqlProd = "SELECT P.DESCRPROD, P.CODVOL, NVL(P.AGRUPMIN, 1) AS AGRUPMIN, "
                           + "       COALESCE("
                           + "           (SELECT MAX(EXC.VLRVENDA) FROM TGFEXC EXC WHERE EXC.CODPROD = P.CODPROD AND EXC.VLRVENDA > 0), "
                           + "           (SELECT MAX(ITE.VLRUNIT) FROM TGFITE ITE WHERE ITE.CODPROD = P.CODPROD AND ITE.VLRUNIT > 0), "
                           + "           0"
                           + "       ) AS VLRVENDA "
                           + "FROM TGFPRO P WHERE P.CODPROD = ?";
            PreparedStatement psProd = null;
            ResultSet rsProd = null;
            try {
                psProd = conn.prepareStatement(sqlProd);
                psProd.setBigDecimal(1, codProd);
                rsProd = psProd.executeQuery();
                if (rsProd.next()) {
                    descrProd = rsProd.getString("DESCRPROD");
                    String cv = rsProd.getString("CODVOL");
                    if (cv != null && !cv.trim().isEmpty()) {
                        codVol = cv.trim();
                    }
                    BigDecimal ag = rsProd.getBigDecimal("AGRUPMIN");
                    if (ag != null && ag.compareTo(BigDecimal.ZERO) > 0) {
                        agrupMin = ag;
                    }
                    BigDecimal vu = rsProd.getBigDecimal("VLRVENDA");
                    if (vu != null) {
                        vlrUnit = vu;
                    }
                } else {
                    throw new Exception("Produto " + codProd + " não encontrado no cadastro.");
                }
            } finally {
                if (rsProd != null) rsProd.close();
                if (psProd != null) psProd.close();
            }
        } finally {
            if (jdbc != null) {
                try {
                    jdbc.closeSession();
                } catch (Exception ignored) {
                }
            }
        }

        // Validação e Ajuste de Múltiplos Mínimos (TGFPRO.AGRUPMIN)
        qtdNeg = ajustarQuantidadeAgrupMin(qtdNeg, agrupMin);
        if (agrupMin.compareTo(BigDecimal.ONE) > 0) {
            System.out.println("[AdicionarItemAction] Produto " + codProd + " possui AGRUPMIN=" + agrupMin + ". Qtd ajustada para múltiplo exato: " + qtdNeg);
        }

        // Se o vendedor informou um preço negociado personalizado, prioriza-o
        if (vlrUnitCustom != null && vlrUnitCustom.compareTo(BigDecimal.ZERO) > 0) {
            vlrUnit = vlrUnitCustom;
        }

        // 2. Obter contexto de autenticação do usuário logado
        AuthenticationInfo authInfo = null;
        try {
            ServiceContext sCtx = ServiceContext.getCurrent();
            if (sCtx != null) {
                authInfo = (AuthenticationInfo) sCtx.getAutentication();
            }
        } catch (Exception ignored) {
        }
        if (authInfo == null) {
            try {
                authInfo = AuthenticationInfo.getCurrent();
            } catch (Exception ignored) {
            }
        }
        if (authInfo == null) {
            try {
                authInfo = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
                authInfo.makeCurrent();
            } catch (Exception ignored) {
            }
        }

        // 3. Montar VO do ItemNota e encapsular em PrePersistEntityState para o Barramento CAC
        DynamicVO itemVO = (DynamicVO) dwfFacade.getDefaultValueObjectInstance("ItemNota");
        itemVO.setProperty("NUNOTA", nuNota);
        itemVO.setProperty("CODPROD", codProd);
        itemVO.setProperty("CODVOL", codVol);
        itemVO.setProperty("QTDNEG", qtdNeg != null && qtdNeg.compareTo(BigDecimal.ZERO) > 0 ? qtdNeg : BigDecimal.ONE);
        if (vlrUnit != null && vlrUnit.compareTo(BigDecimal.ZERO) > 0) {
            itemVO.setProperty("VLRUNIT", vlrUnit);
        }

        try {
            UnidadeProdutoUtils.converterUNPadraoParaUNAlternativa(itemVO, 64);
        } catch (Exception ignored) {
        }

        PrePersistEntityState itemMontado = null;
        try {
            PersistentLocalEntity baseOrderPLE = dwfFacade.findEntityByPrimaryKey("CabecalhoNota", new Object[]{nuNota});
            itemMontado = PrePersistEntityState.build(dwfFacade, "ItemNota", itemVO, null, baseOrderPLE);
        } catch (Exception e) {
            itemMontado = PrePersistEntityState.build(dwfFacade, "ItemNota", itemVO);
        }

        Collection itensNota = new ArrayList();
        itensNota.add(itemMontado);

        BigDecimal userId = (authInfo != null && authInfo.getUserID() != null) ? authInfo.getUserID() : BigDecimal.ZERO;

        // 4. Executar Inclusão Comercial Nativa via CACHelper (motor que simula inclusão manual de usuário)
        System.out.println("[AdicionarItemAction] Executando CACHelper.incluirAlterarItem para Pedido #" + nuNota + ", Produto " + codProd + ", Qtd " + qtdNeg + "...");
        CACHelper cac = new CACHelper();
        cac.setPedidoWeb(false);
        try {
            JapeSessionContext.putProperty("br.com.sankhya.com.CentralCompraVenda", Boolean.TRUE);
            JapeSessionContext.putProperty("usuario_logado", userId);
            JapeSessionContext.putProperty("authInfo", authInfo);
            JapeSessionContext.putProperty("calcular.outros.impostos", "false");
            JapeSession.putProperty("br.com.sankhya.com.CentralCompraVenda", Boolean.TRUE);
            JapeSession.putProperty("ItemNota.incluindo.alterando.pela.central", Boolean.TRUE);

            cac.incluirAlterarItem(nuNota, authInfo, itensNota, true);
            try {
                cac.gerarFinanceiroTardio();
            } catch (Exception ignored) {
            }
            System.out.println("[AdicionarItemAction] Item " + codProd + " inserido com sucesso via motor comercial CAC.");

            // 5. Recalcular Impostos, Totalizar Nota (TGFCAB.VLRNOTA) e Regenerar Financeiro (TGFFIN)
            recalcularTotaisEFinanceiro(nuNota);
        } finally {
            JapeSession.removeProperty("ItemNota.incluindo.alterando.pela.central");
            JapeSession.removeProperty("br.com.sankhya.com.CentralCompraVenda");
            JapeSessionContext.removeProperty("br.com.sankhya.com.CentralCompraVenda");
            JapeSessionContext.removeProperty("usuario_logado");
            JapeSessionContext.removeProperty("authInfo");
            JapeSessionContext.removeProperty("calcular.outros.impostos");
        }

        return descrProd;
    }

    public static void recalcularTotaisEFinanceiro(BigDecimal nuNota) {
        if (nuNota == null || nuNota.compareTo(BigDecimal.ZERO) <= 0) return;

        AuthenticationInfo auth = null;
        try {
            ServiceContext sCtx = ServiceContext.getCurrent();
            if (sCtx != null) {
                auth = (AuthenticationInfo) sCtx.getAutentication();
            }
        } catch (Exception ignored) {
        }
        if (auth == null) {
            try {
                auth = AuthenticationInfo.getCurrent();
            } catch (Exception ignored) {
            }
        }
        BigDecimal userId = (auth != null && auth.getUserID() != null) ? auth.getUserID() : BigDecimal.ZERO;

        try {
            JapeSessionContext.putProperty("usuario_logado", userId);
            JapeSessionContext.putProperty("authInfo", auth);
            JapeSessionContext.putProperty("br.com.sankhya.com.CentralCompraVenda", Boolean.TRUE);
            JapeSessionContext.putProperty("calcular.outros.impostos", "false");
            JapeSession.putProperty("br.com.sankhya.com.CentralCompraVenda", Boolean.TRUE);

            try {
                System.out.println("[AdicionarItemAction] Recalculando impostos e totalizando nota #" + nuNota + "...");
                ImpostosHelpper impostos = new ImpostosHelpper();
                impostos.setForcarRecalculo(true);
                impostos.carregarNota(nuNota);
                impostos.calcularImpostos(nuNota);
                impostos.totalizarNota(nuNota);
                impostos.salvarNota();
                System.out.println("[AdicionarItemAction] Impostos e totalizadores da TGFCAB atualizados e salvos com sucesso!");
            } catch (Exception eImp) {
                System.err.println("[AdicionarItemAction] Aviso ao totalizar impostos: " + eImp.getMessage());
            }

            try {
                System.out.println("[AdicionarItemAction] Regenerando financeiro para nota #" + nuNota + "...");
                CentralFinanceiro fin = new CentralFinanceiro();
                fin.inicializaNota(nuNota);
                fin.refazerFinanceiro();
                System.out.println("[AdicionarItemAction] Parcelas financeiras regeneradas com sucesso!");
            } catch (Exception eFin) {
                System.err.println("[AdicionarItemAction] Aviso ao refazer financeiro: " + eFin.getMessage());
            }
        } finally {
            JapeSessionContext.removeProperty("usuario_logado");
            JapeSessionContext.removeProperty("authInfo");
            JapeSessionContext.removeProperty("br.com.sankhya.com.CentralCompraVenda");
            JapeSessionContext.removeProperty("calcular.outros.impostos");
            JapeSession.removeProperty("br.com.sankhya.com.CentralCompraVenda");
        }
    }

    private BigDecimal extrairNuNota(ContextoAcao contexto) {
        // Tentar via linhas selecionadas
        Registro[] linhas = contexto.getLinhas();
        if (linhas != null && linhas.length > 0) {
            Object val = linhas[0].getCampo("NUNOTA");
            if (val != null) {
                return new BigDecimal(val.toString());
            }
        }

        // Tentar via parâmetros
        return extrairParametroBigDecimal(contexto, "NUNOTA");
    }

    private BigDecimal extrairParametroBigDecimal(ContextoAcao contexto, String paramName) {
        Object val = contexto.getParam(paramName);
        if (val != null) {
            String strVal = val.toString().trim();
            if (!strVal.isEmpty()) {
                return new BigDecimal(strVal);
            }
        }

        // Fail-safe via ServiceContext (Regra 6 do Manifesto Sankhya)
        try {
            br.com.sankhya.ws.ServiceContext sCtx = br.com.sankhya.ws.ServiceContext.getCurrent();
            if (sCtx != null && sCtx.getJsonRequestBody() != null) {
                com.google.gson.JsonObject body = sCtx.getJsonRequestBody();
                if (body.has("params") && body.getAsJsonObject("params").has(paramName)) {
                    return new BigDecimal(body.getAsJsonObject("params").get(paramName).getAsString());
                } else if (body.has(paramName)) {
                    return new BigDecimal(body.get(paramName).getAsString());
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Ajusta a quantidade informada para respeitar o agrupamento mínimo (TGFPRO.AGRUPMIN).
     * Se qtd for menor que agrupMin, assume agrupMin.
     * Se qtd não for múltiplo exato, arredonda para cima no próximo múltiplo.
     */
    public static BigDecimal ajustarQuantidadeAgrupMin(BigDecimal qtd, BigDecimal agrupMin) {
        if (agrupMin == null || agrupMin.compareTo(BigDecimal.ONE) <= 0) {
            return (qtd != null && qtd.compareTo(BigDecimal.ZERO) > 0) ? qtd : BigDecimal.ONE;
        }
        if (qtd == null || qtd.compareTo(agrupMin) < 0) {
            return agrupMin;
        }
        BigDecimal[] divRem = qtd.divideAndRemainder(agrupMin);
        if (divRem[1].compareTo(BigDecimal.ZERO) != 0) {
            return divRem[0].add(BigDecimal.ONE).multiply(agrupMin);
        }
        return qtd;
    }
}
