# Security and Production Checklist

The packaged application is a working Java v1 and is configured for a local/internal pilot by default.

Before production use:

1. Put the service behind HTTPS/TLS and set `FOCI_COOKIE_SECURE=true`.
2. Change/delete all seeded temporary accounts and use strong unique passwords.
3. Prefer company SSO / Microsoft Entra ID for organisational deployment.
4. Run the service under a dedicated non-administrator operating-system account.
5. Restrict network access with firewall/private-network rules.
6. Back up both the database and uploaded-evidence storage regularly and test restores.
7. Add malware scanning before accepting externally sourced file attachments.
8. Define retention, document-classification and access policies for procurement evidence.
9. Move from the bundled local HSQLDB store to a currently supported server database such as PostgreSQL before multi-user/high-availability deployment.
10. Add a reverse proxy (for example nginx/IIS/Azure Application Gateway) with request-size and rate limits.
11. Add central logging/monitoring and alerting.
12. Perform security review and user-acceptance testing before handling confidential production records.

## Current security controls already present

- Passwords are hashed with PBKDF2-HMAC-SHA256 and per-user random salts.
- Seeded accounts are forced to change password.
- Role checks are enforced server-side.
- State-changing form requests require a server-side CSRF token.
- SQL uses prepared statements for user-supplied values.
- HTML output is escaped.
- Sessions use random server-side tokens with HttpOnly / SameSite cookies.
- Security headers include CSP, X-Frame-Options, nosniff and Referrer-Policy.
- Upload size is limited and file extensions are allow-listed.
- Uploaded files are served through an authenticated route, not a public static directory.
- Audit records are created for sign-in, password changes and operational create/update actions.
