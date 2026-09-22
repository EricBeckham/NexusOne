CREATE TABLE platform_static.poc_config (
    config_key varchar(100) PRIMARY KEY,
    config_value varchar(200) NOT NULL
);
INSERT INTO platform_static.poc_config (config_key, config_value) VALUES ('version', 'v1');
