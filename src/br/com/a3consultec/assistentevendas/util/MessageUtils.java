package br.com.a3consultec.assistentevendas.util;

import br.com.sankhya.ws.ServiceContext;

/**
 * Utilitário de Mensageria e Modais do SankhyaW.
 * Define o status 2 no ServiceContext para que o Sankhya renderize
 * o conteúdo HTML como um modal interativo do AngularJS.
 */
public class MessageUtils {

    private MessageUtils() {
    }

    public static void showInfo(String message) {
        ServiceContext ctx = ServiceContext.getCurrent();
        if (ctx == null) {
            System.err.println("[AssistenteVendas] ServiceContext não inicializado abortando showInfo");
            return;
        }
        ctx.setStatus(2);
        ctx.setStatusMessage(message);
    }

    public static void showError(String message) {
        ServiceContext ctx = ServiceContext.getCurrent();
        if (ctx != null) {
            ctx.setStatus(0);
            ctx.setStatusMessage(message);
        }
    }
}
