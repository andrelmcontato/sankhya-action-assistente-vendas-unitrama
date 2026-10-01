package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.model.AssistenteResponseDTO;
import br.com.a3consultec.assistentevendas.model.SugestaoProdutoDTO;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;

/**
 * Testes Unitários do DTO AssistenteResponseDTO e sua serialização JSON com Gson.
 */
public class AssistenteResponseDTOTest {

    private Gson gson = new Gson();

    @Test
    public void testSerializacaoJsonSucesso() {
        AssistenteResponseDTO resp = new AssistenteResponseDTO();
        resp.setSucesso(true);
        resp.setMensagem("Sugestões carregadas.");
        resp.setNuNota(new BigDecimal("12345"));
        resp.setCodParc(new BigDecimal("999"));
        resp.setNomeParc("MERCADO MODELO LTDA");
        resp.setQtdItensCarrinho(2);

        SugestaoProdutoDTO s1 = new SugestaoProdutoDTO();
        s1.setCodProd(new BigDecimal("5001"));
        s1.setDescrProd("CHOCOLATE EM PÓ 1KG");
        s1.setVlrVenda(new BigDecimal("19.50"));
        s1.setTagAfinidade("3.2x mais afinidade");
        resp.getSugestoes().add(s1);

        SugestaoProdutoDTO h1 = new SugestaoProdutoDTO();
        h1.setCodProd(new BigDecimal("2001"));
        h1.setDescrProd("LEITE CONDENSADO 395G");
        h1.setMotivo("Comprado 8 vezes recentemente");
        resp.getItensFrequentes().add(h1);

        String json = gson.toJson(resp);
        assertNotNull(json);
        assertTrue(json.contains("\"sucesso\":true"));
        assertTrue(json.contains("\"nuNota\":12345"));
        assertTrue(json.contains("\"CHOCOLATE EM P\\u00D3 1KG\"") || json.contains("CHOCOLATE"));
        assertTrue(json.contains("\"LEITE CONDENSADO 395G\""));

        AssistenteResponseDTO deserializado = gson.fromJson(json, AssistenteResponseDTO.class);
        assertNotNull(deserializado);
        assertTrue(deserializado.isSucesso());
        assertEquals(new BigDecimal("12345"), deserializado.getNuNota());
        assertEquals(1, deserializado.getSugestoes().size());
        assertEquals(1, deserializado.getItensFrequentes().size());
        assertEquals(new BigDecimal("5001"), deserializado.getSugestoes().get(0).getCodProd());
    }

    @Test
    public void testRespostaErro() {
        AssistenteResponseDTO resp = new AssistenteResponseDTO();
        resp.setSucesso(false);
        resp.setMensagem("Número da nota inválido.");

        assertFalse(resp.isSucesso());
        assertEquals("Número da nota inválido.", resp.getMensagem());
        assertTrue(resp.getSugestoes().isEmpty());
        assertTrue(resp.getItensFrequentes().isEmpty());
    }
}
