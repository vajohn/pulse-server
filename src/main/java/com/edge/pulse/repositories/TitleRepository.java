package com.edge.pulse.repositories;

import com.edge.pulse.data.models.Title;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TitleRepository extends JpaRepository<Title, UUID> {
    Optional<Title> findByName(String name);
}
