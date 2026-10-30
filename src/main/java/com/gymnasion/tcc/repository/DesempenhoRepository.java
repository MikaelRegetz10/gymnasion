package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Desempenho;
import com.gymnasion.tcc.dto.DiaRegistrosMetricaDTO;
import com.gymnasion.tcc.dto.DiaRegistrosModalidadeDTO;
import com.gymnasion.tcc.dto.IntervaloRegistrosModalidadeDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    // US-16 / RNF-004: filtra o período no banco para não carregar todo o histórico do aluno
    @Query("SELECT DISTINCT d FROM Desempenho d " +
            "JOIN FETCH d.sessaoTreino s " +
            "JOIN FETCH s.rotinaTreino r " +
            "JOIN FETCH r.modalidade m " +
            "LEFT JOIN FETCH d.registrosMetricas rm " +
            "LEFT JOIN FETCH rm.metrica met " +
            "WHERE d.aluno.id = :alunoId " +
            "AND COALESCE(s.dataReal, s.dataPlanejada) BETWEEN :dataInicio AND :dataFim")
    List<Desempenho> findByAlunoIdAndPeriodoWithMetricas(
            @Param("alunoId") UUID alunoId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim
    );

    // US-16 C3: intervalo com registros disponíveis, por modalidade, para sugerir um período alternativo
    @Query("SELECT new com.gymnasion.tcc.dto.IntervaloRegistrosModalidadeDTO(" +
            "m.id, MIN(COALESCE(s.dataReal, s.dataPlanejada)), MAX(COALESCE(s.dataReal, s.dataPlanejada))) " +
            "FROM Desempenho d " +
            "JOIN d.sessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "JOIN r.modalidade m " +
            "WHERE d.aluno.id = :alunoId " +
            "GROUP BY m.id")
    List<IntervaloRegistrosModalidadeDTO> findIntervalosRegistrosByAlunoId(@Param("alunoId") UUID alunoId);

    // US-16: agrega por dia, sem carregar os registros, para listar as datas disponíveis para o relatório
    @Query("SELECT new com.gymnasion.tcc.dto.DiaRegistrosModalidadeDTO(" +
            "m.id, m.nome, COALESCE(s.dataReal, s.dataPlanejada), COUNT(d), COUNT(d.nivelCansaco)) " +
            "FROM Desempenho d " +
            "JOIN d.sessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "JOIN r.modalidade m " +
            "WHERE d.aluno.id = :alunoId " +
            "GROUP BY m.id, m.nome, COALESCE(s.dataReal, s.dataPlanejada)")
    List<DiaRegistrosModalidadeDTO> findDiasRegistrosByAlunoId(@Param("alunoId") UUID alunoId);

    // US-16: mesma agregação por métrica, incluindo as descontinuadas (RN-017)
    @Query("SELECT new com.gymnasion.tcc.dto.DiaRegistrosMetricaDTO(" +
            "m.id, met.id, met.titulo, met.tipo, met.ativo, COALESCE(s.dataReal, s.dataPlanejada), COUNT(rm)) " +
            "FROM RegistroMetrica rm " +
            "JOIN rm.desempenho d " +
            "JOIN rm.metrica met " +
            "JOIN d.sessaoTreino s " +
            "JOIN s.rotinaTreino r " +
            "JOIN r.modalidade m " +
            "WHERE d.aluno.id = :alunoId " +
            "GROUP BY m.id, met.id, met.titulo, met.tipo, met.ativo, COALESCE(s.dataReal, s.dataPlanejada)")
    List<DiaRegistrosMetricaDTO> findDiasRegistrosMetricasByAlunoId(@Param("alunoId") UUID alunoId);
}
