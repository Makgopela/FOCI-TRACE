# FOCI TRACE Architecture

## Runtime

Browser → Java 21 HTTP server → service/rule logic → JDBC → HSQLDB file database

Uploaded evidence files are stored in the configured upload directory and are only downloadable to authenticated users.

## Data model

- `users`
- `rfqs`
- `requirements`
- `suppliers`
- `quotations`
- `quotation_items`
- `documents`
- `audit_log`

`quotation_items` links a quotation to a specific requirement so that one RFQ can contain multiple requirements and one supplier quote can be evaluated item by item.

## Core traceability flow

RFQ → Requirement → Supplier/Quotation Item → Required Specification/Documents → Technical Review → Calculated Overall Status → Outstanding Action

## Source layout

- `FociTraceApplication.java` — HTTP routing, authentication enforcement and application actions
- `Database.java` — schema, starter data, SQL access and dashboard/traceability queries
- `TraceabilityEngine.java` — central status/business rules
- `SelfTest.java` — executable rule-engine tests
- `Passwords.java` — PBKDF2 password hashing
- `SessionManager.java` — server-side sessions and CSRF tokens
- `Multipart.java` — controlled multipart upload parser
- `Html.java` — server-rendered UI
- `Web.java` — HTTP/query/escaping/security helpers
