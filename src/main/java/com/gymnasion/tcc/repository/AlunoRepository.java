package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.PersonalTrainer;
import com.gymnasion.tcc.domain.enums.StatusConvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, UUID> {
    @Query("SELECT DISTINCT a FROM Aluno a " +
            "JOIN FETCH a.usuario " +
            "JOIN FETCH a.personal p " +
            "JOIN FETCH p.usuario " +
            "LEFT JOIN FETCH a.modalidades " +
            "WHERE p.id = :personalId AND a.status = :status")
    List<Aluno> findByPersonalIdAndStatus(@Param("personalId") UUID personalId, @Param("status") StatusConvite status);
}
