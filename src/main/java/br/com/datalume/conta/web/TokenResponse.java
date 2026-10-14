package br.com.datalume.conta.web;

public record TokenResponse(String accessToken, String tokenType, long expiraEmSegundos) {

    public static TokenResponse bearer(String token, long expiraEmSegundos) {
        return new TokenResponse(token, "Bearer", expiraEmSegundos);
    }
}
