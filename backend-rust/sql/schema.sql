CREATE TABLE IF NOT EXISTS sys_config (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    config_key TEXT NOT NULL UNIQUE,
    config_type TEXT,
    config_value TEXT,
    group_code TEXT
);

CREATE TABLE IF NOT EXISTS sys_dept (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    ancestors TEXT,
    email TEXT,
    leader TEXT,
    name TEXT NOT NULL,
    parent_id INTEGER,
    phone TEXT,
    sort INTEGER,
    status INTEGER
);

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    dict_type_id INTEGER NOT NULL,
    label TEXT NOT NULL,
    sort INTEGER,
    status INTEGER,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    status INTEGER
);

CREATE TABLE IF NOT EXISTS sys_error_log (
    id INTEGER PRIMARY KEY,
    error_time TEXT,
    exception TEXT,
    ip TEXT,
    method TEXT,
    params TEXT,
    stack_trace TEXT,
    trace_id TEXT,
    url TEXT,
    user_id INTEGER,
    username TEXT
);

CREATE TABLE IF NOT EXISTS sys_file (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    category TEXT,
    content_type TEXT,
    original_name TEXT,
    size INTEGER,
    storage_type TEXT,
    stored_name TEXT NOT NULL,
    url TEXT
);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id INTEGER PRIMARY KEY,
    browser TEXT,
    ip TEXT,
    location TEXT,
    login_time TEXT,
    message TEXT,
    os TEXT,
    status INTEGER,
    user_id INTEGER,
    username TEXT
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    component TEXT,
    hidden INTEGER,
    icon TEXT,
    name TEXT NOT NULL,
    parent_id INTEGER,
    path TEXT NOT NULL,
    permission TEXT,
    redirect TEXT,
    sort INTEGER,
    status INTEGER,
    type INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_message (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    content TEXT,
    read_flag INTEGER,
    read_time TEXT,
    receiver_id INTEGER NOT NULL,
    sender_id INTEGER,
    sender_name TEXT,
    title TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_notice (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    content TEXT,
    pinned INTEGER,
    publish_time TEXT,
    status INTEGER,
    title TEXT NOT NULL,
    type INTEGER
);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id INTEGER PRIMARY KEY,
    error_msg TEXT,
    execute_time INTEGER,
    ip TEXT,
    location TEXT,
    method TEXT,
    module TEXT,
    new_value TEXT,
    old_value TEXT,
    operation TEXT,
    operation_time TEXT,
    status INTEGER,
    url TEXT,
    user_id INTEGER,
    username TEXT
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    code TEXT NOT NULL UNIQUE,
    icon TEXT,
    name TEXT NOT NULL,
    parent_id INTEGER,
    path TEXT,
    sort INTEGER,
    status INTEGER,
    type INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sys_role (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    code TEXT NOT NULL UNIQUE,
    data_scope INTEGER,
    name TEXT NOT NULL,
    sort INTEGER,
    status INTEGER
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY,
    create_by INTEGER,
    create_time TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    update_by INTEGER,
    update_time TEXT,
    avatar TEXT,
    dept_id INTEGER,
    dept_name TEXT,
    email TEXT,
    nickname TEXT,
    password TEXT NOT NULL,
    phone TEXT,
    status INTEGER,
    username TEXT NOT NULL UNIQUE,
    pwd_reset INTEGER,
    province TEXT,
    city TEXT,
    district TEXT
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
