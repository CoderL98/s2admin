-- ============================================
-- S2Admin 通用后台管理系统数据库初始化脚本
-- 数据库: s2admin
-- 创建日期: 2026-04-27
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS s2admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE s2admin;

-- ============================================
-- 1. 用户表 (sys_user)
-- ============================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    avatar VARCHAR(500) COMMENT '头像URL',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用 2锁定 3过期',
    dept_id BIGINT COMMENT '部门ID',
    dept_name VARCHAR(50) COMMENT '部门名称(冗余)',
    pwd_reset TINYINT DEFAULT 0 COMMENT '1 须修改初始/重置密码',
    remark VARCHAR(500) COMMENT '备注',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记：0未删除 1已删除',
    INDEX idx_username (username),
    INDEX idx_phone (phone),
    INDEX idx_email (email),
    INDEX idx_status (status),
    INDEX idx_dept_id (dept_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. 角色表 (sys_role)
-- ============================================
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    name VARCHAR(50) NOT NULL COMMENT '角色名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    sort INT DEFAULT 0 COMMENT '排序号',
    data_scope TINYINT DEFAULT 1 COMMENT '数据范围：1全部 2本部门及以下 3本部门 4本人',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用',
    remark VARCHAR(500) COMMENT '备注',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ============================================
-- 3. 权限表 (sys_permission)
-- ============================================
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    name VARCHAR(50) NOT NULL COMMENT '权限名称',
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    type TINYINT NOT NULL COMMENT '类型：1菜单 2按钮 3API',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    path VARCHAR(200) COMMENT '路由路径',
    icon VARCHAR(100) COMMENT '图标',
    sort INT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_code (code),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- ============================================
-- 4. 菜单表 (sys_menu)
-- ============================================
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
    name VARCHAR(50) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID',
    path VARCHAR(200) NOT NULL COMMENT '路由路径',
    component VARCHAR(255) COMMENT '组件路径',
    redirect VARCHAR(255) COMMENT '重定向路径',
    permission VARCHAR(100) COMMENT '权限标识',
    icon VARCHAR(100) COMMENT '图标',
    sort INT DEFAULT 0 COMMENT '排序号',
    type TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1目录 2菜单 3按钮',
    hidden TINYINT DEFAULT 0 COMMENT '是否隐藏：0显示 1隐藏',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_parent_id (parent_id),
    INDEX idx_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';

-- ============================================
-- 5. 用户角色关联表 (sys_user_role)
-- ============================================
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id),
    INDEX idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ============================================
