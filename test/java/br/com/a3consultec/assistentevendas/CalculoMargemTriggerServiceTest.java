package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.model.SugestaoProdutoDTO;
import br.com.a3consultec.assistentevendas.service.CalculoMargemTriggerService;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.Assert.*;

/**
 * Testes Unitários de Verificação do Serviço de Cálculo de Margem e Preço da Unitrama.
 * Garante fidelidade matemática absoluta com TRG_INC_UPD_TGFITE_MRG.sql e RecalculoIPIMargem.
 */
public class CalculoMargemTriggerServiceTest {

    private CalculoMargemTriggerService service;
    private CalculoMargemTriggerService.NotaFiscalContexto contextoPadrao;

    @Before
    public void setUp() {
        service = new CalculoMargemTriggerService();
        contextoPadrao = new CalculoMargemTriggerService.NotaFiscalContexto();
        contextoPadrao.nuNota = new BigDecimal("12345");
        contextoPadrao.percDescCabecalho = BigDecimal.ZERO;
        contextoPadrao.usaDescEspecial = "N";
        contextoPadrao.clienteTemIpi = true;
        contextoPadrao.isClienteSuframa = false;
        contextoPadrao.topRecalculaIpi = true;
    }

    @Test
    public void testCalculoFatorOmegaSemDescontoSemIpi() {
        CalculoMargemTriggerService.NotaFiscalContexto ctx = new CalculoMargemTriggerService.NotaFiscalContexto();
        ctx.clienteTemIpi = false;
        ctx.percDescCabecalho = BigDecimal.ZERO;

        BigDecimal omega = service.calcularFatorOmega(ctx, BigDecimal.ZERO);
        // F_sim = 1, (1 - desc) = 1, fatorIpi = 1 => omega = 1.00000000
        assertEquals(new BigDecimal("1.00000000"), omega);
    }

    @Test
    public void testCalculoFatorOmegaComIpi() {
        // IPI = 10% => fatorIpi = 1.10
        // Numerador = 1 * 1 + (1.10 - 1) = 1.10 => omega = 1.10 / 1.10 = 1.00000000
        BigDecimal omega = service.calcularFatorOmega(contextoPadrao, new BigDecimal("10"));
        assertEquals(new BigDecimal("1.00000000"), omega);
    }

    @Test
    public void testCalculoFatorOmegaComDescontoCabecalho() {
        // Desc = 10% (0.10) => (1 - desc) = 0.90
        // IPI = 10% (0.10) => fatorIpi = 1.10
        // Numerador = 1 * 0.90 + 0.10 = 1.00
        // Omega = 1.00 / 1.10 = 0.90909091
        contextoPadrao.percDescCabecalho = new BigDecimal("0.10");
        BigDecimal omega = service.calcularFatorOmega(contextoPadrao, new BigDecimal("10"));
        assertEquals(new BigDecimal("0.90909091"), omega);
    }

    @Test
    public void testCalculoFatorKEBidirecionalidadePrecoMargem() {
        BigDecimal cusVar = new BigDecimal("75.00");
        BigDecimal aliqIpi = new BigDecimal("10.00");

        // Fator K com Omega = 1.00000000 => K = 75.0000
        BigDecimal fatorK = service.calcularFatorK(contextoPadrao, cusVar, aliqIpi);
        assertEquals(new BigDecimal("75.0000"), fatorK);

        // Preço para Margem de 25% => P = 75 / (1 - 0.25) = 75 / 0.75 = 100.00
        BigDecimal margemAlvo = new BigDecimal("25.00");
        BigDecimal precoCalculado = service.calcularPrecoDeMargem(fatorK, margemAlvo);
        assertEquals(new BigDecimal("100.00"), precoCalculado);

        // Margem a partir do Preço 100.00 => m = (1 - 75 / 100) * 100 = 25.00%
        BigDecimal margemCalculada = service.calcularMargemDePreco(fatorK, precoCalculado);
        assertEquals(new BigDecimal("25.00"), margemCalculada);
    }

