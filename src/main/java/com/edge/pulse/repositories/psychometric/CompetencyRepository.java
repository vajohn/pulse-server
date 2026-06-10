package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.Competency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

    List<Competency> findAllByOrderByDisplayOrderAsc();
}
