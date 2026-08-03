package com.masiton.member.application.port.out;

/**
 * Coordinates member-authentication request limits without exposing Redis details to the application layer.
 *
 * <p>All values supplied to this port must already be normalized by the caller.</p>
 */
public interface MemberRateLimitStore {

    record VerificationAttemptResult(boolean allowed, long retryAfterSeconds) {
        public static VerificationAttemptResult permit() {
            return new VerificationAttemptResult(true, 0);
        }

        public static VerificationAttemptResult reject(long retryAfterSeconds) {
            return new VerificationAttemptResult(false, retryAfterSeconds);
        }
    }

    /**
     * Acquires the email cooldown and daily quota used for verification-email resends.
     */
    boolean tryAcquireEmailRequest(String normalizedEmail);

    /**
     * Acquires the email limits and the shared registration/password-reset source quota as one operation.
     */
    boolean tryAcquireAccountActionRequest(String normalizedEmail, String source);

    /**
     * Returns whether an email/source login attempt is currently blocked by any login-failure quota.
     */
    boolean isLoginBlocked(String normalizedEmail, String source);

    /**
     * Records one email-verification code submission attempt for a trusted request source.
     */
    VerificationAttemptResult acquireEmailVerificationAttempt(String source);

    /**
     * Records one failed login against its email-and-source, email, and source windows atomically.
     */
    void recordLoginFailure(String normalizedEmail, String source);
}
