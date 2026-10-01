package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.model.SugestaoProdutoDTO;
import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;

/**
 * Testes Unitários de Integridade do DTO SugestaoProdutoDTO.
 */
public class SugestaoProdutoDTOTest {

    @Test
    public void testGettersSettersEPropriedades() {
        SugestaoProdutoDTO dto = new SugestaoProdutoDTO();
        dto.setCodProd(new BigDecimal("1050"));
        dto.setDescrProd("CAFÉ EM GRÃOS 500G");
        dto.setMarca("GOURMET");
        dto.setComplemento("TORRA MÉDIA");
        dto.setVlrVenda(new BigDecimal("29.90"));
        dto.setEstoqueDisponivel(new BigDecimal("150"));
        dto.setLift(new BigDecimal("2.5"));
        dto.setConfianca(new BigDecimal("0.45"));
        dto.setSuporte(new BigDecimal("0.08"));
        dto.setTagAfinidade("2.5x mais afinidade");
        dto.setMotivo("45% dos clientes levam junto");
        dto.setHintTexto("Frequentemente comprado com Filtro");

        assertEquals(new BigDecimal("1050"), dto.getCodProd());
        assertEquals("CAFÉ EM GRÃOS 500G", dto.getDescrProd());
        assertEquals("GOURMET", dto.getMarca());
        assertEquals("TORRA MÉDIA", dto.getComplemento());
        assertEquals(new BigDecimal("29.90"), dto.getVlrVenda());
        assertEquals(new BigDecimal("150"), dto.getEstoqueDisponivel());
        assertEquals(new BigDecimal("2.5"), dto.getLift());
        assertEquals(new BigDecimal("0.45"), dto.getConfianca());
        assertEquals(new BigDecimal("0.08"), dto.getSuporte());
        assertEquals("2.5x mais afinidade", dto.getTagAfinidade());
        assertEquals("45% dos clientes levam junto", dto.getMotivo());
        assertEquals("Frequentemente comprado com Filtro", dto.getHintTexto());
    }

    @Test
    public void testInstanciaVaziaENulos() {
        SugestaoProdutoDTO dto = new SugestaoProdutoDTO();
        assertNull(dto.getCodProd());
        assertNull(dto.getDescrProd());
        assertNull(dto.getVlrVenda());
        assertNull(dto.getEstoqueDisponivel());
        assertNull(dto.getLift());
        assertNull(dto.getConfianca());
        assertNull(dto.getHintTexto());
    }
}
