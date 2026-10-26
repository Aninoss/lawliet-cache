# AGENTS.md

Spring Boot 3.1 / Java 17 / Gradle (wrapper 8.8) REST service: the web cache for the Lawliet Discord bot. Single module, root package `xyz.lawlietcache`, entrypoint `Main` (calls `Program.init()` then `SpringApplication.run`).

## Commands
- Build: `./gradlew build` (jar main class is set manually in `build.gradle`); container image via `./gradlew jib` (base `eclipse-temurin:17.0.11_9-jdk`, `src/main/jib` is empty).
- Run: `./gradlew bootRun` — requires the env vars below or it crashes at startup/class init.
- No tests exist (`src/test` is empty) and no lint/format/CI config; verify with `./gradlew compileJava`.
- `build/`, `out/`, `.gradle/`, `.idea/` are IDE/build artifacts; don't edit them. `lib/` is a flatDir repo (empty).

## Environment (read via `System.getenv`, no defaults — missing values NPE/NumberFormatException)
Many are read in `static final` fields, so a missing var fails on class load.
- Core: `PORT` (also `server.port` in `application.properties`), `PRODUCTION` (must be set; `"true"` enables auth), `AUTH`, `HOSTNAME`, `REDIS_HOST`, `REDIS_PORT`, `WEBCACHE_ROOT_PATH`, `DOMAIN_BLOCKER`, `STOP_ON_CONNECTION_ERROR`
- Booru: `DANBOORU_LOGIN`, `DANBOORU_API_TOKEN`, `RULE34_API_KEY`, `RULE34_USER_ID`, `MAX_BOORU_ALERT_REQUESTS_PER_SECOND`, `LOG_BLOCKED_BOORU_ALERT_REQUESTS`
- Media-server proxy: `MS_MAX_SHARDS`, `MS_SHARD_<n>`, `MS_PROXY_AUTH`, `MS_PROXY_MAX_FILE_SIZE_MB`, `MS_PROXY_HOSTS_<ProxyTarget>`, `MS_PROXY_HEADERS_<ProxyTarget>`
- Others: `PIXIV_REFRESH_TOKEN`, `TWITCH_CLIENT_IDS`
- Redis is required (locks, counters, tag-usage logging). Cache files are also written to disk under `WEBCACHE_ROOT_PATH`.

## Architecture
- `core/ApiController` exposes everything under `/api/` (`/ping`, `/booru_v2`, `cached_proxy`, reddit, pixiv, twitch, ...). `StaticTokenFilter` requires `Authorization: <AUTH>` only when `PRODUCTION=true`; otherwise auth is skipped.
- `core/WebCache`: shared cached HTTP layer (OkHttp, domain blocker, proxy routing through the media-server shards). Downloaders go through it.
- Per-source packages: `booru/` (`BoardType` enum maps domain -> board implementation; `counters/` per-site result counters, `customboards/`, `workaroundsearcher/`, `autocomplete/`), `reddit/`, `pixiv/`, `twitch/`. Each source has its own `exception/` package whose exceptions are surfaced by `core/ExceptionFilter`/controller.
- `BooruDownloader` calls its own `http://localhost:$PORT/api/cached_proxy` with the `AUTH` header; `CheckOwnConnection` pings `/api/ping` the same way.
- Adding a booru site generally means touching `BoardType` plus a matching counter/downloader branch in `BooruDownloader` (CDN rewrites like `rule34UrlToOwnCDN` are per-site).

## Dependencies
- `imageboard-api` and `pixivj` come from JitPack forks (`com.github.Aninoss`) pinned by commit hash; `dv8tion` m2 repo is also configured.
