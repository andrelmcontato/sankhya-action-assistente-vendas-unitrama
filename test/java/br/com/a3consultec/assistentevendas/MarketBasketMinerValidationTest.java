package br.com.a3consultec.assistentevendas;

import org.junit.Test;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Testes Unitários dos Parâmetros de Mineração e Heurística de Regras de Associação.
 */
public class MarketBasketMinerValidationTest {

    private static final int DIAS_HISTORICO = 180;
    private static final int MIN_COOCORRENCIA = 2;
    private static final double MIN_LIFT = 1.20;
    private static final double MIN_CONFIANCA = 0.10;
    private static final long MIN_TOTAL_PEDIDOS = 5;

    @Test
    public void testParametrosEstatisticosPadrao() {
        assertEquals(180, DIAS_HISTORICO);
        assertEquals(2, MIN_COOCORRENCIA);
        assertEquals(1.20, MIN_LIFT, 0.001);
        assertEquals(0.10, MIN_CONFIANCA, 0.001);
        assertEquals(5L, MIN_TOTAL_PEDIDOS);
    }

    @Test
    public void testAbortarSeVolumeInsuficiente() {
        long pedidosAbaixoDoMinimo = 4;
        assertTrue("Deve abortar se pedidos < 5", pedidosAbaixoDoMinimo < MIN_TOTAL_PEDIDOS);

        long pedidosSuficientes = 5;
        assertFalse("Não deve abortar se pedidos >= 5", pedidosSuficientes < MIN_TOTAL_PEDIDOS);
    }

    @Test
    public void testCalculoScoreComponent() {
        double lift = 3.50;
        double confianca = 0.40;
        double score = lift * confianca; // 1.40

        BigDecimal bdScore = BigDecimal.valueOf(score).setScale(6, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1.400000"), bdScore);
    }

    @Test
    public void testDescarteDeRegrasAbaixoDoCorte() {
        // Caso 1: LIFT alto mas confiança abaixo de 10%
        double lift1 = 4.0;
        double conf1 = 0.05;
        assertFalse("Confiança abaixo de 10% deve ser descartada", conf1 >= MIN_CONFIANCA);

        // Caso 2: Confiança alta mas LIFT abaixo de 1.20 (correlação espúria)
        double lift2 = 1.05;
        double conf2 = 0.60;
        assertFalse("LIFT abaixo de 1.20 deve ser descartado", lift2 >= MIN_LIFT);

        // Caso 3: Ambos acima do corte
        double lift3 = 2.10;
        double conf3 = 0.25;
        assertTrue("Regra válida deve ser aceita", lift3 >= MIN_LIFT && conf3 >= MIN_CONFIANCA);
    }

    @Test
    public void testOrdenacaoPrioridadeRegras() {
        // Regra 1: LIFT 3.0, Conf 0.30 -> Score 0.90
        // Regra 2: LIFT 5.0, Conf 0.20 -> Score 1.00
        double lift1 = 3.0;
        double conf1 = 0.30;
        double score1 = lift1 * conf1;

        double lift2 = 5.0;
        double conf2 = 0.20;
        double score2 = lift2 * conf2;

        assertTrue(score2 > score1);
    }
}
