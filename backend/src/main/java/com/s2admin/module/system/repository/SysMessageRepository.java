package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysMessageRepository extends JpaRepository<SysMessage, Long>, JpaSpecificationExecutor<SysMessage> {

    Page<SysMessage> findByReceiverIdOrderByCreateTimeDesc(Long receiverId, Pageable pageable);

    long countByReceiverIdAndReadFlag(Long receiverId, Integer readFlag);
}
