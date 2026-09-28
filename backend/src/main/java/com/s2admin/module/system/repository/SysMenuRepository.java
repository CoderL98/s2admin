package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 菜单仓储
 */
public interface SysMenuRepository extends JpaRepository<SysMenu, Long>, JpaSpecificationExecutor<SysMenu> {

    boolean existsByParentId(Long parentId);

    List<SysMenu> findByParentId(Long parentId);

    List<SysMenu> findByStatusOrderBySortAsc(Integer status);

    boolean existsByPath(String path);

    java.util.Optional<SysMenu> findFirstByPath(String path);

    List<SysMenu> findByPermission(String permission);
}
