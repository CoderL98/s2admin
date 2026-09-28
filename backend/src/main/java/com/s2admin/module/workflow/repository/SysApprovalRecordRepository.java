package com.s2admin.module.workflow.repository;

import com.s2admin.module.workflow.entity.SysApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysApprovalRecordRepository extends JpaRepository<SysApprovalRecord, Long> {

    List<SysApprovalRecord> findByApprovalIdOrderByIdAsc(Long approvalId);
}
