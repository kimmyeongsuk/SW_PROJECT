package com.swproject.app.repository;

import com.swproject.app.entity.TaxProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, Long> {
    Optional<TaxProfile> findByYear(int year);
}