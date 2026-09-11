package com.gymnasion.tcc.repository;

import com.gymnasion.tcc.domain.TermoAceite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TermoAceiteRepository extends JpaRepository<TermoAceite, UUID> {
}
