package com.gramsetu.repository;

import com.gramsetu.model.CropAdvisory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CropAdvisoryRepository extends JpaRepository<CropAdvisory, Long> {
    /** Exact (case-insensitive) crop name; the disease is then compared in Java. */
    List<CropAdvisory> findByCropTypeIgnoreCase(String cropType);
}
