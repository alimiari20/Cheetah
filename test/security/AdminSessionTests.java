package security;

public final class AdminSessionTests {
    public static void main(String[] args) {
        AdminSession session = new AdminSession();

        check(!session.signIn(false, () -> {
            throw new AssertionError("Incorrect password started verification");
        }));
        check(!session.signIn(true, () -> false));
        check(!session.isAuthenticated());
        try {
            session.signIn(true, () -> {
                throw new IllegalStateException("Code verification failed");
            });
            throw new AssertionError("Expected verification failure");
        } catch (IllegalStateException expected) {
            check(!session.isAuthenticated());
        }
        check(!session.signIn(true, () -> {
            session.logout();
            return true;
        }));
        check(!session.isAuthenticated());
        check(session.signIn(true, () -> {
            check(!session.isAuthenticated());
            return true;
        }));
        check(session.isAuthenticated());
        session.logout();
        try {
            session.requireAuthenticated();
            throw new AssertionError("Protected action allowed after logout");
        } catch (SecurityException expected) {
            check(!session.isAuthenticated());
        }
        System.out.println("AdminSessionTests passed");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Administrator session did not enforce both sign-in steps");
    }
}
