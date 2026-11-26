package security;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static security.AdminAudit.Type.*;
import static security.AdminAudit.Method.*;

public final class AdminAuditTests {
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("cheetah-audit-");
        try {
            var file = directory.resolve("audit.tsv");
            var repository = new SecurityEventRepository(file);
            var now = Instant.parse("2026-11-26T12:00:00Z");
            var audit = new AdminAudit(repository, Clock.fixed(now, ZoneOffset.UTC));
            var session = new AdminSession();
            expectDenied(audit, session);
            session.signIn(true, () -> true);
            check(audit.read(session).events().isEmpty(), "New installation has no invented events");
            var service = new AdminAuthService(new AdminCredentialRepository(directory));
            char[] password = "test-password-only".toCharArray();
            char[] code = "test-auth-code-only".toCharArray();
            service.provision(password, code);
            check(!audit.verify(service, AdminAuthService.Factor.PASSWORD, "incorrect".toCharArray(), true), "Bad password");
            check(audit.verify(service, AdminAuthService.Factor.PASSWORD, password, true), "Accepted password");
            check(audit.verify(service, AdminAuthService.Factor.CODE, code, true), "Accepted code");
            check(audit.verify(service, AdminAuthService.Factor.CODE, code, false), "Account re-verification");
            audit.signedIn();
            audit.blocked(AdminAuthService.Factor.CODE);
            repository.append(new AdminAudit.Event(now.minusSeconds(90_000), LOGIN_CHECK, CODE, false));
            Files.writeString(file, "incomplete-record", java.nio.file.StandardOpenOption.APPEND);
            audit.blocked(AdminAuthService.Factor.PASSWORD);

            var reopened = new AdminAudit(new SecurityEventRepository(file), Clock.systemUTC());
            var snapshot = reopened.read(session);
            check(snapshot.events().size() == 8 && snapshot.incomplete(), "Persistent events survive a malformed record");
            var stats = AdminAudit.statistics(snapshot.events(), now);
            check(stats.total() == 4 && stats.successes() == 2 && stats.failures() == 2
                    && stats.failuresLastDay() == 1 && stats.successRate() == .5
                    && now.equals(stats.lastSuccess()) && now.equals(stats.lastFailure()), "Accurate credential-check statistics");
            check(snapshot.events().get(0).time().isBefore(now), "Events are chronological");
            String persisted = Files.readString(file);
            check(!persisted.contains("test-password") && !persisted.contains("test-auth-code")
                    && !persisted.contains("incorrect"), "No entered value is written to the log");
            session.logout();
            expectDenied(reopened, session);
            System.out.println("Admin audit tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void expectDenied(AdminAudit audit, AdminSession session) throws Exception {
        try {
            audit.read(session);
            throw new AssertionError("Unauthenticated dashboard access");
        } catch (SecurityException expected) {
            // The guard applies before reading records.
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
