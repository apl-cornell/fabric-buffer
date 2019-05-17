.DEFAULT_GOAL := help

# Taken from https://marmelab.com/blog/2016/02/29/auto-documented-makefile.html
help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'


.PHONY: build
build: ## Build the CLI
	./gradlew --no-daemon customFatJar
	cat make_jar_executable.sh build/libs/all-in-one-jar-1.0-SNAPSHOT.jar > fbuffer && chmod +x fbuffer

test: ## Run the test script
	python tester.py out.json