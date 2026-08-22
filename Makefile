# Heron Forge developer workflow.
#
# Requires JDK 25 (auto-provisioning is disabled; see gradle.properties).
# If SDKMAN is installed, JAVA_HOME is pointed at its current JDK.

ifeq ($(wildcard $(HOME)/.sdkman/candidates/java/current),)
# Leave JAVA_HOME untouched when SDKMAN is absent.
else
export JAVA_HOME := $(HOME)/.sdkman/candidates/java/current
endif

GRADLE := ./gradlew
HERON := bricks/heron-platform-cli/build/install/heron/bin/heron
DEMO_COMPOSE := examples/factory-demo/docker-compose.yml

HERON_DB_URL ?= jdbc:postgresql://localhost:5432/heron_demo
HERON_DB_USER ?= heron
HERON_DB_PASSWORD ?= heron
RUN_POSTGRES_TESTS ?= true

.PHONY: help
help:
	@echo "Targets:"
	@echo "  make check        full gate: tests + static analysis + coverage"
	@echo "  make publish      publish platform artifacts to mavenLocal()"
	@echo "  make cli          build the heron CLI distribution"
	@echo "  make db-up        start the factory-demo PostgreSQL fixture"
	@echo "  make db-down      stop the PostgreSQL fixture"
	@echo "  make integration  opt-in Postgres-backed test suites"
	@echo "  make demo-run     build and start the factory-demo shell (Ctrl-C to stop)"
	@echo "  make e2e          scaffold an app, wire a DB endpoint, boot it, probe HTTP"
	@echo "  make clean        clean all Gradle build outputs"

.PHONY: check
check:
	$(GRADLE) check

.PHONY: publish
publish:
	$(GRADLE) publishToMavenLocal

.PHONY: cli
cli:
	$(GRADLE) :bricks:heron-platform-cli:installDist

.PHONY: db-up
db-up:
	docker compose -f $(DEMO_COMPOSE) up -d --wait

.PHONY: db-down
db-down:
	docker compose -f $(DEMO_COMPOSE) down

.PHONY: integration
integration: db-up
	HERON_DB_URL=$(HERON_DB_URL) HERON_DB_USER=$(HERON_DB_USER) \
	HERON_DB_PASSWORD=$(HERON_DB_PASSWORD) RUN_POSTGRES_TESTS=true \
	$(GRADLE) :bricks:heron-platform-data-postgresql:test \
	          :examples:external-provider:test \
	          :examples:factory-demo:test

.PHONY: demo-run
demo-run: db-up
	HERON_DB_URL=$(HERON_DB_URL) HERON_DB_USER=$(HERON_DB_USER) \
	HERON_DB_PASSWORD=$(HERON_DB_PASSWORD) \
	$(GRADLE) :examples:factory-demo:installDist
	HERON_DB_URL=$(HERON_DB_URL) HERON_DB_USER=$(HERON_DB_USER) \
	HERON_DB_PASSWORD=$(HERON_DB_PASSWORD) \
	examples/factory-demo/build/install/factory-demo/bin/heron start \
	  --config examples/factory-demo/src/main/resources/supply-chain.yaml

.PHONY: e2e
e2e: publish cli db-up
	HERON_DB_URL=$(HERON_DB_URL) HERON_DB_USER=$(HERON_DB_USER) \
	HERON_DB_PASSWORD=$(HERON_DB_PASSWORD) \
	scripts/e2e-demo.sh

.PHONY: clean
clean:
	$(GRADLE) clean
