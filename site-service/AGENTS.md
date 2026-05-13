# AGENTS.md

## Scope and Service Shape
- This repo is a **SiteOps microservice** (`siteops-service`) in a broader BuildSmart system, not the full monolith described in older docs; entrypoint is `src/main/java/com/buildsmart/BuildSmartApplication.java`.
- Main bounded context is under `src/main/java/com/buildsmart/siteops` with layered packages: `controller`, `service/impl`, `repository`, `entity`, `dto`, `security`, `notification`, `validator`.
- Core business flow is **Site Engineer submission -> PM approval lifecycle -> notifications** across three entities: Site Logs, Issues, Resource Requests.

## Architecture and Data Flow You Must Preserve
- Create operations in `SiteLogServiceImpl`, `IssueServiceImpl`, and `ResourceRequestServiceImpl` generate business IDs via `IdGeneratorUtil` (e.g., `LOGBS###`, `ISSBS###`, `RREBS###`) and then create an approval record (`APRSE###`).
- PM actions are centralized in `SiteOpsApprovalServiceImpl`: `approve/reject` updates both `site_ops_approvals` and the parent entity status; do not bypass this service for state transitions.
- Notifications are stored locally (`NotificationEntity`) and routed by synthetic user IDs like `PM-{projectId}` (see `SiteOpsApprovalServiceImpl` + `NotificationController`).
- Cross-service checks happen through Feign, not direct DB links: project existence via `ProjectServiceClient`; user-active checks via `IAMServiceClient`.

## Security Model (Critical)
- JWT is mandatory for almost all routes; security chain in `security/SecurityConfig.java` permits only `/actuator/health`, `/internal/**`, and `/api/sitelogs/instance-info` anonymously.
- `JwtAuthenticationFilter` validates signature/claims and also calls `UserValidationService` to ensure the user is still ACTIVE in IAM.
- User validation is intentionally **fail-open** when IAM is unreachable (logged warning, request allowed); keep this behavior unless requirements explicitly change.
- Controllers derive actor IDs from JWT using `AuthenticationHelper` (e.g., `submittedBy`, `reportedBy`, `requestedBy`) rather than accepting them in request bodies.

## Validation and API Conventions
- Request DTOs are Java records with Jakarta validation; SiteLog/Issue text fields use custom `@WordCount` (`validator/constraint/WordCountValidator`).
- Many list endpoints expose both non-paginated and `/paginated/...` variants; sort fields are endpoint-whitelisted via `PaginationUtil.normalizeSortBy`.
- Error response envelope is standardized by `exception/GlobalExceptionHandler` (`timestamp`, `status`, `error`, `message`).
- IDs are String business keys (not numeric autoincrement) across entities.

## Local Dev Workflow
- Runtime/config is in `src/main/resources/application.properties` (MySQL `bs_siteops`, port `8087`, Eureka, Feign targets, JWT secret, uploads dir).
- Use Maven wrapper from repo root:
```bash
./mvnw clean test
./mvnw spring-boot:run
```
- On Windows PowerShell:
```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```
- Use `siteops-pm-communication-tests.http` as the most accurate end-to-end workflow reference (SE create -> PM approve/reject -> revise -> notification checks).

## Known Gaps / Practical Notes for Agents
- Automated tests are minimal (`BuildSmartApplicationTests` only); verify behavior through targeted controller/service tests or the provided `.http` flow file.
- `README.md` has stale module descriptions; trust code and `application.properties` for current service boundaries.
- Keep API compatibility for dual route mappings where present (e.g., `SiteLogController` supports `/api/sitelogs` and `/api/siteops/sitelogs`).

