ALTER TABLE platform_static.poc_config ADD COLUMN description varchar(200);
UPDATE platform_static.poc_config SET description = 'Flyway static schema POC' WHERE config_key = 'version';
