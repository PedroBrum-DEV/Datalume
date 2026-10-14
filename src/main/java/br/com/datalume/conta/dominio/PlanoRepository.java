package br.com.datalume.conta.dominio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    Optional<Plano> findByCodigo(String codigo);
}
