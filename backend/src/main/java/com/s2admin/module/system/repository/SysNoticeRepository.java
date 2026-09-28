package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long>, JpaSpecificationExecutor<SysNotice> {

    List<SysNotice> findByStatusOrderByPinnedDescPublishTimeDesc(Integer status);
}
