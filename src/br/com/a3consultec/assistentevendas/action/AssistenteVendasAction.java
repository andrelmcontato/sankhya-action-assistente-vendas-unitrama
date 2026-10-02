package br.com.a3consultec.assistentevendas.action;

import br.com.a3consultec.assistentevendas.model.AssistenteResponseDTO;
import br.com.a3consultec.assistentevendas.service.AssistenteVendasService;
import br.com.a3consultec.assistentevendas.util.ConstrutorPopUp;
import br.com.a3consultec.assistentevendas.util.MessageUtils;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;
import com.google.gson.Gson;
import org.jdom.Element;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * Botão de Ação para a Central de Vendas do SankhyaW.
 * Renderiza o Assistente de Vendas com modal AngularJS nativo
 * baseado no padrão de produção comprovado do VinculaFinanceiro.
 */
public class AssistenteVendasAction implements AcaoRotinaJava {

    private AssistenteVendasService service = new AssistenteVendasService();
    private Gson gson = new Gson();

    @Override
    public void doAction(ContextoAcao contexto) throws Exception {
        Object body = ServiceContext.getCurrent() != null ? ServiceContext.getCurrent().getJsonRequestBody() : null;
        String bodyStr = body != null ? body.toString() : "";

        Object operacaoObj = contexto.getParam("OPERACAO");
        String operacao = operacaoObj != null ? operacaoObj.toString() : null;

        if (operacao == null) {
            try {
                ServiceContext sCtx = ServiceContext.getCurrent();
                if (sCtx != null && sCtx.getRequestBody() != null) {
                    Element reqBody = sCtx.getRequestBody();
                    Element javaCall = reqBody.getChild("javaCall");
                    if (javaCall != null) {
                        Element params = javaCall.getChild("params");
                        if (params != null) {
                            for (Object cObj : params.getChildren()) {
                                Element pElem = (Element) cObj;
                                if ("OPERACAO".equalsIgnoreCase(pElem.getAttributeValue("paramName"))
                                        || "OPERACAO".equalsIgnoreCase(pElem.getName())) {
                                    operacao = pElem.getTextTrim();
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (operacao == null && bodyStr.contains("\"OPERACAO\"")) {
            try {
                operacao = bodyStr.split("\"OPERACAO\"")[1].split(":")[1].split("\"")[1];
            } catch (Exception ignored) {
            }
        }

        System.out.println("====================================================================");
        System.out.println("[AssistenteVendas Unitrama] doAction DISPARADO! Operação=" + operacao);
        System.out.println("====================================================================");

        // Rota 1: Inclusão direta de item sugerido via clique no "+ Adicionar"
        if ("ADICIONAR_ITEM".equalsIgnoreCase(operacao)) {
            processarInclusaoItem(contexto, bodyStr);
            return;
        }

        // Rota 1.1: Recálculo de impostos e totais da nota
        if ("RECALCULAR_TOTAIS".equalsIgnoreCase(operacao)) {
            processarRecalculoTotais(contexto, bodyStr);
            return;
        }

        // Rota 1.2: Consulta dinâmica de sugestões (atualização em tempo de execução)
        if ("OBTER_SUGESTOES".equalsIgnoreCase(operacao)) {
            processarObterSugestoes(contexto, bodyStr);
            return;
        }

        // Rota 2: Abertura do Modal do Assistente na Central de Vendas
        processarAberturaAssistente(contexto, bodyStr);
    }

    private void processarObterSugestoes(ContextoAcao contexto, String bodyStr) throws Exception {
        BigDecimal nuNota = extrairParametroBigDecimal(contexto, "NUNOTA");
        if (nuNota == null && bodyStr != null && bodyStr.contains("\"NUNOTA\"")) {
            nuNota = extrairDeString(bodyStr, "NUNOTA");
        }
        if (nuNota == null) {
            nuNota = extrairNuNota(contexto);
        }
        if (nuNota == null) {
            contexto.mostraErro("Não foi possível identificar o número do pedido (NUNOTA).");
            return;
        }

        AssistenteResponseDTO response = service.obterSugestoes(nuNota);
        String jsonSugestoes = gson.toJson(response.getSugestoes());
        contexto.setMensagemRetorno(jsonSugestoes);
    }

    private void processarRecalculoTotais(ContextoAcao contexto, String bodyStr) {
        BigDecimal nuNota = extrairParametroBigDecimal(contexto, "NUNOTA");
        if (nuNota == null && bodyStr != null && bodyStr.contains("\"NUNOTA\"")) {
            nuNota = extrairDeString(bodyStr, "NUNOTA");
        }
        if (nuNota == null) {
            nuNota = extrairNuNota(contexto);
        }
        if (nuNota != null) {
            AdicionarItemAction.recalcularTotaisEFinanceiro(nuNota);
        }
    }

    private void processarInclusaoItem(ContextoAcao contexto, String bodyStr) throws Exception {
        BigDecimal nuNota = extrairParametroBigDecimal(contexto, "NUNOTA");
        if (nuNota == null && bodyStr != null && bodyStr.contains("\"NUNOTA\"")) {
            nuNota = extrairDeString(bodyStr, "NUNOTA");
        }
        if (nuNota == null) {
            nuNota = extrairNuNota(contexto);
        }

        if (nuNota == null) {
            contexto.mostraErro("Não foi possível identificar o número do pedido (NUNOTA).");
            return;
        }

        BigDecimal codProd = extrairParametroBigDecimal(contexto, "CODPROD");
        if (codProd == null && bodyStr != null && bodyStr.contains("\"CODPROD\"")) {
            codProd = extrairDeString(bodyStr, "CODPROD");
        }

        if (codProd == null) {
            contexto.mostraErro("Código do produto sugerido (CODPROD) não informado.");
            return;
        }

        BigDecimal qtdNeg = extrairParametroBigDecimal(contexto, "QTDNEG");
        if (qtdNeg == null && bodyStr != null && bodyStr.contains("\"QTDNEG\"")) {
            qtdNeg = extrairDeString(bodyStr, "QTDNEG");
        }
        if (qtdNeg == null || qtdNeg.compareTo(BigDecimal.ZERO) <= 0) {
            qtdNeg = BigDecimal.ONE;
        }

        BigDecimal vlrUnit = extrairParametroBigDecimal(contexto, "VLRUNIT");
        if (vlrUnit == null && bodyStr != null && bodyStr.contains("\"VLRUNIT\"")) {
            vlrUnit = extrairDeString(bodyStr, "VLRUNIT");
        }

        try {
            System.out.println("[AssistenteVendas Unitrama] Incluindo item " + codProd + " (Qtd: " + qtdNeg + ", Preço: " + vlrUnit + ") no Pedido #" + nuNota + "...");
            String descrProd = AdicionarItemAction.incluirItemNoPedido(nuNota, codProd, qtdNeg, vlrUnit);
            System.out.println("[AssistenteVendas Unitrama] Item " + codProd + " (" + descrProd + ") processado com sucesso.");
            contexto.setMensagemRetorno("Produto " + descrProd + " adicionado ao pedido!");
        } catch (Exception e) {
            System.err.println("[AssistenteVendas Unitrama] Erro na inclusão do item: " + e.getMessage());
            contexto.mostraErro(e.getMessage());
        }
    }

    private void processarAberturaAssistente(ContextoAcao contexto, String bodyStr) throws Exception {
        if (contexto.getLinhas() != null && contexto.getLinhas().length > 1) {
            throw new Exception("Selecione apenas um pedido por vez para abrir o Assistente de Vendas.");
        }

        BigDecimal nuNota = extrairNuNota(contexto);
        if (nuNota == null) {
            contexto.mostraErro("Selecione um pedido na Central de Vendas para abrir o Assistente.");
            return;
        }

        // 1. Extrair actionID e resourceID da requisição com tripla contingência
        BigDecimal actionID = BigDecimal.ZERO;
        String resourceID = "br.com.sankhya.com.mov.CentralNotas";

        // Contingência 1: Extrair diretamente do XML RequestBody do ServiceContext
        try {
            ServiceContext sCtx = ServiceContext.getCurrent();
            if (sCtx != null && sCtx.getRequestBody() != null) {
                Element reqBody = sCtx.getRequestBody();
                Element javaCall = reqBody.getChild("javaCall");
                if (javaCall != null && javaCall.getAttributeValue("actionID") != null) {
                    String val = javaCall.getAttributeValue("actionID").replaceAll("[^0-9]", "");
                    if (!val.isEmpty()) {
                        actionID = new BigDecimal(val);
                        System.out.println("[AssistenteVendasAction] actionID extraído do ServiceContext XML: " + actionID);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Contingência 2: Extrair do JsonRequestBody (bodyStr)
        if (actionID.compareTo(BigDecimal.ZERO) == 0 && !bodyStr.isEmpty()) {
            try {
                if (bodyStr.contains("\"actionID\"")) {
                    String[] parts = bodyStr.split("\"actionID\"");
                    if (parts.length > 1) {
                        String val = parts[1].split(",")[0].replaceAll("[^0-9]", "");
                        if (!val.isEmpty()) {
                            actionID = new BigDecimal(val);
                            System.out.println("[AssistenteVendasAction] actionID extraído do JsonRequestBody: " + actionID);
                        }
                    }
                }
                if (bodyStr.contains("\"resourceID\"")) {
                    String[] parts = bodyStr.split("\"resourceID\"");
                    if (parts.length > 1) {
                        String val = parts[1].split(",")[0].replaceAll("[\":\\s}]", "");
                        if (!val.isEmpty()) {
                            resourceID = val;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Contingência 3: Localizar dinamicamente o IDBTNACAO via JAPE na entidade BotaoAcao
        if (actionID.compareTo(BigDecimal.ZERO) == 0) {
            actionID = buscarActionIDPorJape();
        }

        // 2. Obter sugestões de inteligência de vendas
        AssistenteResponseDTO response = service.obterSugestoes(nuNota);

        // 3. Montar PopUp com visual nativo
        ConstrutorPopUp construtor = new ConstrutorPopUp();
        construtor.setWidth(780);
        construtor.setHeight(520);
        construtor.setTitle("Unitrama - Sugestões de Vendas");

        construtor.addVariable("nunota", nuNota);
        construtor.addVariable("actionID", actionID != null ? actionID : BigDecimal.ZERO);
        construtor.addVariable("resourceID", resourceID);
        construtor.addVariable("nomeParc", response.getNomeParc() != null ? response.getNomeParc() : "");
        construtor.addVariable("sugestoes", gson.toJson(response.getSugestoes()));

        InputStream cssStream = getResourceStream("/popUp/Assistente.css");
        InputStream jsStream = getResourceStream("/popUp/Assistente.js");
        InputStream htmlStream = getResourceStream("/popUp/Assistente.html");

        if (htmlStream == null || jsStream == null) {
            throw new Exception("Recursos /popUp/Assistente.html ou Assistente.js não encontrados no pacote JAR.");
        }

        if (cssStream != null) {
            construtor.setCssFile(cssStream);
        }
        construtor.setJsFile(jsStream);
        construtor.setHtmlFile(htmlStream);

        // 4. Exibir via ServiceContext status 2 (padrão de produção comprovado do VinculaFinanceiro)
        String html = construtor.builPopUp();
        MessageUtils.showInfo(html);
        System.out.println("[AssistenteVendas Unitrama v1.0.7] PopUp montado (" + html.length() + " bytes) com " + response.getSugestoes().size() + " sugestões para Pedido #" + nuNota);
    }

    private InputStream getResourceStream(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            String clean = path.startsWith("/") ? path.substring(1) : path;
            is = getClass().getClassLoader().getResourceAsStream(clean);
        }
        if (is == null) {
            is = java.lang.invoke.MethodHandles.lookup().lookupClass().getResourceAsStream(path);
        }
        return is;
    }

    private BigDecimal extrairNuNota(ContextoAcao contexto) {
        Registro[] linhas = contexto.getLinhas();
        if (linhas != null && linhas.length > 0) {
            Object val = linhas[0].getCampo("NUNOTA");
            if (val != null) {
                try {
                    return new BigDecimal(val.toString());
                } catch (Exception ignored) {
                }
            }
        }
        return extrairParametroBigDecimal(contexto, "NUNOTA");
    }

    private BigDecimal extrairParametroBigDecimal(ContextoAcao contexto, String paramName) {
        Object val = contexto.getParam(paramName);
        if (val != null) {
            try {
                return new BigDecimal(val.toString().trim());
            } catch (Exception ignored) {
            }
        }
        // Fail-safe via ServiceContext XML requestBody
        try {
            ServiceContext sCtx = ServiceContext.getCurrent();
            if (sCtx != null && sCtx.getRequestBody() != null) {
                Element reqBody = sCtx.getRequestBody();
                Element javaCall = reqBody.getChild("javaCall");
                if (javaCall != null) {
                    Element params = javaCall.getChild("params");
                    if (params != null) {
                        for (Object cObj : params.getChildren()) {
                            Element pElem = (Element) cObj;
                            if (paramName.equalsIgnoreCase(pElem.getAttributeValue("paramName"))
                                    || paramName.equalsIgnoreCase(pElem.getName())) {
                                String text = pElem.getTextTrim();
                                if (!text.isEmpty()) {
                                    return new BigDecimal(text);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private BigDecimal extrairDeString(String bodyStr, String key) {
        try {
            String token = "\"" + key + "\"";
            if (bodyStr.contains(token)) {
                String part = bodyStr.split(token)[1];
                String val = part.split(":")[1].split("[,}]")[0].replaceAll("[\"\\s]", "");
                return new BigDecimal(val);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private BigDecimal buscarActionIDPorJape() {
        try {
            EntityFacade dwf = EntityFacadeFactory.getDWFFacade();
            Collection botoes = dwf.findByDynamicFinderAsVO(
                new FinderWrapper("BotaoAcao", " this.NOMEINSTANCIA = ? AND this.TIPO = 'RJ' ", new Object[]{"CabecalhoNota"})
            );
            if (botoes != null) {
                for (Object obj : botoes) {
                    DynamicVO vo = (DynamicVO) obj;
                    Object cfg = vo.getProperty("CONFIG");
                    if (cfg != null && cfg.toString().contains("AssistenteVendasAction")) {
                        BigDecimal id = vo.asBigDecimal("IDBTNACAO");
                        if (id != null) {
                            System.out.println("[AssistenteVendasAction] actionID localizado via JAPE BotaoAcao: " + id);
                            return id;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AssistenteVendasAction] Erro ao buscar actionID via JAPE: " + e.getMessage());
        }
        return BigDecimal.ZERO;
    }
}
