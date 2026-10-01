package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.util.ConstrutorPopUp;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Testes Unitários de ConstrutorPopUp.
 * Valida injeção de scripts, fechamento seguro de Streams e proteção contra regex crash ($ e \).
 */
public class ConstrutorPopUpTest {

    @Test
    public void testMontagemBasicaPopUp() throws Exception {
        ConstrutorPopUp popup = new ConstrutorPopUp();
        popup.setTitle("Assistente de Vendas Sankhya");
        popup.setWidth(500);
        popup.setHeight(700);

        String htmlMock = "<div class=\"corpo-teste\">Conteúdo do Assistente</div>";
        InputStream stream = new ByteArrayInputStream(htmlMock.getBytes("UTF-8"));
        popup.setHtmlFile(stream);

        String resultado = popup.buildPopUp();

        assertNotNull(resultado);
        assertTrue(resultado.contains("Assistente de Vendas Sankhya"));
        assertTrue(resultado.contains("500px"));
        assertTrue(resultado.contains("700px"));
        assertTrue(resultado.contains("Conteúdo do Assistente"));
    }

    @Test
    public void testInjecaoVariaveisComCifraoEEscape() throws Exception {
        ConstrutorPopUp popup = new ConstrutorPopUp();
        popup.setTitle("Teste Caracteres");

        // String contendo R$ e barras que antes quebravam no replaceAll com regex group error
        String valorComCifrao = "PRODUTO TESTE - VALOR: R$ 1.250,00 (DESCONTO $10)";
        String jsonComAspasEBarra = "{\"nome\": \"Peça 1/2\\\"\", \"obs\": \"Linha 1\\nLinha 2\"}";

        popup.addVariable("dadosProduto", valorComCifrao);
        popup.addVariable("dadosJson", jsonComAspasEBarra);
        popup.addVariable("nuNota", 98765L);
        popup.addVariable("flagNula", null);

        String resultado = popup.buildPopUp();

        assertNotNull(resultado);
        // Garantir que var dadosProduto foi injetado sem estourar IllegalArgumentException
        assertTrue("Deve conter a variável injetada com R$", resultado.contains("var dadosProduto='PRODUTO TESTE - VALOR: R$ 1.250,00 (DESCONTO $10)';"));
        assertTrue("Deve conter a variável nuNota numérica", resultado.contains("var nuNota=98765;"));
        assertTrue("Deve conter a variável nula", resultado.contains("var flagNula=null;"));
        assertTrue("Deve conter a variável JSON com escape seguro", resultado.contains("var dadosJson="));
    }

    @Test
    public void testFechamentoDeRecursosStream() throws Exception {
        ConstrutorPopUp popup = new ConstrutorPopUp();
        byte[] dados = "<div>HTML do Painel</div>".getBytes("UTF-8");
        ByteArrayInputStream is = new ByteArrayInputStream(dados);
        popup.setHtmlFile(is);

        String resultado = popup.buildPopUp();
        assertNotNull(resultado);
        assertTrue(resultado.contains("HTML do Painel"));
    }
}
