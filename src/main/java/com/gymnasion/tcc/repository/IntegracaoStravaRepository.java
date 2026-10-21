package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.Aluno;
import com.gymnasion.tcc.domain.IntegracaoStrava;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegracaoStravaRepository extends JpaRepository<IntegracaoStrava, UUID> {

    Optional<IntegracaoStrava> findByAluno(Aluno aluno);

    Optional<IntegracaoStrava> findByAlunoId(UUID alunoId);

    void deleteByAluno(Aluno aluno);

    boolean existsByAluno(Aluno aluno);
}
