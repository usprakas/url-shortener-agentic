package com.schwab.orchestrator.model;
import java.util.Map;
@FunctionalInterface public interface StageAction { Map<String, String> execute(StageContext context) throws Exception; }
