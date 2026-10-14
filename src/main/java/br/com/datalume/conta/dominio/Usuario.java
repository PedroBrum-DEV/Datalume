package br.com.datalume.conta.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelUsuario papel;

    @Column(name = "admin_plataforma", nullable = false)
    private boolean adminPlataforma = false;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_login_em")
    private Instant ultimoLoginEm;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {
    }

    public Usuario(Conta conta, String nome, String email, String senhaHash, PapelUsuario papel) {
        this.conta = conta;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }

    public void registrarLogin() {
        this.ultimoLoginEm = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public PapelUsuario getPapel() {
        return papel;
    }

    public boolean isAdminPlataforma() {
        return adminPlataforma;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getUltimoLoginEm() {
        return ultimoLoginEm;
    }
}
