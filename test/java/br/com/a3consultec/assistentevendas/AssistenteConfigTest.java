package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.util.AssistenteConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Testes Unitários de AssistenteConfig.
 * Valida valores padrão (TSIPAR fallbacks), sobrescrita de teste e retenção de cache.
 */
public class AssistenteConfigTest {

    @Before
    @After
    public void resetCache() {
        AssistenteConfig.limparCache();
    }

    @Test
    public void testValoresPadraoTSIPAR() {
        assertEquals("Dias de histórico padrão deve ser 180", 180, AssistenteConfig.DEFAULT_DIAS_HISTORICO);
        assertEquals("Min Lift padrão deve ser 1.20", 1.20, AssistenteConfig.DEFAULT_MIN_LIFT, 0.001);
        assertEquals("Min Confiança padrão deve ser 0.10", 0.10, AssistenteConfig.DEFAULT_MIN_CONFIANCA, 0.001);
        assertEquals("Max Sugestões padrão deve ser 6", 6, AssistenteConfig.DEFAULT_MAX_SUGESTOES);
    }

    @Test
    public void testGettersComFallbacks() {
        // Sem banco de dados ativo no teste unitário hermético, deve cair nos defaults com segurança
        int dias = AssistenteConfig.getDiasHistorico();
        double lift = AssistenteConfig.getMinLift();
        double conf = AssistenteConfig.getMinConfianca();
        int max = AssistenteConfig.getMaxSugestoes();

        assertEquals(180, dias);
        assertEquals(1.20, lift, 0.001);
        assertEquals(0.10, conf, 0.001);
        assertEquals(6, max);
    }

    @Test
    public void testSobrescritaParaTestesEInjecao() {
        AssistenteConfig.setConfigForTesting(90, 1.50, 0.25, 4);

        assertEquals(90, AssistenteConfig.getDiasHistorico());
        assertEquals(1.50, AssistenteConfig.getMinLift(), 0.001);
        assertEquals(0.25, AssistenteConfig.getMinConfianca(), 0.001);
        assertEquals(4, AssistenteConfig.getMaxSugestoes());

        AssistenteConfig.limparCache();
        // Após limpar, volta aos defaults
        assertEquals(180, AssistenteConfig.getDiasHistorico());
    }
}
