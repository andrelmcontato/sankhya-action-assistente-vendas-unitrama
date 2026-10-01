package br.com.a3consultec.assistentevendas.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload estruturado com o status e a lista de sugestões entregues ao Frontend Clean Light.
 */
public class AssistenteResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean sucesso;
    private String mensagem;
    private BigDecimal nuNota;
    private BigDecimal codParc;
    private String nomeParc;
    private int qtdItensCarrinho;
    private List<SugestaoProdutoDTO> sugestoes = new ArrayList<SugestaoProdutoDTO>();
    private List<SugestaoProdutoDTO> itensFrequentes = new ArrayList<SugestaoProdutoDTO>();

    public AssistenteResponseDTO() {
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public BigDecimal getNuNota() {
        return nuNota;
    }

    public void setNuNota(BigDecimal nuNota) {
        this.nuNota = nuNota;
    }

    public BigDecimal getCodParc() {
        return codParc;
    }

    public void setCodParc(BigDecimal codParc) {
        this.codParc = codParc;
    }

    public String getNomeParc() {
        return nomeParc;
    }

    public void setNomeParc(String nomeParc) {
        this.nomeParc = nomeParc;
    }

    public int getQtdItensCarrinho() {
        return qtdItensCarrinho;
    }

    public void setQtdItensCarrinho(int qtdItensCarrinho) {
        this.qtdItensCarrinho = qtdItensCarrinho;
    }

    public List<SugestaoProdutoDTO> getSugestoes() {
        return sugestoes;
    }

    public void setSugestoes(List<SugestaoProdutoDTO> sugestoes) {
        this.sugestoes = sugestoes;
    }

    public List<SugestaoProdutoDTO> getItensFrequentes() {
        return itensFrequentes;
    }

    public void setItensFrequentes(List<SugestaoProdutoDTO> itensFrequentes) {
        this.itensFrequentes = itensFrequentes;
    }
}
