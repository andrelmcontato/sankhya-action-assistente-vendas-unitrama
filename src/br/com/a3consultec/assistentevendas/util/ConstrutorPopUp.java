package br.com.a3consultec.assistentevendas.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construtor de PopUp HTML5/CSS3/AngularJS para o SankhyaW.
 * Baseado no padrão comprovado de produção do VinculaFinanceiro.
 * Sanitiza rigorosamente quebras de linha para evitar injeção de <br />
 * pelo mecanismo nativo do SankhyaW.
 */
public class ConstrutorPopUp {

    private String defaultCss = "<style id=\"asst-custom-styles\"> "
            + "/* config: ${width}px x ${height}px */ "
            + "#myPopUp { display: none !important; } "
            + ".asst-modal-host, .modal:has(.asst-root), .modal:has(#assistente-root) { "
            + "pointer-events: none !important; background: transparent !important; border: none !important; box-shadow: none !important; } "
            + ".asst-modal-host .modal-dialog, .modal:has(.asst-root) .modal-dialog, .modal:has(#assistente-root) .modal-dialog { "
            + "position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; "
            + "max-width: 100vw !important; max-height: 100vh !important; margin: 0 !important; padding: 0 !important; "
            + "background: transparent !important; border: none !important; box-shadow: none !important; "
            + "pointer-events: none !important; transform: none !important; -webkit-transform: none !important; } "
            + ".asst-modal-host .modal-content, .modal:has(.asst-root) .modal-content, .modal:has(#assistente-root) .modal-content { "
            + "position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; "
            + "background: transparent !important; border: none !important; box-shadow: none !important; "
            + "border-radius: 0 !important; pointer-events: none !important; } "
            + ".asst-modal-host .modal-header, .asst-modal-host .modal-footer, "
            + ".modal:has(.asst-root) .modal-header, .modal:has(.asst-root) .modal-footer, "
            + ".modal:has(#assistente-root) .modal-header, .modal:has(#assistente-root) .modal-footer { display: none !important; } "
            + ".asst-modal-host .modal-body, .modal:has(.asst-root) .modal-body, .modal:has(#assistente-root) .modal-body { "
            + "position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; "
            + "margin: 0 !important; padding: 0 !important; background: transparent !important; border: none !important; "
            + "pointer-events: none !important; overflow: visible !important; } "
            + ".asst-modal-backdrop-hidden, .modal-backdrop:has(+ .modal .asst-root), .modal-backdrop:has(+ .modal #assistente-root) { "
            + "display: none !important; pointer-events: none !important; opacity: 0 !important; width: 0 !important; height: 0 !important; } "
            + ".asst-root { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; pointer-events: none !important; z-index: 1050 !important; } "
            + ".asst-balloon, .asst-fab, .asst-drawer, .asst-tooltip, .asst-toast { pointer-events: auto !important; } ";

    private String defaultJs = "</style><div id=\"myPopUp\"><script type=\"text/javascript\"> "
            + "${init}; "
            + "try { var sc = angular.element($(\"#myPopUp\")).scope(); if(sc){ sc.$popupTitle=\"${title}\"; } } catch(eSc){} ";

    private String title = "";
    private InputStream jsFile;
    private InputStream htmlFile;
    private InputStream cssFile;
    private int width = 760;
    private int height = 520;
    private Map<String, Object> variable = new LinkedHashMap<String, Object>();

    public void setTitle(String title) {
        this.title = title;
    }

    public void setJsFile(InputStream jsFile) {
        this.jsFile = jsFile;
    }

    public void setHtmlFile(InputStream htmlFile) {
        this.htmlFile = htmlFile;
    }

    public void setCssFile(InputStream cssFile) {
        this.cssFile = cssFile;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void addVariable(String key, Object value) {
        this.variable.put(key, value);
    }

    public String buildPopUp() throws Exception {
        return builPopUp();
    }

    public String builPopUp() throws Exception {
        StringBuffer strPopUp = new StringBuffer();
        strPopUp.append(this.defaultCss);
        if (this.cssFile != null) {
            this.readFile(strPopUp, this.cssFile, false);
        }
        strPopUp.append(this.defaultJs);
        if (this.jsFile != null) {
            this.readFile(strPopUp, this.jsFile, true);
        }
        strPopUp.append("</script></div> ");
        if (this.htmlFile != null) {
            this.readFile(strPopUp, this.htmlFile, false);
        }

        String strPopup = strPopUp.toString();
        StringBuffer strVariable = new StringBuffer();
        strVariable.append("window.__asstData=window.__asstData||{}; ");
        for (Map.Entry<String, Object> entry : this.variable.entrySet()) {
            String value = "";
            if (entry.getValue() == null) {
                value = "null";
            } else if (entry.getValue() instanceof String) {
                String raw = entry.getValue().toString();
                String escaped = raw.replace("\\", "\\\\")
                                    .replace("'", "\\'")
                                    .replace("\r", " ")
                                    .replace("\n", " ");
                value = "'" + escaped + "'";
            } else {
                value = entry.getValue().toString();
            }
            strVariable.append("var " + entry.getKey() + "=" + value + ";");
            strVariable.append("window.__asstData['" + entry.getKey() + "']=" + value + ";");
        }

        strPopup = strPopup.replace("${title}", this.title != null ? this.title : "");
        strPopup = strPopup.replace("${width}", String.valueOf(this.width));
        strPopup = strPopup.replace("${height}", String.valueOf(this.height));
        strPopup = strPopup.replace("${init}", strVariable.toString());

        // Higienização final mandatória: eliminar qualquer resquício de quebra de linha
        // para que o replaceLineBreak do Sankhya (split('\n').join('<br />')) seja um no-op absoluto.
        strPopup = strPopup.replace("\r", " ").replace("\n", " ");
        return strPopup;
    }

    private void readFile(StringBuffer strbuffer, InputStream inputFile, boolean isJs) throws Exception {
        if (inputFile == null) return;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputFile, "UTF-8"))) {
            String line;
            while ((line = in.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (isJs && trimmed.startsWith("//")) continue;
                strbuffer.append(trimmed).append(" ");
            }
        }
    }
}
