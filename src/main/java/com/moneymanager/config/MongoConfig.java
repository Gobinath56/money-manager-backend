package com.moneymanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * FIX #6 — Registers the MongoTransactionManager bean.
 *
 * Without this bean, @Transactional annotations on MongoDB operations are
 * silently ignored — Spring finds no transaction manager and runs without
 * one, so the rollback never fires.
 *
 * Why a separate config file?
 *   Keeps MongoTransactionManager registration isolated so it's easy to
 *   find and easy to toggle off if you ever switch to a standalone mongod
 *   that doesn't support multi-document transactions.
 *
 * PREREQUISITE:
 *   Your MongoDB instance must be a replica set.
 *   - Atlas:   always a replica set ✓
 *   - Local:   run `mongod --replSet rs0` then `rs.initiate()` in mongosh
 *   - Railway: add ?replicaSet=rs0 to your connection string if prompted
 *
 *   If you deploy on a standalone mongod and forget this, the @Transactional
 *   annotation will throw:
 *     "Command failed with error 20: Transaction numbers are only allowed on
 *      a replica set member or mongos"
 *   — a clear, actionable error rather than silent data corruption.
 */
@Configuration
public class MongoConfig {

    /**
     * Binds Spring's @Transactional mechanism to MongoDB's session-based
     * multi-document transactions. MongoDatabaseFactory is auto-configured
     * by spring-boot-starter-data-mongodb — no extra imports needed.
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}