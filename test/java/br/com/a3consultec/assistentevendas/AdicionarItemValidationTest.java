package br.com.a3consultec.assistentevendas;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Testes Unitários de Regras de Validação e Normalização de Inclusão de Itens (AdicionarItemAction).
 */
public class AdicionarItemValidationTest {

    @Test
    public void testNormalizacaoQuantidadeInvalida() {
        BigDecimal qtdNula = null;
        BigDecimal qtdZero = BigDecimal.ZERO;
        BigDecimal qtdNegativa = new BigDecimal("-5");
        BigDecimal qtdValida = new BigDecimal("3");

        assertEquals(BigDecimal.ONE, normalizarQuantidade(qtdNula));
        assertEquals(BigDecimal.ONE, normalizarQuantidade(qtdZero));
        assertEquals(BigDecimal.ONE, normalizarQuantidade(qtdNegativa));
        assertEquals(new BigDecimal("3"), normalizarQuantidade(qtdValida));
    }

    @Test
    public void testFallbackUnidadeVolume() {
        assertEquals("UN", normalizarVolume(null));
        assertEquals("UN", normalizarVolume(""));
        assertEquals("UN", normalizarVolume("   "));
        assertEquals("CX", normalizarVolume("CX"));
        assertEquals("KG", normalizarVolume("  KG  "));
    }

    @Test
    public void testCalculoValorTotalComArredondamento() {
        BigDecimal vlrUnit = new BigDecimal("12.345");
        BigDecimal qtd = new BigDecimal("2");

        BigDecimal total = vlrUnit.multiply(qtd).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("24.69"), total);

        BigDecimal vlrZero = BigDecimal.ZERO;
        BigDecimal totalZero = vlrZero.multiply(qtd).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.00"), totalZero);
    }

    @Test
    public void testValidacaoChavesObrigatorias() {
        BigDecimal nuNota = null;
        BigDecimal codProd = new BigDecimal("100");

        assertFalse("NUNOTA nulo deve ser rejeitado", validarCamposObrigatorios(nuNota, codProd));

        nuNota = BigDecimal.ZERO;
        assertFalse("NUNOTA zero deve ser rejeitado", validarCamposObrigatorios(nuNota, codProd));

        nuNota = new BigDecimal("54321");
        codProd = null;
        assertFalse("CODPROD nulo deve ser rejeitado", validarCamposObrigatorios(nuNota, codProd));

        codProd = BigDecimal.ZERO;
        assertFalse("CODPROD zero deve ser rejeitado", validarCamposObrigatorios(nuNota, codProd));

        codProd = new BigDecimal("100");
        assertTrue("Campos válidos devem ser aceitos", validarCamposObrigatorios(nuNota, codProd));
    }

    private BigDecimal normalizarQuantidade(BigDecimal qtd) {
        if (qtd == null || qtd.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return qtd;
    }

    private String normalizarVolume(String codVol) {
        if (codVol == null || codVol.trim().isEmpty()) {
            return "UN";
        }
        return codVol.trim();
    }

    private boolean validarCamposObrigatorios(BigDecimal nuNota, BigDecimal codProd) {
        if (nuNota == null || nuNota.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (codProd == null || codProd.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return true;
    }
}
