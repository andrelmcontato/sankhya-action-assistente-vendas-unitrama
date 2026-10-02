package br.com.a3consultec.assistentevendas.model;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO tipado com os dados do produto recomendado para o Assistente de Vendas.
 */
public class SugestaoProdutoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal codProd;
    private String descrProd;
    private String marca;
    private String complemento;
    private BigDecimal vlrVenda;
    private BigDecimal estoqueDisponivel;
    private BigDecimal lift;
    private BigDecimal confianca;
    private BigDecimal suporte;
    private String tagAfinidade;
    private String motivo;
    private String hintTexto;

    // Campos Unitrama: Margem e Preço
    private BigDecimal margemSugerida;
    private BigDecimal fatorK;
    private BigDecimal aliquotaIpi;
    private transient BigDecimal custoVariavel;

    // Campo de agrupamento mínimo / múltiplos de venda (TGFPRO.AGRUPMIN)
    private BigDecimal agrupMin;

    public SugestaoProdutoDTO() {
    }

    public String getHintTexto() {
        return hintTexto;
    }

    public void setHintTexto(String hintTexto) {
        this.hintTexto = hintTexto;
    }

    public BigDecimal getCodProd() {
        return codProd;
    }

    public void setCodProd(BigDecimal codProd) {
        this.codProd = codProd;
    }

    public String getDescrProd() {
        return descrProd;
    }

    public void setDescrProd(String descrProd) {
        this.descrProd = descrProd;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public BigDecimal getVlrVenda() {
        return vlrVenda;
    }

    public void setVlrVenda(BigDecimal vlrVenda) {
        this.vlrVenda = vlrVenda;
    }

    public BigDecimal getEstoqueDisponivel() {
        return estoqueDisponivel;
    }

    public void setEstoqueDisponivel(BigDecimal estoqueDisponivel) {
        this.estoqueDisponivel = estoqueDisponivel;
    }

    public BigDecimal getLift() {
        return lift;
    }

    public void setLift(BigDecimal lift) {
        this.lift = lift;
    }

    public BigDecimal getConfianca() {
        return confianca;
    }

    public void setConfianca(BigDecimal confianca) {
        this.confianca = confianca;
    }

    public BigDecimal getSuporte() {
        return suporte;
    }

    public void setSuporte(BigDecimal suporte) {
        this.suporte = suporte;
    }

    public String getTagAfinidade() {
        return tagAfinidade;
    }

    public void setTagAfinidade(String tagAfinidade) {
        this.tagAfinidade = tagAfinidade;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public BigDecimal getMargemSugerida() {
        return margemSugerida;
    }

    public void setMargemSugerida(BigDecimal margemSugerida) {
        this.margemSugerida = margemSugerida;
    }

    public BigDecimal getFatorK() {
        return fatorK;
    }

    public void setFatorK(BigDecimal fatorK) {
        this.fatorK = fatorK;
    }

    public BigDecimal getAliquotaIpi() {
        return aliquotaIpi;
    }

    public void setAliquotaIpi(BigDecimal aliquotaIpi) {
        this.aliquotaIpi = aliquotaIpi;
    }

    public BigDecimal getCustoVariavel() {
        return custoVariavel;
    }

    public void setCustoVariavel(BigDecimal custoVariavel) {
        this.custoVariavel = custoVariavel;
    }

    public BigDecimal getAgrupMin() {
        return (agrupMin != null && agrupMin.compareTo(BigDecimal.ZERO) > 0) ? agrupMin : BigDecimal.ONE;
    }

    public void setAgrupMin(BigDecimal agrupMin) {
        this.agrupMin = agrupMin;
    }
}
