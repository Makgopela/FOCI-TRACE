# FOCI TRACE v1 — Java Application

FOCI TRACE is a Java 21 web application for RFQ, requirement, supplier, quotation, supporting-document and technical-review traceability.

This build was created from the supplied `FOCI_TRACE(1).xlsx` working prototype. The starter database preserves the real RFQ-based records used in that workbook, including materials 144746, 144751, 144834, 502724, 717834, 554089, 746993 and 746994.

## What works

- Secure local login with role-based access: ADMIN, PROCUREMENT, ENGINEERING, VIEWER
- Forced password change for seeded accounts
- Dashboard calculated from live database records
- RFQ register and RFQ creation
- Requirement/BOQ register and requirement creation
- Supplier register and supplier creation
- Quotation capture with requirement-level pricing linkage
- Supporting-document metadata and actual file attachments (max 10 MB)
- Engineering technical-review status updates
- Dynamic traceability engine: Complete / Pending Review / Incomplete / Clarification Required
- Missing quotation, supplier-document and specification detection
- Outstanding-action calculation
- Search and status filtering
- Audit trail
- CSRF protection, PBKDF2 password hashing, secure headers and role checks
- Embedded HSQLDB database for a self-contained local installation
- Built-in rule-engine self-tests (5/5 expected)

## Requirements

- Java 21 or newer (`java -version`)
- Windows, Linux or macOS
- A modern web browser

No Maven, Gradle, Node.js or JavaScript toolchain is required to run this packaged version.

## Start on Windows

1. Extract the ZIP to a normal folder such as `C:\FOCI-TRACE`.
2. Double-click `run.bat`.
3. If the JAR has not been built yet, run `build.bat` once first.
4. Open `http://localhost:8080` in your browser.
5. Sign in with one of the temporary accounts below.
6. The application will force you to change the temporary password before normal use.

## Start on Linux/macOS

```bash
./build.sh
./run.sh
```

Then open `http://localhost:8080`.

## Temporary starter accounts

| Username | Temporary password | Role |
|---|---|---|
| `admin` | `ChangeMe!2026` | ADMIN |
| `procurement` | `Procurement!2026` | PROCUREMENT |
| `engineering` | `Engineering!2026` | ENGINEERING |
| `viewer` | `Viewer!2026` | VIEWER |

**Change every seeded password immediately.** These credentials are only for first-run setup/testing.

## Role behaviour

- **ADMIN** — full access, including users.
- **PROCUREMENT** — create RFQs, requirements, suppliers, quotations and documents.
- **ENGINEERING** — review requirements and add technical documents.
- **VIEWER** — read-only access to operational screens.

## Traceability logic

The Java rule engine calculates status in this order:

1. Required specification missing → **Clarification Required**
2. Supplier quotation missing → **Incomplete**
3. Supplier technical evidence missing → **Incomplete**
4. Technical review not completed → **Pending Review**
5. All checks satisfied → **Complete**

For example, starter material `502724` remains **Clarification Required** because `DSG-312-201` is recorded as missing.

## Where data is stored

- Database: `data/`
- Uploaded evidence: `uploads/`

These folders are intentionally empty in the distributed ZIP. They are created/populated the first time the application runs.

## Environment variables

- `FOCI_PORT` — server port, default `8080`
- `FOCI_DATA_DIR` — database directory, default `data`
- `FOCI_UPLOAD_DIR` — upload directory, default `uploads`
- `FOCI_COOKIE_SECURE` — set to `true` when the app is behind HTTPS

Example:

```bash
FOCI_PORT=8085 FOCI_COOKIE_SECURE=true ./run.sh
```

## Build

The source is under `src/`. `build.sh` / `build.bat` compile the application using Java 21, run the five rule-engine tests and create `foci-trace.jar`.

The JAR expects `lib/hsqldb.jar` to remain beside it because its manifest references that library.

## Docker

Build and run:

```bash
docker build -t foci-trace .
docker run --rm -p 8080:8080 -v foci-data:/app/data -v foci-uploads:/app/uploads foci-trace
```

## Important production note

This is a **working deployable v1 application**, suitable for demonstrating and piloting the FOCI TRACE workflow. Before putting it on the public internet or using it as a high-volume enterprise procurement platform, follow `docs/SECURITY_AND_PRODUCTION.md` — in particular HTTPS, backups, central identity/SSO, malware scanning for uploads and migration to a supported production database such as PostgreSQL.
