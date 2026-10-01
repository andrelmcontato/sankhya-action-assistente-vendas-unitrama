package br.com.a3consultec.assistentevendas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço de Cálculo de Margem da Unitrama.
 * Reutiliza 100% a matemática exata da trigger TRG_INC_UPD_TGFITE_MRG.sql
 * em harmonia com o listener oficial RecalculoIpiEmbutidoListener da Unitrama.
 */
public class CalculoMargemTriggerService {

    public static final BigDecimal CEM = new BigDecimal("100");

    public static class NotaFiscalContexto {
        public BigDecimal nuNota;
        public BigDecimal percDescCabecalho = BigDecimal.ZERO;
        public String usaDescEspecial = "N";
        public BigDecimal percDescParceiro = BigDecimal.ZERO;
        public String vinculo = "";
        public boolean clienteTemIpi = false;
        public boolean isClienteSuframa = false;
        public boolean topRecalculaIpi = true;
        public BigDecimal vlrFrete = BigDecimal.ZERO;
    }

    /**
     * Calcula o fator multiplicador de recomposição F_sim da trigger da Unitrama.
     */
    public BigDecimal calcularFatorSimulado(NotaFiscalContexto ctx) {
        if ("S".equalsIgnoreCase(ctx.usaDescEspecial)
                && ctx.percDescParceiro != null
                && ctx.percDescParceiro.compareTo(BigDecimal.ZERO) > 0
                && ctx.percDescParceiro.compareTo(CEM) < 0) {

            BigDecimal percDecimal = ctx.percDescParceiro.divide(CEM, 8, RoundingMode.HALF_UP);
            if (ctx.vinculo != null && ctx.vinculo.startsWith("Nota de Venda")) {
                return BigDecimal.ONE.divide(percDecimal, 8, RoundingMode.HALF_UP);
            } else {
                BigDecimal umMenosPerc = BigDecimal.ONE.subtract(percDecimal);
                return BigDecimal.ONE.divide(umMenosPerc, 8, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ONE;
    }

    /**
     * Calcula o fator de acoplamento Omega da Unitrama entre a trigger e o listener de IPI.
     */
    public BigDecimal calcularFatorOmega(NotaFiscalContexto ctx, BigDecimal aliqIpi) {
        BigDecimal fSim = calcularFatorSimulado(ctx);
        BigDecimal percDesc = ctx.percDescCabecalho != null ? ctx.percDescCabecalho : BigDecimal.ZERO;
        BigDecimal umMenosPercDesc = BigDecimal.ONE.subtract(percDesc);

        BigDecimal fatorIpi = BigDecimal.ONE;
        boolean incideIpi = ctx.clienteTemIpi && !ctx.isClienteSuframa && ctx.topRecalculaIpi && aliqIpi != null && aliqIpi.compareTo(BigDecimal.ZERO) > 0;
        if (incideIpi) {
            fatorIpi = BigDecimal.ONE.add(aliqIpi.divide(CEM, 4, RoundingMode.HALF_UP));
        }

        BigDecimal numeradorOmega = fSim.multiply(umMenosPercDesc).add(fatorIpi.subtract(BigDecimal.ONE));
        return numeradorOmega.divide(fatorIpi, 8, RoundingMode.HALF_UP);
    }

    /**
     * Calcula a constante fatorK (C_var / Omega) que permite o recálculo bidirecional
     * instantâneo no frontend sem expor o custo real do produto (sigilo comercial estrito).
     */
    public BigDecimal calcularFatorK(NotaFiscalContexto ctx, BigDecimal cusVar, BigDecimal aliqIpi) {
        if (cusVar == null || cusVar.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal omega = calcularFatorOmega(ctx, aliqIpi);
        if (omega.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return cusVar.divide(omega, 4, RoundingMode.HALF_UP);
    }

    /**
     * Calcula a Margem Alvo (%) a partir do Preço Unitário e do fatorK.
     * m = [ 1 - (K / P) ] * 100
     */
    public BigDecimal calcularMargemDePreco(BigDecimal fatorK, BigDecimal precoUnitario) {
        if (fatorK == null || precoUnitario == null || precoUnitario.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal razao = fatorK.divide(precoUnitario, 8, RoundingMode.HALF_UP);
        BigDecimal umMenosRazao = BigDecimal.ONE.subtract(razao);
        return umMenosRazao.multiply(CEM).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula o Preço Unitário de Venda a partir da Margem Alvo e do fatorK.
     * P = K / [ 1 - (m / 100) ]
     */
    public BigDecimal calcularPrecoDeMargem(BigDecimal fatorK, BigDecimal margemAlvo) {
        if (fatorK == null || fatorK.compareTo(BigDecimal.ZERO) <= 0 || margemAlvo == null) {
            return BigDecimal.ZERO;
        }
        if (margemAlvo.compareTo(CEM) >= 0) {
            throw new IllegalArgumentException("A margem alvo deve ser menor que 100%. Informado: " + margemAlvo + "%");
        }
        BigDecimal m = margemAlvo.divide(CEM, 8, RoundingMode.HALF_UP);
        BigDecimal umMenosM = BigDecimal.ONE.subtract(m);
        if (umMenosM.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return fatorK.divide(umMenosM, 2, RoundingMode.HALF_UP);
    }

    /**
     * Método de compatibilidade direta com RecalculoIPIMargem.
     */
    public BigDecimal calcularPrecoUnitarioParaMargem(NotaFiscalContexto ctx, BigDecimal cusVar, BigDecimal margemAlvo, BigDecimal aliqIpi) {
        BigDecimal fatorK = calcularFatorK(ctx, cusVar, aliqIpi);
        return calcularPrecoDeMargem(fatorK, margemAlvo);
    }
}
