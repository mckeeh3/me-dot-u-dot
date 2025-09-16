# Repository Guidelines

## Project Structure & Module Organization
Java sources live under `src/main/java/com/example`, grouped into `api` Akka HTTP endpoints, `application` entities and consumers, and `domain` core models. Static web assets sit in `src/main/resources/static-resources`, while runtime configuration (including agent models) is defined in `src/main/resources/application.conf`. Tests mirror this layout in `src/test/java`, and shared fixtures belong in `src/test/resources`. Shell helpers such as `cancel-game.sh` reside in the repository root for quick local API calls.

## Build, Test, and Development Commands
Run `mvn clean verify` for a full build plus unit tests before pushing. Use `mvn test` for faster iteration during feature work. Start the Akka runtime with `mvn compile exec:java`; add `-Dakka.runtime.http-interface=0.0.0.0` when exposing the service externally. Helper scripts like `./get-games-by-player.sh <playerId>` provide manual smoke checks of HTTP endpoints.

## Coding Style & Naming Conventions
Target Java 21 with two-space indentation. Prefer fluent Akka patterns such as `effects().persistAll(...)` and use `var` for obvious local inference. Classes and records follow UpperCamelCase, methods and fields lowerCamelCase, and constants UPPER_SNAKE_CASE. REST paths stay kebab-cased and JSON fields camelCase.

## Testing Guidelines
Tests use JUnit 5 and must sit under matching packages with a `*Test` suffix (e.g., `DotGameEntityTest`). Cover both command handling and event application when touching entities or domain logic. Run `mvn test` before submitting changes, and keep scenarios deterministic with provided builders and sample move histories.

## Commit & Pull Request Guidelines
Commit titles use short, present-tense phrases under 60 characters (examples in `git log`: `remove old scoring code`, `add scoring moves to game state tool`). Pull requests should note intent, reference tracking issues, summarize manual or automated test results, and include relevant screenshots or curl transcripts. Call out configuration or migration updates so operators can adjust Akka runtime settings.

## Configuration & Secrets
Never commit API keys; rely on environment variables consumed by `application.conf` (e.g., `OPENAI_API_KEY`). Document new toggles inline with concise comments and follow the `ai-agent-model-<name>` naming pattern for agent entries.
