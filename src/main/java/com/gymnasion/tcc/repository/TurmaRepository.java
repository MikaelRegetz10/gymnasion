package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TurmaRepository extends JpaRepository<Turma, UUID> {

    @Query("SELECT DISTINCT t FROM Turma t " +
            "JOIN FETCH t.personal p " +
            "JOIN FETCH t.modalidade " +
            "LEFT JOIN FETCH t.alunos a " +
            "LEFT JOIN FETCH a.usuario " +
            "WHERE p.id = :personalId")
    List<Turma> findByPersonalId(@Param("personalId") UUID personalId);

    @Query("SELECT DISTINCT t FROM Turma t " +
            "JOIN FETCH t.personal p " +
            "JOIN FETCH t.modalidade " +
            "LEFT JOIN FETCH t.alunos a " +
            "LEFT JOIN FETCH a.usuario " +
            "WHERE t.id = :id AND p.id = :personalId")
    Optional<Turma> findByIdAndPersonalId(@Param("id") UUID id, @Param("personalId") UUID personalId);
}