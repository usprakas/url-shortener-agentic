package com.schwab.orchestrator.model;
@FunctionalInterface public interface StageGate { GateResult evaluate(StageContext context); }
