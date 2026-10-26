#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./build.sh >/tmp/foci-build.log
TESTROOT="$(mktemp -d)"
PORT=18089
FOCI_PORT=$PORT FOCI_DATA_DIR="$TESTROOT/data" FOCI_UPLOAD_DIR="$TESTROOT/uploads" nohup ./run.sh >"$TESTROOT/server.log" 2>&1 &
PID=$!
cleanup(){ kill "$PID" 2>/dev/null || true; rm -rf "$TESTROOT"; }
trap cleanup EXIT
for _ in $(seq 1 20); do curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1 && break; sleep .25; done
COOKIE="$TESTROOT/cookies.txt"
curl -fsS -c "$COOKIE" -b "$COOKIE" -X POST -d 'username=admin&password=ChangeMe%212026' "http://127.0.0.1:$PORT/login" >/dev/null
PAGE=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/change-password")
CSRF=$(echo "$PAGE" | sed -n "s/.*name='csrf' value='\([^']*\)'.*/\1/p" | head -1)
curl -fsS -b "$COOKIE" -X POST --data-urlencode "csrf=$CSRF" --data-urlencode 'password=Integration!2026' --data-urlencode 'confirm=Integration!2026' "http://127.0.0.1:$PORT/change-password" >/dev/null
BASE=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/traceability?q=502724")
echo "$BASE" | grep -q 'Clarification Required'
PAGE=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/quotations")
CSRF=$(echo "$PAGE" | sed -n "s/.*name='csrf' value='\([^']*\)'.*/\1/p" | head -1)
curl -fsS -b "$COOKIE" -X POST --data-urlencode "csrf=$CSRF" --data-urlencode 'rfq_id=RFQ-001' --data-urlencode 'supplier_id=SUP-001' --data-urlencode 'requirement_id=REQ-001' --data-urlencode 'quotation_ref=INTEGRATION-Q001' --data-urlencode 'status=Received' --data-urlencode 'quoted=true' --data-urlencode 'unit_price=25.50' --data-urlencode 'currency=ZAR' --data-urlencode 'lead_time_days=14' --data-urlencode 'evidence_basis=Integration test' "http://127.0.0.1:$PORT/quotations/create" >/dev/null
PAGE=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/documents")
CSRF=$(echo "$PAGE" | sed -n "s/.*name='csrf' value='\([^']*\)'.*/\1/p" | head -1)
echo 'Integration evidence' > "$TESTROOT/evidence.txt"
curl -fsS -b "$COOKIE" -X POST -F "csrf=$CSRF" -F 'requirement_id=REQ-001' -F 'supplier_id=SUP-001' -F 'quotation_id=QUO-007' -F 'doc_type=Product Datasheet / Certificate of Conformance' -F 'reference_name=Integration Evidence' -F 'version=Rev 0' -F 'status=Received' -F 'reviewer=Integration Test' -F 'source_notes=Automated integration test' -F "attachment=@$TESTROOT/evidence.txt;type=text/plain" "http://127.0.0.1:$PORT/documents/upload" >/dev/null
PAGE=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/requirements")
CSRF=$(echo "$PAGE" | sed -n "s/.*name='csrf' value='\([^']*\)'.*/\1/p" | head -1)
curl -fsS -b "$COOKIE" -X POST --data-urlencode "csrf=$CSRF" --data-urlencode 'id=REQ-001' --data-urlencode 'review=Reviewed' "http://127.0.0.1:$PORT/requirements/review" >/dev/null
FINAL=$(curl -fsS -b "$COOKIE" "http://127.0.0.1:$PORT/traceability?q=REQ-001")
echo "$FINAL" | grep -q 'Priced'
echo "$FINAL" | grep -q 'Received'
echo "$FINAL" | grep -q 'Reviewed'
echo "$FINAL" | grep -q 'Complete'
echo 'FOCI TRACE integration test: PASS'
