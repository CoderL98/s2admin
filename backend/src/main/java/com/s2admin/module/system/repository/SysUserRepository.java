package com.s2admin.module.system.repository;

import com.s2admin.module.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    @Query("SELECT u FROM SysUser u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<SysUser> findWithRolesById(@Param("id") Long id);

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByEmail(String email);

    Optional<SysUser> findByEmailIgnoreCase(String email);

    Optional<SysUser> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("SELECT COUNT(u) FROM SysUser u JOIN u.roles r WHERE r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT u.id FROM SysUser u JOIN u.roles r WHERE r.id = :roleId")
    List<Long> findIdsByRoleId(@Param("roleId") Long roleId);

    long countByDeptId(Long deptId);

    long countByTenantId(Long tenantId);

    long countByIdIn(java.util.Collection<Long> ids);

    @Query("SELECT u.id FROM SysUser u WHERE u.deptId IN :deptIds")
    List<Long> findIdsByDeptIdIn(@Param("deptIds") java.util.Collection<Long> deptIds);

    @Query(value = """
            SELECT r.code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId AND r.deleted = 0 AND r.status = 0
            """, nativeQuery = true)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT p.code FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = :userId AND p.deleted = 0 AND p.status = 0
              AND r.deleted = 0 AND r.status = 0
              AND p.code IS NOT NULL AND p.code <> ''
            """, nativeQuery = true)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM sys_user_role WHERE user_id = :userId", nativeQuery = true)
    void deleteUserRoles(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SysUser u SET u.deptName = :name WHERE u.deptId = :deptId")
    int updateDeptName(@Param("deptId") Long deptId, @Param("name") String name);

    @Query("SELECT COUNT(DISTINCT r.id) FROM SysUser u JOIN u.roles r WHERE u.id IN :ids")
    long countDistinctRolesByUserIdIn(@Param("ids") java.util.Collection<Long> ids);
}
