package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Desempenho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DesempenhoRepository extends JpaRepository<Desempenho, UUID> {

    Optional<Desempenho> findBySessaoTreinoId(UUID sessaoTreinoId);

    @Query("SELECT DISTINCT d FROM Desempenho d " +
            "JOIN FETCH d.sessaoTreino s " +
            "JOIN FETCH s.rotinaTreino r " +
            "JOIN FETCH r.modalidade m " +
            "LEFT JOIN FETCH d.registrosMetricas rm " +
            "LEFT JOIN FETCH rm.metrica met " +
            "WHERE d.aluno.id = :alunoId")
    List<Desempenho> findByAlunoIdWithMetricas(@Param("alunoId") UUID alunoId);
}
