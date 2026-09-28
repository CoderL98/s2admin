package com.s2admin.module.workflow.repository;

import com.s2admin.module.workflow.entity.SysFlowNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysFlowNodeRepository extends JpaRepository<SysFlowNode, Long> {

    List<SysFlowNode> findByFlowIdOrderBySortAscIdAsc(Long flowId);

    void deleteByFlowId(Long flowId);
}
