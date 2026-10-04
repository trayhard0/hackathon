package com.hackathon.backend.gmail;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRecordRepository extends JpaRepository<EmailRecord, String> {
}
