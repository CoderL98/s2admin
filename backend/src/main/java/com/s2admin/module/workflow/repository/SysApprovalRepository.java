package com.s2admin.module.workflow.repository;

import com.s2admin.module.workflow.entity.SysApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysApprovalRepository extends JpaRepository<SysApproval, Long>, JpaSpecificationExecutor<SysApproval> {

    long countByFlowIdAndStatus(Long flowId, Integer status);
}
