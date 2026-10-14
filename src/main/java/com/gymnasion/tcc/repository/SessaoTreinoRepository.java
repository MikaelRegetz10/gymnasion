package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.SessaoTreino;
import com.gymnasion.tcc.domain.enums.StatusSessaoTreino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessaoTreinoRepository extends JpaRepository<SessaoTreino, UUID> {

    List<SessaoTreino> findByRotinaTreinoIdOrderBySemanaAscDataPlanejadaAsc(UUID rotinaTreinoId);

    List<SessaoTreino> findByRotinaTreinoIdAndStatusOrderByDataRealDesc(UUID rotinaTreinoId, StatusSessaoTreino status);

    @Query("SELECT s FROM SessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "WHERE r.personalTrainer.id = :personalId AND s.id = :sessaoId")
    Optional<SessaoTreino> findByIdAndPersonalTrainerId(@Param("sessaoId") UUID sessaoId, @Param("personalId") UUID personalId);

    @Query("SELECT s FROM SessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "JOIN r.alunos a " +
            "WHERE a.id = :alunoId AND s.id = :sessaoId")
    Optional<SessaoTreino> findByIdAndAlunoId(@Param("sessaoId") UUID sessaoId, @Param("alunoId") UUID alunoId);

    @Query("SELECT s FROM SessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "JOIN r.alunos a " +
            "WHERE a.id = :alunoId AND s.status = :status " +
            "ORDER BY s.dataReal DESC")
    List<SessaoTreino> findByAlunoIdAndStatusOrderByDataRealDesc(@Param("alunoId") UUID alunoId, @Param("status") StatusSessaoTreino status);
}
