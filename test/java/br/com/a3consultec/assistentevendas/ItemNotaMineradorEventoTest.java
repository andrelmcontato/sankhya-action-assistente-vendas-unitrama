package br.com.a3consultec.assistentevendas;

import br.com.a3consultec.assistentevendas.event.ItemNotaMineradorEvento;
import br.com.a3consultec.assistentevendas.model.AssistenteResponseDTO;
import br.com.a3consultec.assistentevendas.service.AssistenteVendasService;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.vo.DynamicVO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suíte de testes unitários automatizados para o ItemNotaMineradorEvento.
 * Valida rigorosamente as regras de corte:
 * - TGFCAB.TIPMOV = 'P'
 * - TGFCAB.STATUSNOTA <> 'L'
 * - TGFTOP.GOLSINAL = -1
 */
public class ItemNotaMineradorEventoTest {

    private AssistenteVendasService mockService;
    private ItemNotaMineradorEvento evento;

    @Before
    public void setUp() {
        mockService = mock(AssistenteVendasService.class);
        evento = new ItemNotaMineradorEvento(mockService);
    }

    @Test
    public void testValidarCriterios_PedidoVendaNaoLiberadoComercial_RetornaTrue() {
        assertTrue("Pedido de venda pendente com GOLSINAL -1 deve ser aceito",
                ItemNotaMineradorEvento.validarCriterios("P", "P", -1));
        assertTrue("Pedido com status não liberado (ex: A) deve ser aceito",
                ItemNotaMineradorEvento.validarCriterios("P", "A", -1));
        assertTrue("Case insensitive deve ser suportado",
                ItemNotaMineradorEvento.validarCriterios("p", "p", -1));
    }

    @Test
    public void testValidarCriterios_NotaFiscalVendaNaoEhPedido_RetornaFalse() {
        assertFalse("Nota fiscal normal (TIPMOV=V) não deve ser processada",
                ItemNotaMineradorEvento.validarCriterios("V", "P", -1));
    }

    @Test
    public void testValidarCriterios_Compra_RetornaFalse() {
        assertFalse("Nota ou pedido de compra (TIPMOV=C ou O) não deve ser processada",
                ItemNotaMineradorEvento.validarCriterios("C", "P", 1));
        assertFalse("Pedido de compra (TIPMOV=O) não deve ser processado",
                ItemNotaMineradorEvento.validarCriterios("O", "P", 1));
    }

    @Test
    public void testValidarCriterios_PedidoLiberado_RetornaFalse() {
        assertFalse("Pedido já confirmado/liberado (STATUSNOTA=L) não deve ser reprocessado",
                ItemNotaMineradorEvento.validarCriterios("P", "L", -1));
        assertFalse("Case insensitive para STATUSNOTA=l",
                ItemNotaMineradorEvento.validarCriterios("P", "l", -1));
    }

    @Test
    public void testValidarCriterios_GolSinalNaoComercial_RetornaFalse() {
        assertFalse("GOLSINAL 0 (operação sem sinal comercial) não deve ser processada",
                ItemNotaMineradorEvento.validarCriterios("P", "P", 0));
        assertFalse("GOLSINAL 1 (operação de entrada/compra) não deve ser processada",
                ItemNotaMineradorEvento.validarCriterios("P", "P", 1));
    }

    @Test
    public void testProcessarAlteracaoItem_EventNulo_NaoLancaExcecao() {
        evento.processarAlteracaoItem(null);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));
    }

    @Test
    public void testProcessarAlteracaoItem_VoNulo_NaoLancaExcecao() {
        PersistenceEvent mockEvent = mock(PersistenceEvent.class);
        when(mockEvent.getVo()).thenReturn(null);

        evento.processarAlteracaoItem(mockEvent);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));
    }

    private DynamicVO criarMockItem(PersistenceEvent mockEvent) {
        DynamicVO mockItem = (DynamicVO) mock(DynamicVO.class, Mockito.withSettings().extraInterfaces(br.com.sankhya.jape.vo.EntityVO.class));
        when(mockEvent.getVo()).thenReturn((br.com.sankhya.jape.vo.EntityVO) mockItem);
        return mockItem;
    }

    @Test
    public void testProcessarAlteracaoItem_NuNotaInvalido_NaoProcessa() {
        PersistenceEvent mockEvent = mock(PersistenceEvent.class);
        DynamicVO mockItem = criarMockItem(mockEvent);
        when(mockItem.asBigDecimal("NUNOTA")).thenReturn(null);

        evento.processarAlteracaoItem(mockEvent);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));

        when(mockItem.asBigDecimal("NUNOTA")).thenReturn(BigDecimal.ZERO);
        evento.processarAlteracaoItem(mockEvent);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));
    }

    @Test
    public void testProcessarAlteracaoItem_FastFailEmMemoria_TipMovDiferenteDeP() {
        PersistenceEvent mockEvent = mock(PersistenceEvent.class);
        DynamicVO mockItem = criarMockItem(mockEvent);
        DynamicVO mockCab = mock(DynamicVO.class);

        when(mockItem.asBigDecimal("NUNOTA")).thenReturn(new BigDecimal("12345"));
        when(mockItem.getProperty("CabecalhoNota")).thenReturn(mockCab);
        when(mockCab.asString("TIPMOV")).thenReturn("C"); // Compra
        when(mockCab.asString("STATUSNOTA")).thenReturn("P");

        evento.processarAlteracaoItem(mockEvent);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));
    }

    @Test
    public void testProcessarAlteracaoItem_FastFailEmMemoria_StatusNotaLiberada() {
        PersistenceEvent mockEvent = mock(PersistenceEvent.class);
        DynamicVO mockItem = criarMockItem(mockEvent);
        DynamicVO mockCab = mock(DynamicVO.class);

        when(mockItem.asBigDecimal("NUNOTA")).thenReturn(new BigDecimal("12345"));
        when(mockItem.getProperty("CabecalhoNota")).thenReturn(mockCab);
        when(mockCab.asString("TIPMOV")).thenReturn("P");
        when(mockCab.asString("STATUSNOTA")).thenReturn("L"); // Liberada

        evento.processarAlteracaoItem(mockEvent);
        verify(mockService, never()).obterSugestoes(any(BigDecimal.class));
    }

    @Test
    public void testProcessarAlteracaoItem_PedidoValido_ChamaServiceObterSugestoes() {
        BigDecimal nuNotaValido = new BigDecimal("98765");

        // Subclasse para isolar o acesso ao banco e simular pedido válido
        ItemNotaMineradorEvento eventoSpy = new ItemNotaMineradorEvento(mockService) {
            @Override
            public boolean isPedidoValido(BigDecimal nuNota) {
                return nuNotaValido.equals(nuNota);
            }
        };

        PersistenceEvent mockEvent = mock(PersistenceEvent.class);
        DynamicVO mockItem = criarMockItem(mockEvent);
        DynamicVO mockCab = mock(DynamicVO.class);

        when(mockItem.asBigDecimal("NUNOTA")).thenReturn(nuNotaValido);
        when(mockItem.getProperty("CabecalhoNota")).thenReturn(mockCab);
        when(mockCab.asString("TIPMOV")).thenReturn("P");
        when(mockCab.asString("STATUSNOTA")).thenReturn("P");

        when(mockService.obterSugestoes(nuNotaValido)).thenReturn(new AssistenteResponseDTO());

        eventoSpy.processarAlteracaoItem(mockEvent);

        // Verifica que o serviço foi acionado com o NUNOTA exato!
        verify(mockService).obterSugestoes(nuNotaValido);
    }
}
