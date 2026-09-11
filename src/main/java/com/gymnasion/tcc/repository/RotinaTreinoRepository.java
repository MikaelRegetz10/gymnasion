package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.RotinaTreino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RotinaTreinoRepository extends JpaRepository<RotinaTreino, UUID> {
}
