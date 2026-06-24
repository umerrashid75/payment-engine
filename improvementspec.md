# Improvement Spec — Implementation Guide for Claude Code

This file is an executable spec. Each section is a self-contained task: what to change, where, the exact code to write, and how to verify. Work top-to-bottom; later tasks assume earlier ones are done.

**Conventions**
- All paths are relative to repo root `C:\BuizelCodes\payment-engine`.
- Verify each task with `./mvnw.cmd clean compile -DskipTests` before moving on.
- Do not commit unless explicitly asked.

---

## Task 0 — Stage the missing entrypoint and wrapper

**Why:** `core-api` has no `main` class committed; the build wrapper isn't tracked.

**Files to add to git** (already on disk, currently untracked):
- `core-api/src/main/java/com/coreissuer/api/CoreApiApplication.java`
- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.jar`
- `.mvn/wrapper/maven-wrapper.properties`

**Action:** Do not commit yet. Confirm `git status` lists them under "Untracked" and leave them for a single commit at the end of all tasks.

---

## Task 1 — Add `application.yml` for `core-api`

**Why:** Spring Boot needs datasource, Flyway, and JPA settings to start. Currently `core-api/src/main/resources/` only contains `db/migration/`.

**Create** `core-api/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: core-api
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/coreissuer?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: ${DB_USER:coreissuer}
    password: ${DB_PASSWORD:coreissuer}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
        jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false

server:
  port: ${SERVER_PORT:8080}

coreissuer:
  security:
    pepper: ${COREISSUER_PEPPER}

logging:
  level:
    com.coreissuer: INFO
    org.hibernate.SQL: WARN
```

**Note:** `coreissuer.security.pepper` has no default — startup fails if `COREISSUER_PEPPER` is unset. This is intentional (see Task 4).

**Verify:** `./mvnw.cmd -pl core-api compile` still passes.

---

## Task 2 — Add `application.yml` for `admin-struts`

**Why:** Same reason as Task 1, plus the port currently bound via `System.setProperty` (Task 8).

**Create** `admin-struts/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: admin-struts
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/coreissuer?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: ${DB_USER:coreissuer}
    password: ${DB_PASSWORD:coreissuer}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
  flyway:
    enabled: false  # core-api owns migrations

server:
  port: ${SERVER_PORT:8081}

logging:
  level:
    com.coreissuer: INFO
```

**Verify:** `./mvnw.cmd -pl admin-struts compile`.

---

## Task 3 — Remove the explicit `mysql-connector-java` version

**Why:** Triggers Maven relocation warning. Spring Boot 2.7.18 BOM already manages the version.

**Edit** `core-api/pom.xml`. Find:

```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
    <scope>runtime</scope>
</dependency>
```

**Replace with** the new coordinates (no version — managed by parent):

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

**Verify:** `./mvnw.cmd clean compile -DskipTests` — relocation warning should be gone.

---

## Task 4 — Drop the hardcoded pepper default

**Why:** Silent fallback to `default-secret-pepper` defeats the security purpose of a pepper.

**Edit** `core-api/src/main/java/com/coreissuer/api/service/CardService.java:24`. Change:

```java
@Value("${coreissuer.security.pepper:default-secret-pepper}")
private String pepper;
```

**To:**

```java
@Value("${coreissuer.security.pepper}")
private String pepper;
```

(No default → Spring fails fast at startup if the property is unset, which matches the YAML in Task 1.)

**Verify:** Compile passes. Manually check that `application.yml` no longer provides a literal default either.

---

## Task 5 — Fix hardcoded `MERCHANT_ACCOUNT_ID` in `TransactionService`

**Why:** Every capture/refund posts to `acc-merchant-1` regardless of the request's merchant id. Accounting bug as soon as a second merchant exists.

### 5a. Add a finder on `AccountRepository`

**Edit** `common/src/main/java/com/coreissuer/common/repository/AccountRepository.java`. Add:

```java
@Query("select a from Account a where a.id = :externalId")
Optional<Account> findByExternalId(@Param("externalId") String externalId);
```

If the repo already has a similarly-named method, reuse it. The merchant id in the seed data appears as a string like `"acc-merchant-1"` and is stored in the `account.id` column — so `findById(String)` is what we need. Confirm by reading `V2__seed_data.sql` first.

> If `account.id` is the canonical merchant key, no new method is needed — use `accountRepository.findByIdForUpdate(request.getMerchantId())` once we map merchant → account id. The clean fix is a separate `merchant_account` mapping table; for now the minimal change is to derive the account id from the request.

### 5b. Edit `TransactionService.java`

**Replace** the constant on line 39:

```java
private static final String MERCHANT_ACCOUNT_ID = "acc-merchant-1";
```

**With** a small helper that resolves the merchant account id from a `Transaction` (or the request). Suggested pattern — derive `"acc-merchant-" + merchantId` only if the seed data follows that convention, otherwise add a `merchant_account_id` column. **Confirm the seed convention by reading `V2__seed_data.sql` before choosing.**

In `capture(...)` and `refund(...)`, replace:

```java
Account merchantAccount = accountRepository.findByIdForUpdate(MERCHANT_ACCOUNT_ID)
        .orElseThrow();
