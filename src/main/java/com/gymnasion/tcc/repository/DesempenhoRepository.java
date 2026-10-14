package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Desempenho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesempenhoRepository extends JpaRepository<Desempenho, UUID> {
    Optional<Desempenho> findBySessaoTreinoId(UUID sessaoTreinoId);
}
