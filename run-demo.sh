#!/usr/bin/env sh
set -eu
./mvnw -q -pl orchestrator-api -am package -DskipTests
java -cp "orchestrator-api/target/orchestrator-api-0.1.0-SNAPSHOT.jar:agents/target/agents-0.1.0-SNAPSHOT.jar:policy-engine/target/policy-engine-0.1.0-SNAPSHOT.jar:orchestrator-core/target/orchestrator-core-0.1.0-SNAPSHOT.jar" com.schwab.orchestrator.api.DemoMain
