package br.com.datalume.referencia.carga;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Cliente da API de localidades e CNAE do IBGE (servicodados.ibge.gov.br). */
@Component
class IbgeClient {

    private final RestClient client;

    IbgeClient(RestClient.Builder builder, @Value("${datalume.referencia.ibge-url}") String baseUrl) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder.baseUrl(baseUrl).requestFactory(fabrica).build();
    }

    JsonNode municipios() {
        return client.get().uri("/v1/localidades/municipios").retrieve().body(JsonNode.class);
    }

    JsonNode cnaeSubclasses() {
        return client.get().uri("/v2/cnae/subclasses").retrieve().body(JsonNode.class);
    }
}
