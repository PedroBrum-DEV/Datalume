package br.com.datalume.conta.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plano")
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(name = "limite_usuarios", nullable = false)
    private int limiteUsuarios;

    @Column(name = "limite_nichos", nullable = false)
    private int limiteNichos;

    @Column(name = "limite_alertas", nullable = false)
    private int limiteAlertas;

    @Column(name = "limite_exportacao", nullable = false)
    private int limiteExportacao;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Plano() {
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public int getLimiteUsuarios() {
        return limiteUsuarios;
    }

    public int getLimiteNichos() {
        return limiteNichos;
    }

    public int getLimiteAlertas() {
        return limiteAlertas;
    }

    public int getLimiteExportacao() {
        return limiteExportacao;
    }

    public boolean isAtivo() {
        return ativo;
    }
}