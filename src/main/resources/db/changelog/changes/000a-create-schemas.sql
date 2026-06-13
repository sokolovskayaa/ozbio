--liquibase formatted sql

--changeset ozbio:000a-create-schemas
CREATE SCHEMA IF NOT EXISTS testing;
CREATE SCHEMA IF NOT EXISTS production;
