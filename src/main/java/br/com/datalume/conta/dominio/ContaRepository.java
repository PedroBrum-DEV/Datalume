package br.com.datalume.conta.dominio;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, UUID> {
}
