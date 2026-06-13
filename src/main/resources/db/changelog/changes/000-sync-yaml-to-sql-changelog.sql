--liquibase formatted sql

--changeset ozbio:000-sync-yaml-to-sql-changelog splitStatements:false
-- После перехода YAML → SQL: таблицы уже есть, но Liquibase видит новые пути файлов.
-- Переносим записи databasechangelog с .yaml на .sql.
DO $$
BEGIN
    UPDATE databasechangelog
    SET filename = REPLACE(filename, '001-initial-schema.yaml', '001-initial-schema.sql')
    WHERE filename LIKE '%001-initial-schema.yaml%';

    UPDATE databasechangelog
    SET filename = REPLACE(filename, '002-seed-demo-catalog.yaml', '002-seed-demo-catalog.sql')
    WHERE filename LIKE '%002-seed-demo-catalog.yaml%';
END $$;

--changeset ozbio:000b-dedupe-sql-changelog splitStatements:false
-- Удаляет дубликаты, если sync уже выполнялся через INSERT.
DO $$
BEGIN
    DELETE FROM databasechangelog dc
    WHERE dc.filename LIKE '%001-initial-schema.sql%'
      AND dc.ctid NOT IN (
          SELECT min(inner_dc.ctid)
          FROM databasechangelog inner_dc
          WHERE inner_dc.filename LIKE '%001-initial-schema.sql%'
            AND inner_dc.id = dc.id
            AND inner_dc.author = dc.author
      );

    DELETE FROM databasechangelog dc
    WHERE dc.filename LIKE '%002-seed-demo-catalog.sql%'
      AND dc.ctid NOT IN (
          SELECT min(inner_dc.ctid)
          FROM databasechangelog inner_dc
          WHERE inner_dc.filename LIKE '%002-seed-demo-catalog.sql%'
            AND inner_dc.id = dc.id
            AND inner_dc.author = dc.author
      );
END $$;