-- 6. 角色权限关联表 (sys_role_permission)
-- ============================================
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ============================================
-- 7. 系统配置表 (sys_config)
-- ============================================
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value VARCHAR(500) COMMENT '配置值',
    config_type VARCHAR(20) DEFAULT 'string' COMMENT '配置类型：string number boolean',
    group_code VARCHAR(50) DEFAULT 'default' COMMENT '分组编码',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_config_key (config_key),
    INDEX idx_group_code (group_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================
-- 8. 字典类型表 (sys_dict_type)
-- ============================================
DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典类型ID',
    name VARCHAR(50) NOT NULL COMMENT '字典名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '字典编码',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用',
    remark VARCHAR(500) COMMENT '备注',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- ============================================
-- 9. 字典数据表 (sys_dict_data)
-- ============================================
DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典数据ID',
    dict_type_id BIGINT NOT NULL COMMENT '字典类型ID',
    label VARCHAR(100) NOT NULL COMMENT '字典标签',
    value VARCHAR(100) NOT NULL COMMENT '字典值',
    sort INT DEFAULT 0 COMMENT '排序号',
    status TINYINT DEFAULT 0 COMMENT '状态：0正常 1禁用',
    remark VARCHAR(500) COMMENT '备注',
    create_by BIGINT COMMENT '创建者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者ID',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    INDEX idx_dict_type_id (dict_type_id),
    INDEX idx_value (value),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- ============================================
-- 10. 登录日志表 (sys_login_log)
-- ============================================
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名',
    ip VARCHAR(50) COMMENT 'IP地址',
    location VARCHAR(200) COMMENT '登录位置',
    browser VARCHAR(100) COMMENT '浏览器',
    os VARCHAR(100) COMMENT '操作系统',
    status TINYINT DEFAULT 0 COMMENT '状态：0成功 1失败',
    message VARCHAR(500) COMMENT '提示消息',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    INDEX idx_username (username),
    INDEX idx_login_time (login_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ============================================
-- 11. 操作日志表 (sys_operation_log)
-- ============================================
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名',
    operation VARCHAR(50) COMMENT '操作类型',
    module VARCHAR(50) COMMENT '操作模块',
    method VARCHAR(100) COMMENT '方法名',
    url VARCHAR(200) COMMENT '请求路径',
    ip VARCHAR(50) COMMENT 'IP地址',
    location VARCHAR(200) COMMENT '操作位置',
    old_value TEXT COMMENT '旧值',
    new_value TEXT COMMENT '新值',
    status TINYINT DEFAULT 0 COMMENT '状态：0成功 1失败',
    error_msg TEXT COMMENT '错误信息',
    execute_time BIGINT COMMENT '执行时长(ms)',
    operation_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_user_id (user_id),
    INDEX idx_operation_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ============================================
-- 12. 异常日志表 (sys_error_log)
-- ============================================
DROP TABLE IF EXISTS sys_error_log;
CREATE TABLE sys_error_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    trace_id VARCHAR(50) COMMENT '追踪ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(50) COMMENT '用户名',
    ip VARCHAR(50) COMMENT 'IP地址',
    url VARCHAR(200) COMMENT '请求路径',
    method VARCHAR(100) COMMENT '方法名',
    params TEXT COMMENT '请求参数',
    exception TEXT COMMENT '异常信息',
    stack_trace TEXT COMMENT '堆栈信息',
    error_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '错误时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_error_time (error_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常日志表';

-- ============================================
-- 初始化数据
-- ============================================

-- 插入超级管理员用户 (密码: admin123，使用BCrypt加密)
INSERT INTO sys_user (username, password, nickname, email, phone, status, create_by, create_time) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '超级管理员', 'admin@example.com', '13800138000', 0, 1, NOW()); -- 密码: admin123

-- 插入内置角色
INSERT INTO sys_role (name, code, sort, data_scope, status, remark, create_by, create_time) VALUES
('超级管理员', 'SUPER_ADMIN', 1, 1, 0, '系统最高权限，可管理所有功能', 1, NOW()),
('系统管理员', 'ADMIN', 2, 1, 0, '系统管理功能，不可删除超级管理员', 1, NOW()),
('普通用户', 'USER', 3, 4, 0, '基本功能使用权限', 1, NOW()),
('审计员', 'AUDITOR', 4, 1, 0, '仅查看日志，无操作权限', 1, NOW()),
('访客', 'GUEST', 5, 4, 0, '只读权限', 1, NOW());

-- 为超级管理员分配角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 插入默认菜单(id 按插入顺序: 1-12,监控=7,工具=11)
INSERT INTO sys_menu (name, parent_id, path, component, icon, permission, sort, type, status, create_by, create_time) VALUES
('系统管理', 0, '/system', 'Layout', 'Setting', NULL, 1, 1, 0, 1, NOW()),
('用户管理', 1, '/system/user', '/system/user/index', 'User', 'system:user:view', 1, 2, 0, 1, NOW()),
('角色管理', 1, '/system/role', '/system/role/index', 'Role', 'system:role:view', 2, 2, 0, 1, NOW()),
('菜单管理', 1, '/system/menu', '/system/menu/index', 'Menu', 'system:menu:view', 3, 2, 0, 1, NOW()),
('权限管理', 1, '/system/permission', '/system/permission/index', 'Shield', 'system:permission:view', 4, 2, 0, 1, NOW()),
('系统配置', 1, '/system/config', '/system/config/index', 'Tool', 'system:config:view', 5, 2, 0, 1, NOW()),
('系统监控', 0, '/monitor', 'Layout', 'Monitor', NULL, 2, 1, 0, 1, NOW()),
('登录日志', 7, '/monitor/login-log', '/monitor/login-log/index', 'Log', 'monitor:log:view', 1, 2, 0, 1, NOW()),
('操作日志', 7, '/monitor/op-log', '/monitor/op-log/index', 'FileText', 'monitor:log:view', 2, 2, 0, 1, NOW()),
('异常日志', 7, '/monitor/error-log', '/monitor/error-log/index', 'AlertTriangle', 'monitor:log:view', 3, 2, 0, 1, NOW()),
('系统工具', 0, '/tools', 'Layout', 'Wrench', NULL, 3, 1, 0, 1, NOW()),
('字典管理', 11, '/tools/dict', '/tools/dict/index', 'Book', 'tools:dict:view', 1, 2, 0, 1, NOW()),
('代码生成', 11, '/tools/build', '/tools/build/index', 'Code', NULL, 2, 2, 0, 1, NOW());

-- 插入默认权限
INSERT INTO sys_permission (name, code, type, parent_id, status, create_by, create_time) VALUES
('系统管理', 'system', 1, 0, 0, 1, NOW()),
('用户查看', 'system:user:view', 2, 1, 0, 1, NOW()),
('用户新增', 'system:user:add', 2, 1, 0, 1, NOW()),
('用户编辑', 'system:user:edit', 2, 1, 0, 1, NOW()),
('用户删除', 'system:user:delete', 2, 1, 0, 1, NOW()),
('角色查看', 'system:role:view', 2, 1, 0, 1, NOW()),
('角色新增', 'system:role:add', 2, 1, 0, 1, NOW()),
('角色编辑', 'system:role:edit', 2, 1, 0, 1, NOW()),
('角色删除', 'system:role:delete', 2, 1, 0, 1, NOW()),
('角色授权', 'system:role:assign', 2, 1, 0, 1, NOW()),
('菜单查看', 'system:menu:view', 2, 1, 0, 1, NOW()),
('菜单新增', 'system:menu:add', 2, 1, 0, 1, NOW()),
('菜单编辑', 'system:menu:edit', 2, 1, 0, 1, NOW()),
('菜单删除', 'system:menu:delete', 2, 1, 0, 1, NOW()),
('权限查看', 'system:permission:view', 2, 1, 0, 1, NOW()),
('权限新增', 'system:permission:add', 2, 1, 0, 1, NOW()),
('权限编辑', 'system:permission:edit', 2, 1, 0, 1, NOW()),
('权限删除', 'system:permission:delete', 2, 1, 0, 1, NOW()),
('配置查看', 'system:config:view', 2, 1, 0, 1, NOW()),
('配置新增', 'system:config:add', 2, 1, 0, 1, NOW()),
('配置编辑', 'system:config:edit', 2, 1, 0, 1, NOW()),
('配置删除', 'system:config:delete', 2, 1, 0, 1, NOW()),
('监控中心', 'monitor', 1, 0, 0, 1, NOW()),
('日志查看', 'monitor:log:view', 2, 23, 0, 1, NOW()),
('日志清空', 'monitor:log:delete', 2, 23, 0, 1, NOW()),
('工具中心', 'tools', 1, 0, 0, 1, NOW()),
('字典查看', 'tools:dict:view', 2, 26, 0, 1, NOW()),
('字典编辑', 'tools:dict:edit', 2, 26, 0, 1, NOW());

-- 为超级管理员角色分配所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE deleted = 0;

-- 插入字典类型
INSERT INTO sys_dict_type (name, code, status, remark, create_by, create_time) VALUES
('用户状态', 'user_status', 0, '用户状态字典', 1, NOW()),
('角色状态', 'role_status', 0, '角色状态字典', 1, NOW()),
('菜单类型', 'menu_type', 0, '菜单类型字典', 1, NOW()),
('权限类型', 'permission_type', 0, '权限类型字典', 1, NOW()),
('登录状态', 'login_status', 0, '登录状态字典', 1, NOW());

-- 插入字典数据
INSERT INTO sys_dict_data (dict_type_id, label, value, sort, status, create_by, create_time) VALUES
(1, '正常', '0', 1, 0, 1, NOW()),
(1, '禁用', '1', 2, 0, 1, NOW()),
(1, '锁定', '2', 3, 0, 1, NOW()),
(1, '过期', '3', 4, 0, 1, NOW()),
(2, '正常', '0', 1, 0, 1, NOW()),
(2, '禁用', '1', 2, 0, 1, NOW()),
(3, '目录', '1', 1, 0, 1, NOW()),
(3, '菜单', '2', 2, 0, 1, NOW()),
(3, '按钮', '3', 3, 0, 1, NOW()),
(4, '菜单权限', '1', 1, 0, 1, NOW()),
(4, '按钮权限', '2', 2, 0, 1, NOW()),
(4, 'API权限', '3', 3, 0, 1, NOW()),
(5, '成功', '0', 1, 0, 1, NOW()),
(5, '失败', '1', 2, 0, 1, NOW());

-- 插入系统配置
INSERT INTO sys_config (config_key, config_value, config_type, group_code, remark, create_by, create_time) VALUES
('sys.user.initPassword', '123456', 'string', 'system', '用户初始密码', 1, NOW()),
('sys.account.captchaEnabled', 'true', 'boolean', 'system', '是否启用验证码', 1, NOW()),
('sys.account.captchaExpiration', '5', 'number', 'system', '验证码有效期(分钟)', 1, NOW());