package br.com.datalume.alerta.aplicacao;

/** Porta de saída de e-mail (SMTP em produção; trocável em testes). */
public interface EnviadorEmail {

    record MensagemEmail(String para, String assunto, String texto, String html, String linkCancelamento) {
    }

    /** Lança exceção em caso de falha de entrega. */
    void enviar(MensagemEmail mensagem);
}