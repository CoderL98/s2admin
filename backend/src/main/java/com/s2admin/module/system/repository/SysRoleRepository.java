package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储
 */
public interface SysRoleRepository extends JpaRepository<SysRole, Long>, JpaSpecificationExecutor<SysRole> {

    boolean existsByCode(String code);

    Optional<SysRole> findByCode(String code);

    @Query("SELECT r.id FROM SysRole r WHERE r.code IN :codes AND (r.status IS NULL OR r.status = 0)")
    List<Long> findIdsByCodeIn(@Param("codes") java.util.Collection<String> codes);

    @Query(value = "SELECT role_id, COUNT(*) FROM sys_user_role GROUP BY role_id", nativeQuery = true)
    List<Object[]> countUsersGroupByRole();

    List<SysRole> findByStatusOrderBySortAsc(Integer status);

    @Query("SELECT p.id FROM SysRole r JOIN r.permissions p WHERE r.id = :roleId")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query(value = "DELETE FROM sys_role_permission WHERE role_id = :roleId", nativeQuery = true)
    void deleteRolePermissions(@Param("roleId") Long roleId);

    @Modifying
    @Query(value = "DELETE FROM sys_user_role WHERE role_id = :roleId", nativeQuery = true)
    void deleteRoleUsers(@Param("roleId") Long roleId);
}
