# Makefile shortcuts for Knox Token Utility
# Environment overrides: KNOX_REGION=us|eu|ap, KNOX_BASE_URL, KNOX_API_VERSION

MVN ?= mvn
JAR := target/knox-token-utility-1.0.0-jar-with-dependencies.jar
MAIN := com.samsung.knoxwsm.token.Launcher

.PHONY: build fast lowspace clean run-server token enroll refresh validate docker-build server

build:
	$(MVN) -q -DskipTests=false clean verify

fast:
	$(MVN) -q -Pfast -DskipTests clean package

lowspace:
	$(MVN) -q -Plowspace -DskipTests clean package

clean:
	$(MVN) -q clean

run-server: build
	java -cp target/classes:target/dependency/* $(MAIN) server

server: run-server

fatjar: build
	@test -f $(JAR) || echo "Fat JAR not found; ensure assembly plugin executed."

token: fatjar
	./scripts/request-access-token.sh $$CLIENT_ID

refresh: fatjar
	./scripts/refresh-access-token.sh $$CLIENT_ID $$REFRESH_TOKEN_SPEC

validate: fatjar
	./scripts/validate-access-token.sh $$CLIENT_ID $$ACCESS_TOKEN_SPEC

enroll: fatjar
	./scripts/enroll-device.sh $$CLIENT_ID $$IMEI

docker-build:
	docker build -t knox-token-util:latest .