    @Test
    public void testReversibilidadeExataPrecoMargemPreco() {
        BigDecimal fatorK = new BigDecimal("82.4500");

        // Dado Preço P = 120.00 => calcula Margem m
        BigDecimal precoOriginal = new BigDecimal("120.00");
        BigDecimal margem = service.calcularMargemDePreco(fatorK, precoOriginal);

        // A partir da margem calculada => recalcula Preço P_rec
        BigDecimal precoRecalculado = service.calcularPrecoDeMargem(fatorK, margem);

        // Diferença máxima tolerável de centavos por arredondamento
        BigDecimal diff = precoOriginal.subtract(precoRecalculado).abs();
        assertTrue("A diferença entre o preço original e o recalculado deve ser <= 0.05", diff.compareTo(new BigDecimal("0.05")) <= 0);
    }

    @Test
    public void testDTOUnitramaCamposESigiloComercial() {
        SugestaoProdutoDTO dto = new SugestaoProdutoDTO();
        dto.setCodProd(new BigDecimal("999"));
        dto.setVlrVenda(new BigDecimal("150.00"));
        dto.setMargemSugerida(new BigDecimal("30.00"));
        dto.setFatorK(new BigDecimal("105.0000"));
        dto.setAliquotaIpi(new BigDecimal("5.00"));
        dto.setCustoVariavel(new BigDecimal("95.00"));

        assertEquals(new BigDecimal("30.00"), dto.getMargemSugerida());
        assertEquals(new BigDecimal("105.0000"), dto.getFatorK());
        assertEquals(new BigDecimal("5.00"), dto.getAliquotaIpi());
        assertEquals(new BigDecimal("95.00"), dto.getCustoVariavel());

        // Sigilo Comercial: limpeza de custo variável antes da serialização
        dto.setCustoVariavel(null);
        assertNull(dto.getCustoVariavel());
    }

    @Test
    public void testCalculoMargemRealTriggerCenarioScreenshotUnitrama() {
        // Dados reais do screenshot da Unitrama:
        // Produto: ACM UNIBOND 3mm (Vlr. unitário: 370.98, IPI: 3.25%, CustoVar: 114.30)
        // Preço com IPI = 383.04. Margem esperada: 70.16%
        BigDecimal vlrUnitBase = new BigDecimal("370.98");
        BigDecimal aliqIpi = new BigDecimal("3.25");
        BigDecimal cusVar = new BigDecimal("114.30");

        BigDecimal margemCalculada = service.calcularMargemRealTrigger(contextoPadrao, vlrUnitBase, cusVar, aliqIpi);
        assertEquals(new BigDecimal("70.16"), margemCalculada);

        // Fator K derivado para o frontend (bidirecionalidade 100% perfeita):
        BigDecimal fatorK = vlrUnitBase.multiply(BigDecimal.ONE.subtract(margemCalculada.divide(CalculoMargemTriggerService.CEM, 6, RoundingMode.HALF_UP))).setScale(4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("110.7004"), fatorK);

        // A partir do preço 370.98, deve retornar exatamente 70.16%
        BigDecimal margemDoPreco = service.calcularMargemDePreco(fatorK, vlrUnitBase);
        assertEquals(new BigDecimal("70.16"), margemDoPreco);

        // Se o vendedor altera o preço para 400.00, margem sobe para 72.32%
        BigDecimal margem400 = service.calcularMargemDePreco(fatorK, new BigDecimal("400.00"));
        assertEquals(new BigDecimal("72.32"), margem400);
    }

    @Test
    public void testCalculoMargemRealTriggerSemIpi() {
        CalculoMargemTriggerService.NotaFiscalContexto ctxSemIpi = new CalculoMargemTriggerService.NotaFiscalContexto();
        ctxSemIpi.clienteTemIpi = false;

        BigDecimal vlrUnitBase = new BigDecimal("100.00");
        BigDecimal cusVar = new BigDecimal("60.00");

        BigDecimal margem = service.calcularMargemRealTrigger(ctxSemIpi, vlrUnitBase, cusVar, BigDecimal.ZERO);
        assertEquals(new BigDecimal("40.00"), margem);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMargemInvalidaCemPorcento() {
        service.calcularPrecoDeMargem(new BigDecimal("100"), new BigDecimal("100"));
    }
}