```

with:

```java
String merchantAccountId = resolveMerchantAccountId(transaction.getMerchantId());
Account merchantAccount = accountRepository.findByIdForUpdate(merchantAccountId)
        .orElseThrow(() -> new AccountNotFoundException(merchantAccountId));
```

And add the helper:

```java
private String resolveMerchantAccountId(String merchantId) {
    // TODO: replace with a real merchant->account mapping table.
    return "acc-merchant-" + merchantId;
}
```

**Verify:** Compile passes. Add a TODO comment referencing the lookup-table follow-up.

---

## Task 6 — Replace silent error swallows with SLF4J

### 6a. `ReconciliationService.writeReportToFile`

**Edit** `core-api/src/main/java/com/coreissuer/api/reconciliation/ReconciliationService.java`.

Add at the top (after package/imports):

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

Add a static logger field inside the class:

```java
private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
```

Replace the `catch` block in `writeReportToFile`:

```java
} catch (IOException e) {
    e.printStackTrace();
}
```

with:

```java
} catch (IOException e) {
    log.error("Failed to write reconciliation report to {}", file, e);
    throw new ReconciliationReportException("Failed to write reconciliation report", e);
}
```

Create `core-api/src/main/java/com/coreissuer/api/reconciliation/ReconciliationReportException.java`:

```java
package com.coreissuer.api.reconciliation;

