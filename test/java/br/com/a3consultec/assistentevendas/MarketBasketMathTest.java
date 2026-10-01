package br.com.a3consultec.assistentevendas;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Testes Unitários dos Cálculos Matemáticos de Market Basket Analysis (Apriori).
 * Valida Suporte, Confiança, LIFT, reciprocidade assimétrica e limites estatísticos.
 */
public class MarketBasketMathTest {

    private static final double MIN_LIFT = 1.20;
    private static final double MIN_CONFIANCA = 0.10;

    @Test
    public void testCalculoMetricasSucesso() {
        long totalPedidos = 1000;
        long freqA = 200; // Café
        long freqB = 50;  // Filtro de Papel
        long freqPar = 40; // Ambos juntos

        double suporteA = (double) freqA / (double) totalPedidos;
        double suporteB = (double) freqB / (double) totalPedidos;
        double suporteAB = (double) freqPar / (double) totalPedidos;
        double confiancaAtoB = (double) freqPar / (double) freqA;
        double liftAtoB = confiancaAtoB / suporteB;

        assertEquals(0.20, suporteA, 0.0001);
        assertEquals(0.05, suporteB, 0.0001);
        assertEquals(0.04, suporteAB, 0.0001);
        assertEquals(0.20, confiancaAtoB, 0.0001);
        assertEquals(4.00, liftAtoB, 0.0001);

        assertTrue("LIFT deve ser superior a 1.20", liftAtoB >= MIN_LIFT);
        assertTrue("Confiança deve ser superior a 10%", confiancaAtoB >= MIN_CONFIANCA);
    }

    @Test
    public void testAssimetriaDeConfianca() {
        long totalPedidos = 1000;
        long freqA = 200;
        long freqB = 50;
        long freqPar = 40;

        // A -> B: Café -> Filtro
        double confAtoB = (double) freqPar / (double) freqA; // 40 / 200 = 0.20 (20%)
        // B -> A: Filtro -> Café
        double confBtoA = (double) freqPar / (double) freqB; // 40 / 50 = 0.80 (80%)

        assertEquals(0.20, confAtoB, 0.0001);
        assertEquals(0.80, confBtoA, 0.0001);
        assertTrue("Quem compra filtro tem chance muito maior de levar café", confBtoA > confAtoB);

        // LIFT é matematicamente simétrico
        double suporteA = (double) freqA / (double) totalPedidos;
        double suporteB = (double) freqB / (double) totalPedidos;
        double liftAtoB = confAtoB / suporteB;
        double liftBtoA = confBtoA / suporteA;

        assertEquals(liftAtoB, liftBtoA, 0.0001);
        assertEquals(4.0, liftAtoB, 0.0001);
    }

    @Test
    public void testFiltroRegrasIrrelevantes() {
        long totalPedidos = 1000;
        long freqA = 500; // Arroz (50%)
        long freqB = 500; // Feijão (50%)
        long freqPar = 260; // 26% juntos

        double suporteB = (double) freqB / (double) totalPedidos; // 0.50
        double confianca = (double) freqPar / (double) freqA;     // 0.52
        double lift = confianca / suporteB;                       // 0.52 / 0.50 = 1.04

        // Lift de 1.04 é correlação fraca (apenas coincidência de produtos populares)
        assertFalse("Lift de 1.04 deve ser descartado por estar abaixo de 1.20", lift >= MIN_LIFT);
    }

    @Test
    public void testProtecaoDivisaoPorZeroEInfinita() {
        long totalPedidos = 100;
        long freqA = 0;
        long freqB = 0;
        long freqPar = 0;

        double suporteB = (double) freqB / (double) totalPedidos;
        double confianca = freqA > 0 ? (double) freqPar / (double) freqA : 0.0;
        double lift = suporteB > 0 ? confianca / suporteB : 0.0;

        assertEquals(0.0, lift, 0.0001);
        assertFalse(Double.isInfinite(lift));
        assertFalse(Double.isNaN(lift));
    }

    @Test
    public void testFormatacaoEscalaBigDecimal() {
        double lift = 3.245678;
        BigDecimal bdLift = BigDecimal.valueOf(lift).setScale(1, RoundingMode.HALF_UP);
        assertEquals("3.2", bdLift.toPlainString());

        double score = 3.245678 * 0.456789;
        BigDecimal bdScore = BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP);
        assertNotNull(bdScore);
        assertTrue(bdScore.compareTo(BigDecimal.ZERO) > 0);
    }
}
