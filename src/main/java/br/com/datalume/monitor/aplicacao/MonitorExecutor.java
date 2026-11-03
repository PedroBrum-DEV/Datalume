package br.com.datalume.monitor.aplicacao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/** Uma thread só: o processamento de referências nunca roda em paralelo e não segura a importação. */
@Component
public class MonitorExecutor implements DisposableBean {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "monitor");
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