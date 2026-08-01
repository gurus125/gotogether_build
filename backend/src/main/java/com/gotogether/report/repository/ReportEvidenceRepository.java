package com.gotogether.report.repository;

import com.gotogether.report.entity.ReportEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, UUID> {

    List<ReportEvidence> findByReportIdOrderByCreatedAtAsc(UUID reportId);

    boolean existsByStorageKey(String storageKey);
}
