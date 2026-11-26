package security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Audit trail for the desktop admin flow. All persisted fields are typed metadata. */
public final class AdminAudit {
    public enum Type { LOGIN_CHECK, REAUTH_CHECK, SIGN_IN, BLOCKED, ERROR }
    public enum Method { PASSWORD, CODE, BOTH }
    public record Event(Instant time, Type type, Method method, boolean success) {}
    public record Snapshot(List<Event> events, boolean incomplete) {}
    public record Statistics(long successes, long failures, long failuresLastDay,
                             Instant lastSuccess, Instant lastFailure) {
        public long total() { return successes + failures; }
        public double successRate() { return total() == 0 ? 0 : (double) successes / total(); }
    }

    private final SecurityEventRepository repository;
    private final Clock clock;
    private volatile boolean writeFailed;

    public AdminAudit() { this(new SecurityEventRepository(), Clock.systemUTC()); }

    AdminAudit(SecurityEventRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean verify(AdminAuthService service, AdminAuthService.Factor factor, char[] entered,
                          boolean login) throws IOException {
        Method method = method(factor);
        boolean accepted;
        try {
            accepted = service.verify(factor, entered);
        } catch (IOException | RuntimeException ex) {
            record(Type.ERROR, method, false);
            throw ex;
        }
        record(login ? Type.LOGIN_CHECK : Type.REAUTH_CHECK, method, accepted);
        return accepted;
    }

    public void blocked(AdminAuthService.Factor factor) { record(Type.BLOCKED, method(factor), false); }
    public void signedIn() { record(Type.SIGN_IN, Method.BOTH, true); }

    public Snapshot read(AdminSession session) throws IOException {
        session.requireAuthenticated();
        var stored = repository.read();
        return new Snapshot(stored.events(), writeFailed || stored.incomplete());
    }

    private void record(Type type, Method method, boolean success) {
        try {
            repository.append(new Event(clock.instant(), type, method, success));
        } catch (IOException | SecurityException ex) {
            // Audit storage must not change the result of credential verification.
            writeFailed = true;
        }
    }

    private static Method method(AdminAuthService.Factor factor) {
        return factor == AdminAuthService.Factor.PASSWORD ? Method.PASSWORD : Method.CODE;
    }

    public static Statistics statistics(List<Event> events, Instant now) {
        long successes = 0, failures = 0, recent = 0;
        Instant lastSuccess = null, lastFailure = null;
        Instant cutoff = now.minus(Duration.ofHours(24));
        for (Event event : events) {
            if (event.type() != Type.LOGIN_CHECK) continue;
            if (event.success()) {
                successes++;
                if (lastSuccess == null || event.time().isAfter(lastSuccess)) lastSuccess = event.time();
            } else {
                failures++;
                if (lastFailure == null || event.time().isAfter(lastFailure)) lastFailure = event.time();
                if (!event.time().isBefore(cutoff) && !event.time().isAfter(now)) recent++;
            }
        }
        return new Statistics(successes, failures, recent, lastSuccess, lastFailure);
    }

    public static List<Event> chronological(List<Event> events) {
        return events.stream().sorted(Comparator.comparing(Event::time)).toList();
    }
}
