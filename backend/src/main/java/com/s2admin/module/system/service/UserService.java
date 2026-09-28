package com.s2admin.module.system.service;

import com.s2admin.module.auth.service.LoginAccountService;
import com.s2admin.module.common.PageResult;
import com.s2admin.module.common.exception.BusinessException;
import com.s2admin.module.common.util.CsvUtils;
import com.s2admin.module.common.util.PasswordPolicy;
import com.s2admin.module.common.util.SecurityUtils;
import com.s2admin.module.common.util.UniqueFields;
import com.s2admin.module.security.LoginUser;
import com.s2admin.module.security.TokenBlacklistService;
import com.s2admin.module.security.UserPermissionService;
import com.s2admin.module.common.util.XlsxUtils;
import com.s2admin.module.tenant.TenantContext;
import com.s2admin.module.system.entity.SysDept;
import com.s2admin.module.system.entity.SysRole;
import com.s2admin.module.system.entity.SysUser;
import com.s2admin.module.system.form.UserForm;
import com.s2admin.module.system.form.UserQuery;
import com.s2admin.module.system.repository.SysDeptRepository;
import com.s2admin.module.system.repository.SysRoleRepository;
import com.s2admin.module.system.repository.SysUserRepository;
import com.s2admin.module.system.vo.ImportResultVO;
import com.s2admin.module.system.vo.UserVO;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final int MAX_EXPORT = 10_000;
    private static final int MAX_IMPORT = 2_000;

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionService permissionService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DataScopeService dataScopeService;
    private final SysDeptRepository deptRepository;
    private final LoginAccountService loginAccountService;
    private final TenantService tenantService;
    private final ObjectProvider<UserService> self;

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(UserQuery query) {
        if (query.getOrderBy() == null) {
            query.setOrderBy("id");
            query.setSortDirection("desc");
        }
        Pageable pageable = query.toPageable();
        Page<SysUser> page = userRepository.findAll(buildSpec(query), pageable);
        return PageResult.of(page, this::toVO);
    }

    @Transactional(readOnly = true)
    public UserVO getById(Long id) {
        SysUser user = getEntity(id);
        dataScopeService.assertCanAccessUser(user);
        return toVO(user);
    }

    @Transactional
    public UserVO create(UserForm form) {
        resolveDept(form, true);
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (StringUtils.hasText(form.getEmail()) && userRepository.existsByEmailIgnoreCase(form.getEmail())) {
            throw new BusinessException("邮箱已被使用");
        }
        if (StringUtils.hasText(form.getPhone()) && userRepository.existsByPhone(form.getPhone())) {
            throw new BusinessException("手机号已被使用");
        }
        if (StringUtils.hasText(form.getPassword())) {
            PasswordPolicy.validate(form.getPassword());
        }
        dataScopeService.assertCanAssignDept(form.getDeptId());
        SysUser user = new SysUser();
        applyForm(user, form, true);
        boolean customPassword = StringUtils.hasText(form.getPassword());
        user.setPassword(passwordEncoder.encode(customPassword ? form.getPassword() : PasswordPolicy.randomStrong()));
        user.setPwdReset(customPassword ? 0 : 1);
        Set<SysRole> roles = resolveRoles(form.getRoleIds());
        assertCanAssignRoles(null, roles);
        user.setRoles(roles);
        user.setTenantId(resolveCreateTenant(form.getTenantId()));
        userRepository.save(user);
        return toVO(user);
    }

    @Transactional
    public UserVO update(Long id, UserForm form) {
        SysUser user = getEntity(id);
        dataScopeService.assertCanAccessUser(user);
        assertCanEditUser(user);
        if (form.getStatus() != null && user.getId().equals(SecurityUtils.getUserId())
                && normalizeStatus(form.getStatus()) != 0) {
            throw new BusinessException("不能停用当前登录用户");
        }
        resolveDept(form, false);
        if (form.getDeptId() == null && user.getDeptId() != null && !dataScopeService.current().all()) {
            throw new BusinessException("不能清空该用户的部门");
        }
        if (form.getDeptId() != null && !form.getDeptId().equals(user.getDeptId())) {
            dataScopeService.assertCanAssignDept(form.getDeptId());
        }
        if (StringUtils.hasText(form.getUsername()) && !form.getUsername().equals(user.getUsername())) {
            throw new BusinessException("用户名创建后不允许修改");
        }
        if (StringUtils.hasText(form.getEmail())
                && userRepository.existsByEmailIgnoreCaseAndIdNot(form.getEmail(), id)) {
            throw new BusinessException("邮箱已被使用");
        }
        if (StringUtils.hasText(form.getPhone())
                && userRepository.existsByPhoneAndIdNot(form.getPhone(), id)) {
            throw new BusinessException("手机号已被使用");
        }
        Integer previousStatus = user.getStatus();
        applyForm(user, form, false);
        if (form.getStatus() != null && !Objects.equals(previousStatus, user.getStatus())) {
            loginAccountService.clearAutoLock(id);
            if (user.getStatus() != null && user.getStatus() != 0) {
                tokenBlacklistService.invalidateUser(id);
            }
        }
        if (StringUtils.hasText(form.getPassword())) {
            PasswordPolicy.validate(form.getPassword());
            user.setPassword(passwordEncoder.encode(form.getPassword()));
            user.setPwdReset(1);
            tokenBlacklistService.invalidateUser(id);
        }
        if (form.getRoleIds() != null) {
            Set<SysRole> roles = resolveRoles(form.getRoleIds());
            assertCanAssignRoles(user, roles);
            assertKeepLastSuperAdmin(user, roles);
            user.setRoles(roles);
            tokenBlacklistService.invalidateUser(id);
        }
        applyTenantOnUpdate(user, form.getTenantId());
        userRepository.save(user);
        permissionService.evict(user.getId());
        return toVO(user);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = getEntity(id);
        dataScopeService.assertCanAccessUser(user);
        if (user.getId().equals(SecurityUtils.getUserId())) {
            throw new BusinessException("不能删除当前登录用户");
        }
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException("内置管理员不允许删除");
        }
        assertCanEditUser(user);
        tombstoneUniques(user);
        userRepository.save(user);
        userRepository.deleteUserRoles(id);
        userRepository.delete(user);
        permissionService.evict(id);
        tokenBlacklistService.invalidateUser(id);
    }

    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        SysUser user = getEntity(id);
        dataScopeService.assertCanAccessUser(user);
        if (status == null || status < 0 || status > 3) {
            throw new BusinessException("状态值不合法");
        }
        if (id.equals(SecurityUtils.getUserId()) && status != 0) {
            throw new BusinessException("不能停用当前登录用户");
        }
        assertCanEditUser(user);
        user.setStatus(status);
        userRepository.save(user);
        loginAccountService.clearAutoLock(id);
        permissionService.evict(id);
        if (status != 0) {
            tokenBlacklistService.invalidateUser(id);
        }
    }

    @Transactional
    public void batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            updateStatus(id, status);
        }
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        PasswordPolicy.validate(newPassword);
        SysUser user = getEntity(id);
        dataScopeService.assertCanAccessUser(user);
        assertCanEditUser(user);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPwdReset(1);
        userRepository.save(user);
        permissionService.evict(id);
        tokenBlacklistService.invalidateUser(id);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UserQuery query) {
        Specification<SysUser> spec = buildSpec(query);
        long count = userRepository.count(spec);
        if (count > MAX_EXPORT) {
            throw new BusinessException("导出条数超过 " + MAX_EXPORT + ",请缩小筛选范围");
        }
        List<SysUser> rows = userRepository.findAll(spec);
        List<String> cols = resolveExportFields(query.getFields());
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF').append(String.join(",", cols)).append('\n');
        for (SysUser row : rows) {
            List<String> vals = new ArrayList<>();
            for (String col : cols) {
                vals.add(CsvUtils.escape(exportValue(row, col)));
            }
            sb.append(String.join(",", vals)).append('\n');
        }
        return sb.toString();
    }

    public String importTemplate() {
        return "\uFEFFusername,nickname,email,phone,deptName,status\nadmin_demo,示例用户,demo@example.com,13800138000,研发部,0\n";
    }

    public ImportResultVO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 CSV 文件");
        }
        ImportResultVO result = new ImportResultVO();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                if (lineNo > MAX_IMPORT + 1) {
                    result.getErrors().add("超过最大导入行数 " + MAX_IMPORT + ",后续行已忽略");
                    break;
                }
                if (lineNo == 1 && line.toLowerCase().contains("username")) {
                    continue;
                }
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> cols = CsvUtils.parseLine(line);
                if (cols.isEmpty() || !StringUtils.hasText(cols.get(0))) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add("第 " + lineNo + " 行缺少用户名");
                    continue;
                }
                String username = cols.get(0).trim();
                if (userRepository.existsByUsername(username)) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add("第 " + lineNo + " 行用户名已存在: " + username);
                    continue;
                }
                UserForm form = new UserForm();
                form.setUsername(username);
                form.setNickname(cols.size() > 1 && StringUtils.hasText(cols.get(1)) ? cols.get(1).trim() : username);
                form.setEmail(cols.size() > 2 ? emptyToNull(cols.get(2)) : null);
                form.setPhone(cols.size() > 3 ? emptyToNull(cols.get(3)) : null);
                form.setDeptName(cols.size() > 4 ? emptyToNull(cols.get(4)) : null);
                if (cols.size() > 5 && StringUtils.hasText(cols.get(5))) {
                    try {
                        form.setStatus(Integer.parseInt(cols.get(5).trim()));
                    } catch (NumberFormatException ignored) {
                        form.setStatus(0);
                    }
                }
                try {
                    validateImported(form);
                    self.getObject().create(form);
                    result.setCreated(result.getCreated() + 1);
                } catch (Exception e) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add("第 " + lineNo + " 行: " + messageOf(e));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析 CSV 失败");
        }
        return result;
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(UserQuery query) {
        Specification<SysUser> spec = buildSpec(query);
        long count = userRepository.count(spec);
        if (count > MAX_EXPORT) {
            throw new BusinessException("导出条数超过 " + MAX_EXPORT + ",请缩小筛选范围");
        }
        List<String> cols = resolveExportFields(query.getFields());
        List<List<String>> rows = userRepository.findAll(spec).stream()
                .map(u -> cols.stream().map(c -> exportValue(u, c)).toList())
                .toList();
        try {
            return XlsxUtils.write(cols, rows);
        } catch (Exception e) {
            throw new BusinessException("导出 Excel 失败");
        }
    }

    public ImportResultVO importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        ImportResultVO result = new ImportResultVO();
        try {
            List<List<String>> rows = XlsxUtils.read(file.getInputStream());
            int lineNo = 0;
            for (List<String> row : rows) {
                lineNo++;
                if (lineNo > MAX_IMPORT + 1) {
                    result.getErrors().add("超过最大导入行数 " + MAX_IMPORT + ",后续行已忽略");
                    break;
                }
                if (lineNo == 1 && row.stream().anyMatch(c -> c != null && c.toLowerCase().contains("username"))) {
                    continue;
                }
                if (row.isEmpty() || !StringUtils.hasText(row.get(0))) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add("第 " + lineNo + " 行缺少用户名");
                    continue;
                }
                UserForm form = new UserForm();
                form.setUsername(row.get(0).trim());
                form.setNickname(row.size() > 1 && StringUtils.hasText(row.get(1)) ? row.get(1) : row.get(0));
                form.setEmail(row.size() > 2 ? emptyToNull(row.get(2)) : null);
                form.setPhone(row.size() > 3 ? emptyToNull(row.get(3)) : null);
                form.setDeptName(row.size() > 4 ? emptyToNull(row.get(4)) : null);
                if (row.size() > 5 && StringUtils.hasText(row.get(5))) {
                    try {
                        form.setStatus(Integer.parseInt(row.get(5).trim()));
                    } catch (NumberFormatException ignored) {
                        form.setStatus(0);
                    }
                }
                try {
                    validateImported(form);
                    self.getObject().create(form);
                    result.setCreated(result.getCreated() + 1);
                } catch (Exception e) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add("第 " + lineNo + " 行: " + messageOf(e));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析 Excel 失败");
        }
        return result;
    }

    private Specification<SysUser> buildSpec(UserQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("nickname")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("phone")), like)
                ));
            }
            if (query.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            if (query.getDeptId() != null) {
                predicates.add(cb.equal(root.get("deptId"), query.getDeptId()));
            }
            if (query.getRoleId() != null) {
                Join<SysUser, SysRole> roles = root.join("roles", JoinType.INNER);
                predicates.add(cb.equal(roles.get("id"), query.getRoleId()));
                cq.distinct(true);
            }
            if (query.getBeginTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), query.getBeginTime()));
            }
            if (query.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), query.getEndTime()));
            }
            dataScopeService.applyUser(root, cb, predicates);
            Long tenantLimit = TenantContext.get();
            if (tenantLimit != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantLimit));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static final List<String> DEFAULT_EXPORT_FIELDS =
            List.of("username", "nickname", "email", "phone", "deptName", "status");

    private List<String> resolveExportFields(String fields) {
        if (!StringUtils.hasText(fields)) {
            return DEFAULT_EXPORT_FIELDS;
        }
        List<String> cols = new ArrayList<>();
        for (String raw : fields.split(",")) {
            String col = raw.trim();
            if (DEFAULT_EXPORT_FIELDS.contains(col) && !cols.contains(col)) {
                cols.add(col);
            }
        }
        return cols.isEmpty() ? DEFAULT_EXPORT_FIELDS : cols;
    }

    private String exportValue(SysUser row, String col) {
        return switch (col) {
            case "username" -> nullToEmpty(row.getUsername());
            case "nickname" -> nullToEmpty(row.getNickname());
            case "email" -> nullToEmpty(row.getEmail());
            case "phone" -> nullToEmpty(row.getPhone());
            case "deptName" -> nullToEmpty(row.getDeptName());
            case "status" -> String.valueOf(row.getStatus() == null ? 0 : row.getStatus());
            default -> "";
        };
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void applyForm(SysUser user, UserForm form, boolean creating) {
        if (creating) {
            user.setUsername(form.getUsername());
        }
        user.setNickname(form.getNickname());
        user.setEmail(form.getEmail());
        user.setPhone(form.getPhone());
        if (creating || form.getAvatar() != null) {
            user.setAvatar(form.getAvatar());
        }
        if (form.getDeptId() != null) {
            SysDept dept = deptRepository.findById(form.getDeptId())
                    .orElseThrow(() -> new BusinessException("部门不存在"));
            user.setDeptId(dept.getId());
            user.setDeptName(dept.getName());
        } else {
            user.setDeptId(null);
            user.setDeptName(form.getDeptName());
        }
        if (creating || form.getStatus() != null) {
            user.setStatus(normalizeStatus(form.getStatus()));
        }
        user.setProvince(form.getProvince());
        user.setCity(form.getCity());
        user.setDistrict(form.getDistrict());
        user.setRemark(form.getRemark());
    }

    private Set<SysRole> resolveRoles(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        List<SysRole> found = roleRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw new BusinessException("部分角色不存在或已删除");
        }
        return new HashSet<>(found);
    }

    /**
     * 导入只带部门名称。未指定部门时,部门范围管理员落到自己的部门,避免用户脱离数据权限。
     */
    private void resolveDept(UserForm form, boolean creating) {
        if (form.getDeptId() != null) {
            return;
        }
        if (StringUtils.hasText(form.getDeptName())) {
            String name = form.getDeptName().trim();
            List<SysDept> matches = deptRepository.findByName(name);
            if (matches.isEmpty()) {
                throw new BusinessException("部门不存在: " + name);
            }
            if (matches.size() > 1) {
                throw new BusinessException("部门名称不唯一,请在页面指定部门: " + name);
            }
            form.setDeptId(matches.get(0).getId());
            return;
        }
        if (!creating) {
            return;
        }
        DataScopeService.Scope scope = dataScopeService.current();
        if (scope.all() || scope.selfOnly()) {
            return;
        }
        userRepository.findById(SecurityUtils.getUserId())
                .map(SysUser::getDeptId)
                .ifPresent(form::setDeptId);
    }

    private void validateImported(UserForm form) {
        if (form.getUsername() == null || !form.getUsername().matches("^[a-zA-Z0-9_]{2,50}$")) {
            throw new BusinessException("用户名只能包含 2-50 位字母、数字和下划线");
        }
        if (form.getPhone() != null && !form.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式不正确");
        }
        if (form.getEmail() != null && !form.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException("邮箱格式不正确");
        }
        normalizeStatus(form.getStatus());
    }

    private String messageOf(Exception e) {
        if (e instanceof BusinessException) {
            return e.getMessage();
        }
        return "导入失败";
    }

    private int normalizeStatus(Integer status) {
        int value = status == null ? 0 : status;
        if (value < 0 || value > 3) {
            throw new BusinessException("状态值不合法");
        }
        return value;
    }

    private boolean isCurrentSuperAdmin() {
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        return loginUser != null && loginUser.getRoles() != null && loginUser.getRoles().contains(SUPER_ADMIN);
    }

    private boolean hasSuperRole(Set<SysRole> roles) {
        return roles != null && roles.stream().anyMatch(r -> SUPER_ADMIN.equals(r.getCode()));
    }

    private boolean isPrivileged() {
        if (isCurrentSuperAdmin()) {
            return true;
        }
        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains("*");
    }

    /**
     * 已有角色可以保留。新分配的角色不能是超管,数据范围和权限码都不能超过操作者。
     */
    private void assertCanAssignRoles(SysUser existing, Set<SysRole> roles) {
        if (roles == null || roles.isEmpty() || isPrivileged()) {
            return;
        }
        Set<Long> kept = existing == null || existing.getRoles() == null
                ? Set.of()
                : existing.getRoles().stream().map(SysRole::getId).collect(Collectors.toSet());
        Set<String> mine = permissionService.loadPermissions(SecurityUtils.getUserId());
        for (SysRole role : roles) {
            if (role.getId() != null && kept.contains(role.getId())) {
                continue;
            }
            if (SUPER_ADMIN.equals(role.getCode())) {
                throw new BusinessException("不允许分配超级管理员角色");
            }
            dataScopeService.assertCanAssignDataScope(role.getDataScope());
            if (role.getPermissions() == null) {
                continue;
            }
            for (var permission : role.getPermissions()) {
                if (permission.getStatus() != null && permission.getStatus() != 0) {
                    continue;
                }
                String code = permission.getCode();
                if (StringUtils.hasText(code) && !mine.contains(code)) {
                    throw new BusinessException("不能分配超出自身权限的角色: " + role.getName());
                }
            }
        }
    }

    private void assertCanEditUser(SysUser user) {
        if (hasSuperRole(user.getRoles()) && !isCurrentSuperAdmin()) {
            throw new BusinessException("不允许修改超级管理员用户");
        }
    }

    private void assertKeepLastSuperAdmin(SysUser user, Set<SysRole> newRoles) {
        if (hasSuperRole(user.getRoles()) && !hasSuperRole(newRoles)) {
            long remaining = userRepository.findAll().stream()
                    .filter(u -> !u.getId().equals(user.getId()))
                    .filter(u -> hasSuperRole(u.getRoles()))
                    .count();
            if (remaining == 0) {
                throw new BusinessException("至少保留一名超级管理员");
            }
        }
    }

    private void tombstoneUniques(SysUser user) {
        Long id = user.getId();
        user.setUsername(UniqueFields.tombstone(user.getUsername(), id, 50));
        if (StringUtils.hasText(user.getEmail())) {
            user.setEmail(UniqueFields.tombstone(user.getEmail(), id, 100));
        }
        if (StringUtils.hasText(user.getPhone())) {
            user.setPhone(UniqueFields.tombstone(user.getPhone(), id, 20));
        }
    }

    private SysUser getEntity(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        Long limit = TenantContext.get();
        if (limit != null && !limit.equals(user.getTenantId())) {
            throw BusinessException.notFound("用户不存在");
        }
        return user;
    }

    private Long resolveCreateTenant(Long requested) {
        Long forced = TenantContext.get();
        if (forced != null) {
            if (forced < 0) {
                throw new BusinessException("当前账号未分配租户");
            }
            return forced;
        }
        if (requested != null) {
            tenantService.requireActive(requested);
            return requested;
        }
        return tenantService.defaultId();
    }

    private void applyTenantOnUpdate(SysUser user, Long requested) {
        Long forced = TenantContext.get();
        if (forced != null) {
            if (forced < 0) {
                throw new BusinessException("当前账号未分配租户");
            }
            user.setTenantId(forced);
            return;
        }
        if (requested == null || requested.equals(user.getTenantId())) {
            return;
        }
        tenantService.requireActive(requested);
        user.setTenantId(requested);
        tokenBlacklistService.invalidateUser(user.getId());
        permissionService.evict(user.getId());
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptName());
        vo.setProvince(user.getProvince());
        vo.setCity(user.getCity());
        vo.setDistrict(user.getDistrict());
        vo.setRemark(user.getRemark());
        vo.setPwdReset(user.getPwdReset());
        vo.setTenantId(user.getTenantId());
        vo.setCreateBy(user.getCreateBy());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        List<SysRole> roles = new ArrayList<>(user.getRoles());
        vo.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
        vo.setRoleNames(roles.stream().map(SysRole::getName).collect(Collectors.toList()));
        return vo;
    }
}
