package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.RotinaTreino;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RotinaTreinoRepository extends JpaRepository<RotinaTreino, UUID> {

    List<RotinaTreino> findByPersonalTrainerId(UUID personalTrainerId);

    Page<RotinaTreino> findByPersonalTrainerId(UUID personalTrainerId, Pageable pageable);

    List<RotinaTreino> findByPersonalTrainerIdAndAlunosId(UUID personalTrainerId, UUID alunoId);

    Page<RotinaTreino> findByPersonalTrainerIdAndAlunosId(UUID personalTrainerId, UUID alunoId, Pageable pageable);

    Page<RotinaTreino> findByAlunosId(UUID alunoId, Pageable pageable);

    List<RotinaTreino> findByAlunosId(UUID alunoId);

    Optional<RotinaTreino> findByIdAndPersonalTrainerId(UUID id, UUID personalTrainerId);

    List<RotinaTreino> findByPersonalTrainerIdAndModalidadeId(UUID personalTrainerId, Long modalidadeId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RotinaTreino r JOIN r.alunos a WHERE a.id = :alunoId AND r.ativo = true")
    boolean existsByAlunoIdAndAtivoTrue(@Param("alunoId") UUID alunoId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RotinaTreino r JOIN r.alunos a WHERE a.id = :alunoId AND r.ativo = true AND r.id <> :rotinaId")
    boolean existsByAlunoIdAndAtivoTrueAndIdNot(@Param("alunoId") UUID alunoId, @Param("rotinaId") UUID rotinaId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RotinaTreino r JOIN r.alunos a WHERE a.id = :alunoId AND r.modalidade.id = :modalidadeId AND r.ativo = true")
    boolean existsByAlunoIdAndModalidadeIdAndAtivoTrue(@Param("alunoId") UUID alunoId, @Param("modalidadeId") Long modalidadeId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RotinaTreino r JOIN r.alunos a WHERE a.id = :alunoId AND r.modalidade.id = :modalidadeId AND r.ativo = true AND r.id <> :rotinaId")
    boolean existsByAlunoIdAndModalidadeIdAndAtivoTrueAndIdNot(@Param("alunoId") UUID alunoId, @Param("modalidadeId") Long modalidadeId, @Param("rotinaId") UUID rotinaId);

    @Query("SELECT r FROM RotinaTreino r JOIN r.alunos a WHERE a.id = :alunoId AND r.modalidade.id = :modalidadeId AND r.ativo = true")
    List<RotinaTreino> findByAlunoIdAndModalidadeIdAndAtivoTrue(@Param("alunoId") UUID alunoId, @Param("modalidadeId") Long modalidadeId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RotinaTreino r JOIN r.metricas m WHERE m.id = :metricaId")
    boolean existsByMetricaId(@Param("metricaId") UUID metricaId);
}