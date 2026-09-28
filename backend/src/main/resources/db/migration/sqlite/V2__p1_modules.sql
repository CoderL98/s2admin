CREATE TABLE IF NOT EXISTS sys_dept (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(50) NOT NULL,
    parent_id INTEGER DEFAULT 0,
    ancestors VARCHAR(200) DEFAULT '0',
    sort INTEGER DEFAULT 0,
    leader VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    status INTEGER DEFAULT 0,
    remark VARCHAR(500),
    create_by INTEGER,
    create_time DATETIME,
    update_by INTEGER,
    update_time DATETIME,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    original_name VARCHAR(200),
    stored_name VARCHAR(200) NOT NULL,
    url VARCHAR(500),
    content_type VARCHAR(100),
    size INTEGER DEFAULT 0,
    category VARCHAR(50) DEFAULT 'default',
    storage_type VARCHAR(20) DEFAULT 'local',
    remark VARCHAR(500),
    create_by INTEGER,
    create_time DATETIME,
    update_by INTEGER,
    update_time DATETIME,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_notice (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type INTEGER DEFAULT 1,
    status INTEGER DEFAULT 0,
    pinned INTEGER DEFAULT 0,
    publish_time DATETIME,
    remark VARCHAR(500),
    create_by INTEGER,
    create_time DATETIME,
    update_by INTEGER,
    update_time DATETIME,
    deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    sender_id INTEGER,
    sender_name VARCHAR(50),
    receiver_id INTEGER NOT NULL,
    read_flag INTEGER DEFAULT 0,
    read_time DATETIME,
    remark VARCHAR(500),
    create_by INTEGER,
    create_time DATETIME,
    update_by INTEGER,
    update_time DATETIME,
    deleted INTEGER DEFAULT 0
);
