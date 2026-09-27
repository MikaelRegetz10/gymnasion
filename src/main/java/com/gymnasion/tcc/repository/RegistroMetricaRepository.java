package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.RegistroMetrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RegistroMetricaRepository extends JpaRepository<RegistroMetrica, UUID> {

    boolean existsByMetricaId(UUID metricaId);

    @Query("SELECT COUNT(DISTINCT d.id) FROM RegistroMetrica rm " +
            "JOIN rm.desempenho d " +
            "WHERE rm.metrica.id = :metricaId")
    Long countAlunosComRegistrosByMetricaId(@Param("metricaId") UUID metricaId);
}