public class ReconciliationReportException extends RuntimeException {
    public ReconciliationReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 6b. `TransactionService.publishEvent`

**Edit** `core-api/src/main/java/com/coreissuer/api/service/TransactionService.java`.

Add imports + logger field (same pattern as 6a).

Replace the `catch` block in `publishEvent`:

```java
} catch (Exception e) {
    System.err.println("Failed to serialize transaction event: " + e.getMessage());
}
```

with:

```java
} catch (Exception e) {
    log.error("Failed to serialize transaction event txId={}", tx.getId(), e);
}
```

(Do not rethrow — event publication failure must not fail the surrounding payment transaction.)

**Verify:** `./mvnw.cmd compile`.

---

## Task 7 — Replace generic `RuntimeException` with domain exceptions

**Why:** Project Java rules require domain-specific exceptions. Generic `RuntimeException` makes `@ControllerAdvice` mapping impossible.

### 7a. Create exception classes

Create the following under `core-api/src/main/java/com/coreissuer/api/exception/`:

**`CardNotFoundException.java`:**
```java
package com.coreissuer.api.exception;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException(String id) {
        super("Card not found: id=" + id);
    }
}
```

**`AccountNotFoundException.java`:**
```java
package com.coreissuer.api.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String id) {
        super("Account not found: id=" + id);
    }
}
```

**`TransactionNotFoundException.java`:**
```java
package com.coreissuer.api.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String id) {
        super("Transaction not found: id=" + id);
    }
}
```

**`AuthorizationStrategyNotFoundException.java`:**
```java
package com.coreissuer.api.exception;

public class AuthorizationStrategyNotFoundException extends RuntimeException {
    public AuthorizationStrategyNotFoundException(String currency, String country) {
        super("No authorization strategy for currency=" + currency + " country=" + country);
    }
}
```

### 7b. Replace call sites

In `TransactionService.java`:
- `new RuntimeException("Card not found")` → `new CardNotFoundException(request.getCardId())`
- `new RuntimeException("Account not found")` → `new AccountNotFoundException(card.getAccount().getId())`
- `new RuntimeException("Transaction not found")` → `new TransactionNotFoundException(transactionId)`
- `new RuntimeException("Network settlement account missing")` → `new AccountNotFoundException(NETWORK_SETTLEMENT_ACCOUNT_ID)`
- `new RuntimeException("No suitable authorization strategy found")` → `new AuthorizationStrategyNotFoundException(request.getCurrency(), "US")`
- All bare `.orElseThrow()` calls in `capture/reverse/refund` for accounts → `.orElseThrow(() -> new AccountNotFoundException(...))`

In `CardService.java`:
- `new RuntimeException("Card not found")` → `new CardNotFoundException(id)`

### 7c. Add a `@ControllerAdvice` for HTTP mapping

Create `core-api/src/main/java/com/coreissuer/api/exception/GlobalExceptionHandler.java`:

```java
package com.coreissuer.api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({CardNotFoundException.class, AccountNotFoundException.class, TransactionNotFoundException.class})
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "resource_not_found"));
    }

    @ExceptionHandler(AuthorizationStrategyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleStrategy(AuthorizationStrategyNotFoundException ex) {
        log.warn(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "unsupported_route"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "internal_server_error"));
    }
}
```

**Verify:** `./mvnw.cmd compile` — all imports resolve, no remaining generic `RuntimeException` literals in `TransactionService` / `CardService`.

---

## Task 8 — Move admin port from `System.setProperty` to YAML

**Why:** `System.setProperty("server.port", "8081")` runs before Spring config is read but bypasses profiles and env overrides.

**Edit** `admin-struts/src/main/java/com/coreissuer/admin/AdminApplication.java`:

```java
package com.coreissuer.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.coreissuer.admin", "com.coreissuer.common"})
@EntityScan(basePackages = "com.coreissuer.common.domain")
@EnableJpaRepositories(basePackages = "com.coreissuer.common.repository")
public class AdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
```

(Port is now set in the `application.yml` from Task 2.)

**Verify:** `./mvnw.cmd -pl admin-struts compile`.

---

## Task 9 — Add `.gitattributes` for line endings

**Why:** `git diff` warns `LF will be replaced by CRLF` on every modified file. Lock the policy.

**Create** `.gitattributes` at repo root:

```
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.sh text eol=lf
*.jar binary
```

**Verify:** `git status` no longer warns on the next edit.

---

## Task 10 — Tests (separate branch)

**Do not implement in the same commit as Tasks 1–9.** This is a multi-day item.

### Suggested initial test files

`core-api/src/test/java/com/coreissuer/api/service/TransactionServiceTest.java`
- Mock all repositories with Mockito.
- Cover: authorize success, authorize insufficient funds, authorize fraud decline, authorize card-not-active, capture from authorized, capture from non-authorized (state-machine reject), reverse, refund.
- For each ledger-writing path, assert exactly two `LedgerEntry` saves with matching amounts and opposite directions.

`core-api/src/test/java/com/coreissuer/api/fraud/`
- One test class per check: `AmountCeilingCheckTest`, `MccBlockCheckTest`, `VelocityCheckTest`.

`core-api/src/test/java/com/coreissuer/api/reconciliation/ReconciliationServiceTest.java`
- Build in-memory `LedgerEntry` lists; assert discrepancy detection and merchant volume bucketing.

`core-api/src/test/java/com/coreissuer/api/integration/TransactionFlowIT.java`
- `@Testcontainers` with `MySQLContainer<?>`.
- Run Flyway, seed via SQL, exercise authorize → capture end-to-end and assert account balances + ledger.

### pom additions

Test dependencies in `core-api/pom.xml` already include `spring-boot-starter-test`, `testcontainers/junit-jupiter`, and `testcontainers/mysql`. Confirm before adding anything.

### Coverage

Add JaCoCo to `core-api/pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Target: 80% line coverage on `service`, `fraud`, `reconciliation` packages.

---

## Final Verification Checklist

After Tasks 0–9 (skip 10):

```powershell
.\mvnw.cmd clean compile -DskipTests
```

Expected:
- `BUILD SUCCESS` for all 4 modules.
- No `mysql-connector-java` relocation warning.
- No `LF will be replaced by CRLF` warnings on tracked files.

Code-level checks:
- [ ] `grep -r "default-secret-pepper" core-api` → no match
- [ ] `grep -r "new RuntimeException" core-api/src/main` → no match
- [ ] `grep -r "printStackTrace\|System.err" core-api/src/main` → no match
- [ ] `grep -r "MERCHANT_ACCOUNT_ID" core-api/src/main` → no match
- [ ] `grep -r 'System.setProperty("server.port"' admin-struts` → no match
- [ ] `core-api/src/main/resources/application.yml` exists
- [ ] `admin-struts/src/main/resources/application.yml` exists
- [ ] `.gitattributes` exists

---

## Commit Plan

Suggested commit boundaries (do not commit unless asked):

1. `chore: track maven wrapper, entrypoint, and lombok deps` — Task 0 + the already-modified poms.
2. `feat(config): add application.yml for core-api and admin-struts` — Tasks 1, 2, 8.
3. `chore(deps): use com.mysql:mysql-connector-j coordinates` — Task 3.
4. `fix(security): remove default pepper fallback` — Task 4.
5. `fix(txn): resolve merchant account from request` — Task 5.
6. `refactor: replace silent error swallows with SLF4J` — Task 6.
7. `refactor: domain exceptions and global handler` — Task 7.
8. `chore: add .gitattributes` — Task 9.
9. (separate branch) `test: add unit + integration coverage` — Task 10.
