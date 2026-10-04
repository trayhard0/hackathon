package com.hackathon.backend.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByCompanyIgnoreCaseAndRoleIgnoreCase(String company, String role);
    List<Application> findByCompanyIgnoreCase(String company);

}
