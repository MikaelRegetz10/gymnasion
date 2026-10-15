package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.ObservacaoFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ObservacaoFeedbackRepository extends JpaRepository<ObservacaoFeedback, UUID> {

    @Query("SELECT o FROM ObservacaoFeedback o " +
            "JOIN FETCH o.autor a " +
            "JOIN FETCH o.aluno al " +
            "WHERE o.aluno.id = :alunoId " +
            "ORDER BY o.dataCriacao DESC")
    List<ObservacaoFeedback> findByAlunoIdOrderByDataCriacaoDesc(@Param("alunoId") UUID alunoId);
}
