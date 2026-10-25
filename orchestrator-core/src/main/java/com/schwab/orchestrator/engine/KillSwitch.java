package com.schwab.orchestrator.engine;
import java.util.concurrent.atomic.AtomicBoolean;
public final class KillSwitch { private final AtomicBoolean engaged = new AtomicBoolean(); public void engage() { engaged.set(true); } public boolean engaged() { return engaged.get(); } }
