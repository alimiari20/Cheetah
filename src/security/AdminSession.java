package security;

import java.util.function.BooleanSupplier;

public final class AdminSession {
    private boolean authenticated;
    private long signInGeneration;

    public boolean signIn(boolean passwordAccepted, BooleanSupplier verifyCode) {
        logout();
        long generation = signInGeneration;
        if (!passwordAccepted || !verifyCode.getAsBoolean() || generation != signInGeneration) {
            return false;
        }
        authenticated = true;
        return true;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void requireAuthenticated() {
        if (!authenticated) {
            throw new SecurityException("Administrator authentication is required.");
        }
    }

    public void logout() {
        signInGeneration++;
        authenticated = false;
    }
}
