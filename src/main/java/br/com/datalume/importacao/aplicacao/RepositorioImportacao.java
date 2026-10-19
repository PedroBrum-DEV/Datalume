package br.com.datalume.importacao.aplicacao;

import br.com.datalume.compartilhado.erro.ConflitoException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RepositorioImportacao {

    private static final String MSG_EM_EXECUCAO = "Já existe uma importação em execução.";
    private static final String COLUNAS_ARQUIVO = """
            id, lote_id, nome, tipo, ordem, status, tamanho_bytes, linhas_lidas, inseridos, atualizados,
            tentativas, erro, iniciado_em, finalizado_em""";

    private final JdbcClient jdbc;

    public RepositorioImportacao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cria o lote (EXECUTANDO) e seus arquivos na mesma transação. */
    @Transactional
    public long criarLote(String referencia, String filtro, List<String> nomes, List<TipoArquivo> tipos) {
        long id;
        try {
            id = jdbc.sql("""
                            INSERT INTO importacao_lote (referencia, status, filtro)
                            VALUES (:ref, 'EXECUTANDO', :filtro) RETURNING id
                            """)
                    .param("ref", referencia)
                    .param("filtro", filtro)
                    .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw new ConflitoException(MSG_EM_EXECUCAO);
        }
        for (int i = 0; i < nomes.size(); i++) {
            jdbc.sql("""
                            INSERT INTO importacao_arquivo (lote_id, nome, tipo, ordem)
                            VALUES (:lote, :nome, :tipo, :ordem)
                            """)
                    .param("lote", id)
                    .param("nome", nomes.get(i))
                    .param("tipo", tipos.get(i).name())
                    .param("ordem", i + 1)
                    .update();
        }
        return id;
    }

    /** FALHOU -> EXECUTANDO. */
    public void reabrirLote(long id) {
        try {
            int n = jdbc.sql("""
                            UPDATE importacao_lote SET status = 'EXECUTANDO', erro = NULL, finalizado_em = NULL
                            WHERE id = :id AND status = 'FALHOU'
                            """)
                    .param("id", id).update();
            if (n == 0) {
                throw new ConflitoException("Só é possível retomar lotes com status FALHOU.");
            }
        } catch (DuplicateKeyException e) {
            throw new ConflitoException(MSG_EM_EXECUCAO);
        }
    }

    public void finalizarLote(long id, String status, String erro) {
        jdbc.sql("UPDATE importacao_lote SET status = :s, erro = :e, finalizado_em = now() WHERE id = :id")
                .param("s", status).param("e", erro).param("id", id).update();
    }

    public Optional<Lote> buscarLote(long id) {
        return jdbc.sql("SELECT id, referencia, status, filtro, erro, criado_em, finalizado_em FROM importacao_lote WHERE id = :id")
                .param("id", id).query(Lote.class).optional();
    }

    public Optional<Lote> ultimoLoteDaReferencia(String referencia) {
        return jdbc.sql("""
                        SELECT id, referencia, status, filtro, erro, criado_em, finalizado_em
                        FROM importacao_lote WHERE referencia = :r ORDER BY id DESC LIMIT 1
                        """)
                .param("r", referencia).query(Lote.class).optional();
    }

    public boolean existeLoteConcluido(String referencia) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM importacao_lote WHERE referencia = :r AND status = 'CONCLUIDO')")
                .param("r", referencia).query(Boolean.class).single();
    }

    public List<Lote> listarLotes(int limite) {
        return jdbc.sql("""
                        SELECT id, referencia, status, filtro, erro, criado_em, finalizado_em
                        FROM importacao_lote ORDER BY id DESC LIMIT :n
                        """)
                .param("n", limite).query(Lote.class).list();
    }

    public List<ArquivoImportacao> arquivos(long loteId) {
        return jdbc.sql("SELECT " + COLUNAS_ARQUIVO + " FROM importacao_arquivo WHERE lote_id = :l ORDER BY ordem")
                .param("l", loteId).query(ArquivoImportacao.class).list();
    }

    public void iniciarArquivo(long id, String status) {
        jdbc.sql("""
                        UPDATE importacao_arquivo
                           SET status = :s, tentativas = tentativas + 1, erro = NULL,
                               iniciado_em = COALESCE(iniciado_em, now()), finalizado_em = NULL
                         WHERE id = :id
                        """)
                .param("s", status).param("id", id).update();
    }

    public void atualizarArquivo(long id, String status, Long tamanhoBytes) {
        jdbc.sql("UPDATE importacao_arquivo SET status = :s, tamanho_bytes = :t WHERE id = :id")
                .param("s", status).param("t", tamanhoBytes).param("id", id).update();
    }

    public void concluirArquivo(long id, long lidas, long inseridos, long atualizados) {
        jdbc.sql("""
                        UPDATE importacao_arquivo
                           SET status = 'CONCLUIDO', linhas_lidas = :l, inseridos = :i, atualizados = :a,
                               erro = NULL, finalizado_em = now()
                         WHERE id = :id
                        """)
                .param("l", lidas).param("i", inseridos).param("a", atualizados).param("id", id).update();
    }

    public void falharArquivo(long id, String erro) {
        jdbc.sql("UPDATE importacao_arquivo SET status = 'FALHOU', erro = :e, finalizado_em = now() WHERE id = :id")
                .param("e", erro).param("id", id).update();
    }

    /** Lotes que ficaram EXECUTANDO após queda/reinício da aplicação passam a FALHOU (retomáveis). */
    @Transactional
    public int recuperarInterrompidos() {
        jdbc.sql("""
                        UPDATE importacao_arquivo SET status = 'FALHOU', erro = 'Interrompido', finalizado_em = now()
                         WHERE status IN ('BAIXANDO', 'IMPORTANDO')
                        """).update();
        return jdbc.sql("""
                        UPDATE importacao_lote
                           SET status = 'FALHOU', erro = 'Interrompido (aplicação reiniciada)', finalizado_em = now()
                         WHERE status = 'EXECUTANDO'
                        """).update();
    }
}