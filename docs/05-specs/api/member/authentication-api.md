---
id: API-MEMBER-AUTH-001
title: Member account and authentication API
status: draft
related_requirements:
  - FR-MEMBER-001
  - FR-MEMBER-002
  - FR-MEMBER-003
  - FR-MEMBER-004
  - FR-MEMBER-005
  - FR-AUTH-001
  - FR-AUTH-002
  - FR-AUTH-003
related_documents:
  - ../../../07-adr/security/auth-002-member-jwt-refresh-token.md
  - ../common/error-contract.md
---

# Member account and authentication API

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/registrations` | Create a pending account and send an email-verification token |
| POST | `/api/auth/email-verifications` | Consume an email-verification token |
| POST | `/api/auth/email-verifications/resend` | Re-send verification without exposing account existence |
| POST | `/api/auth/password-resets/requests` | Request a reset without exposing account existence |
| POST | `/api/auth/password-resets/confirmations` | Consume a password-reset token and invalidate sessions |
| POST | `/api/auth/tokens` | Sign in and issue access plus refresh tokens |
| POST | `/api/auth/tokens/refresh` | Rotate the HttpOnly refresh cookie |
| DELETE | `/api/auth/tokens` | Revoke all member refresh sessions |
| GET | `/api/me` | Read the current authenticated member |
| DELETE | `/api/me` | Request account deletion and immediately revoke the current access-token sid |

## Authentication delivery

Successful sign-in and refresh return `accessToken`, `tokenType: Bearer`, and
`expiresInSeconds`. The browser keeps the access token in memory only. The raw
refresh token is returned only as `__Secure-masiton-member-refresh`, an HttpOnly,
Secure, SameSite=Strict cookie scoped to `/api/auth/tokens`.

All credential-changing payloads use `email` and `password`; password length is
12 to 128 characters. Action-token payloads use a single `token` string. Tokens
are stored as SHA-256 hashes, are single-use, expire after one hour, and replacing
one revokes the previous token for the same member and purpose.
The delivery adapter builds links from `MEMBER_PUBLIC_BASE_URL`; production must
set it to the public HTTPS web origin.

## Enumeration and failure rules

Resend-verification and password-reset-request always return `202 Accepted` for a
valid email-shaped request, whether or not an eligible account exists. Credential,
refresh-token, and revoked-session failures return the common `401
AUTHENTICATION_REQUIRED` body. Redis or mail-delivery failures do not issue a
token or claim success.
