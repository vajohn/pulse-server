package com.edge.pulse.repositories.spark;

import com.edge.pulse.data.models.spark.SparkCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SparkCategoryRepository extends JpaRepository<SparkCategory, String> {

    List<SparkCategory> findByActiveTrueOrderByDisplayOrderAsc();
}
