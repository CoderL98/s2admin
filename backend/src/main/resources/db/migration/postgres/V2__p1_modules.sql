CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    ancestors VARCHAR(200) DEFAULT '0',
    sort INT DEFAULT 0,
    leader VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    status SMALLINT DEFAULT 0,
    remark VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_file (
    id BIGSERIAL PRIMARY KEY,
    original_name VARCHAR(200),
    stored_name VARCHAR(200) NOT NULL,
    url VARCHAR(500),
    content_type VARCHAR(100),
    size BIGINT DEFAULT 0,
    category VARCHAR(50) DEFAULT 'default',
    storage_type VARCHAR(20) DEFAULT 'local',
    remark VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_notice (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    type SMALLINT DEFAULT 1,
    status SMALLINT DEFAULT 0,
    pinned SMALLINT DEFAULT 0,
    publish_time TIMESTAMP,
    remark VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_message (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    sender_id BIGINT,
    sender_name VARCHAR(50),
    receiver_id BIGINT NOT NULL,
    read_flag SMALLINT DEFAULT 0,
    read_time TIMESTAMP,
    remark VARCHAR(500),
    create_by BIGINT,
    create_time TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
