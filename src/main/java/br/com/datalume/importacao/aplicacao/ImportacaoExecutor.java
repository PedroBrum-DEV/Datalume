package br.com.datalume.importacao.aplicacao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/** Uma única thread: as importações rodam em segundo plano e nunca em paralelo. */
@Component
public class ImportacaoExecutor implements DisposableBean {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "importacao");
        t.setDaemon(true);
        return t;
    });

    public void submeter(Runnable tarefa) {
        executor.submit(tarefa);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}