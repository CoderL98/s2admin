package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 权限仓储
 */
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long>, JpaSpecificationExecutor<SysPermission> {

    boolean existsByCode(String code);

    java.util.Optional<SysPermission> findByCode(String code);

    @Query(value = "SELECT permission_id, COUNT(*) FROM sys_role_permission GROUP BY permission_id", nativeQuery = true)
    List<Object[]> countRolesGroupByPermission();

    List<SysPermission> findByStatusOrderBySortAsc(Integer status);

    @Query(value = "SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = :permissionId", nativeQuery = true)
    long countRoleBindings(@Param("permissionId") Long permissionId);
}
