package com.schwab.orchestrator.model;
@FunctionalInterface public interface RollbackAction { void rollback(StageContext context) throws Exception; }
