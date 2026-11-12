#!/usr/bin/env bash
# End-to-end check of the Phase 4 saga against the whole system running in
# e2e/docker-compose.yml: OS, Billing, Inventory and Execution talking over
# SQS/SNS (LocalStack), each with its own database.
#
#   docker compose -f e2e/docker-compose.yml up -d --build
#   bash e2e/run-e2e.sh
#
# Three scenarios, each on a new service order:
#   1. happy path           diagnosis -> budget -> approval -> stock -> payment -> execution -> delivery
#   2. payment declined     compensation: stock released, budget cancelled
#   3. insufficient stock   compensation: budget cancelled, nothing reserved
# It then checks that no message ended in a dead-letter queue.
#
# Needs curl, jq and docker. Exit code 0 only if every check passed.
set -uo pipefail

OS_URL=${OS_URL:-http://localhost:8080}
INVENTORY_URL=${INVENTORY_URL:-http://localhost:8081}
BILLING_URL=${BILLING_URL:-http://localhost:8082}
EXECUTION_URL=${EXECUTION_URL:-http://localhost:8083}
LOCALSTACK_CONTAINER=${LOCALSTACK_CONTAINER:-e2e-localstack}
WAIT_SECONDS=${WAIT_SECONDS:-60}

# Seeded users (OS) and reference data (OS catalog, Inventory supplies).
ADMIN_CPF=11144477735
CONSULTANT_CPF=22255588846
MECHANIC_CPF=33366699957
PASSWORD=workshop123
OIL_CHANGE=a1000000-0000-0000-0000-000000000001   # service, 150.00
OIL_FILTER=b2000000-0000-0000-0000-000000000005   # supply, 25.00, stock 40
BRAKE_DISC=b2000000-0000-0000-0000-000000000009   # supply, 180.00, stock 10

FAILURES=0

pass() { printf '  \033[32m✓\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$*"; FAILURES=$((FAILURES + 1)); }
step() { printf '\n\033[1m%s\033[0m\n' "$*"; }

for tool in curl jq docker; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 2; }
done

# call METHOD URL TOKEN [JSON_BODY]: prints the body; returns 1 (and says why on stderr) unless the answer is 2xx.
call() {
  local method=$1 url=$2 token=$3 body=${4:-}
  local args=(-sS -X "$method" -w '\n%{http_code}')
  [[ -n $token ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n $body ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  local out
  out=$(curl "${args[@]}" "$url") || return 1
  local code=${out##*$'\n'} response=${out%$'\n'*}
  if [[ $code != 2* ]]; then
    echo "HTTP $code $method $url: $response" >&2
    return 1
  fi
  printf '%s' "$response"
}

# eventually DESCRIPTION COMMAND...: retries the command once a second until it succeeds or WAIT_SECONDS pass.
eventually() {
  local what=$1
  shift
  local i
  for ((i = 0; i < WAIT_SECONDS; i++)); do
    if "$@" >/dev/null 2>&1; then
      pass "$what"
      return 0
    fi
    sleep 1
  done
  fail "$what (timed out after ${WAIT_SECONDS}s)"
  return 1
}

# check DESCRIPTION JSON JQ_EXPRESSION: passes when the expression is true for the JSON.
check() {
  if jq -e "$3" >/dev/null 2>&1 <<<"$2"; then pass "$1"; else fail "$1 (got: $(jq -c . <<<"$2" 2>/dev/null || echo "$2"))"; fi
}

expect_equals() {
  if [[ "$2" == "$3" ]]; then pass "$1"; else fail "$1 (expected '$3', got '$2')"; fi
}

login() {
  call POST "$OS_URL/api/v1/auth/login" "" "{\"cpf\":\"$1\",\"password\":\"$PASSWORD\"}" | jq -r .token
}

# ---- reads (all through the services' own APIs) -----------------------------

order_json()       { call GET "$OS_URL/api/v1/service-orders/$1" "$ADMIN"; }
saga_json()        { call GET "$OS_URL/api/v1/service-orders/$1/saga" "$ADMIN"; }
order_status()     { order_json "$1" | jq -r .status; }
saga_status()      { saga_json "$1" | jq -r .status; }
stock_of()         { call GET "$INVENTORY_URL/api/v1/supplies/$1" "$ADMIN" | jq -r .stockQuantity; }
reservation_json() { call GET "$INVENTORY_URL/api/v1/stock-reservations/$1" "$ADMIN"; }
budget_json()      { call GET "$BILLING_URL/api/v1/budgets/$1" "$ADMIN"; }
payment_json()     { call GET "$BILLING_URL/api/v1/payments/$1" "$ADMIN"; }
execution_json()   { call GET "$EXECUTION_URL/api/v1/executions/$1" "$MECHANIC"; }

order_is() { [[ "$(order_status "$1")" == "$2" ]]; }
saga_is()  { [[ "$(saga_status "$1")" == "$2" ]]; }

# ---- actions ----------------------------------------------------------------

# Opens an order (customer and vehicle are created or reused) with the given items.
open_order() {
  local body
  body=$(jq -n --argjson items "$1" '{
    customer: {name: "Cliente E2E", documentNumber: "52998224725", phone: "11999990000", email: "e2e@test.com"},
    vehicle: {plate: "ABC1D23", brand: "Fiat", model: "Uno", year: 2020, color: "Branco"},
    customerComments: "end-to-end run",
    items: $items}')
  call POST "$OS_URL/api/v1/service-orders/full" "$CONSULTANT" "$body" | jq -r .id
}

# Takes the order through diagnosis; the budget request then goes to Billing.
diagnose() {
  call PATCH "$OS_URL/api/v1/service-orders/$1/diagnosis/start" "$MECHANIC" >/dev/null &&
    call POST "$OS_URL/api/v1/service-orders/$1/diagnosis/complete" "$MECHANIC" \
      '{"diagnosisNotes":"Troca de óleo e filtro"}' >/dev/null
}

approve() {
  call PATCH "$OS_URL/api/v1/service-orders/$1/estimate/approve" "$CONSULTANT" "{\"paymentMethod\":\"$2\"}" >/dev/null
}

items_json() {
  printf '[{"serviceCatalogItemId":"%s","quantity":1},{"supplyId":"%s","quantity":%s}]' "$OIL_CHANGE" "$1" "$2"
}

# ---- start-up ---------------------------------------------------------------

step "Waiting for the four services"
for service in "OS|$OS_URL" "Inventory|$INVENTORY_URL" "Billing|$BILLING_URL" "Execution|$EXECUTION_URL"; do
  name=${service%%|*}
  url=${service#*|}
  ready=false
  for ((i = 0; i < 180; i++)); do
    if curl -fsS "$url/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 1
  done
  if $ready; then pass "$name is UP"; else fail "$name did not become healthy"; exit 1; fi
done

ADMIN=$(login "$ADMIN_CPF")
CONSULTANT=$(login "$CONSULTANT_CPF")
MECHANIC=$(login "$MECHANIC_CPF")
if [[ -z $ADMIN || -z $CONSULTANT || -z $MECHANIC ]]; then
  fail "could not log in with the seeded users"
  exit 1
fi
pass "logged in as admin, consultant and mechanic"

# ---- 1. happy path ----------------------------------------------------------

step "1. Happy path: oil change + 2 oil filters, paid by credit card"
STOCK_BEFORE=$(stock_of "$OIL_FILTER")
ORDER=$(open_order "$(items_json "$OIL_FILTER" 2)")
[[ -n $ORDER ]] && pass "order opened ($ORDER)" || { fail "could not open the order"; exit 1; }
diagnose "$ORDER" && pass "diagnosis completed; the budget was requested from Billing" || fail "diagnosis failed"

eventually "Billing answered: the order waits for approval" order_is "$ORDER" WAITING_APPROVAL
check "the total (150 + 2 x 25) comes from Billing" "$(order_json "$ORDER")" '.totalAmount == 200'
check "Billing holds a GENERATED budget" "$(budget_json "$ORDER")" '.status == "GENERATED" and .totalAmount == 200'

approve "$ORDER" credit-card && pass "estimate approved with credit-card" || fail "approval failed"
eventually "stock reserved, payment approved, execution queued: order IN_EXECUTION" order_is "$ORDER" IN_EXECUTION
expect_equals "Inventory took 2 filters off the shelf" "$(stock_of "$OIL_FILTER")" "$((STOCK_BEFORE - 2))"
check "reservation is RESERVED" "$(reservation_json "$ORDER")" '.status == "RESERVED"'
check "payment is APPROVED and the budget PAID" "$(jq -n --argjson p "$(payment_json "$ORDER")" --argjson b "$(budget_json "$ORDER")" '{p: $p, b: $b}')" \
  '.p.status == "APPROVED" and .b.status == "PAID"'
check "Execution queued one task" "$(execution_json "$ORDER")" '.status == "QUEUED" and (.tasks | length) == 1'

TASK=$(execution_json "$ORDER" | jq -r '.tasks[0].id')
call PATCH "$EXECUTION_URL/api/v1/executions/$ORDER/tasks/$TASK/start" "$MECHANIC" >/dev/null && pass "mechanic started the task" || fail "start failed"
call PATCH "$EXECUTION_URL/api/v1/executions/$ORDER/tasks/$TASK/finish" "$MECHANIC" >/dev/null && pass "mechanic finished the task" || fail "finish failed"
eventually "Execution told the saga: order FINISHED" order_is "$ORDER" FINISHED
eventually "Inventory consumed the reservation: saga COMPLETED" saga_is "$ORDER" COMPLETED
check "reservation is CONSUMED" "$(reservation_json "$ORDER")" '.status == "CONSUMED"'
expect_equals "the stock stays off the shelf" "$(stock_of "$OIL_FILTER")" "$((STOCK_BEFORE - 2))"
call PATCH "$OS_URL/api/v1/service-orders/$ORDER/deliver" "$CONSULTANT" >/dev/null && pass "vehicle delivered" || fail "delivery failed"
expect_equals "order is DELIVERED" "$(order_status "$ORDER")" DELIVERED

# ---- 2. payment declined ----------------------------------------------------

step "2. Payment declined (mock-decline): the stock is given back and the budget cancelled"
STOCK_BEFORE=$(stock_of "$OIL_FILTER")
ORDER=$(open_order "$(items_json "$OIL_FILTER" 2)")
diagnose "$ORDER" || fail "diagnosis failed"
eventually "order waits for approval" order_is "$ORDER" WAITING_APPROVAL
approve "$ORDER" mock-decline && pass "estimate approved with mock-decline" || fail "approval failed"

eventually "payment declined: order CANCELLED" order_is "$ORDER" CANCELLED
eventually "Inventory and Billing confirmed the undo: saga COMPENSATED" saga_is "$ORDER" COMPENSATED
expect_equals "the stock is back" "$(stock_of "$OIL_FILTER")" "$STOCK_BEFORE"
check "reservation is RELEASED" "$(reservation_json "$ORDER")" '.status == "RELEASED"'
check "payment is DECLINED and the budget CANCELLED" "$(jq -n --argjson p "$(payment_json "$ORDER")" --argjson b "$(budget_json "$ORDER")" '{p: $p, b: $b}')" \
  '.p.status == "DECLINED" and .b.status == "CANCELLED"'
check "the saga kept the reason" "$(saga_json "$ORDER")" '.failureReason | contains("mock-decline")'

# ---- 3. insufficient stock --------------------------------------------------

step "3. Insufficient stock: 11 brake discs for a stock of 10"
STOCK_BEFORE=$(stock_of "$BRAKE_DISC")
ORDER=$(open_order "$(items_json "$BRAKE_DISC" 11)")
diagnose "$ORDER" || fail "diagnosis failed"
eventually "order waits for approval" order_is "$ORDER" WAITING_APPROVAL
approve "$ORDER" credit-card && pass "estimate approved" || fail "approval failed"

eventually "reservation refused: order CANCELLED" order_is "$ORDER" CANCELLED
eventually "Billing confirmed the cancellation: saga COMPENSATED" saga_is "$ORDER" COMPENSATED
expect_equals "no stock was taken" "$(stock_of "$BRAKE_DISC")" "$STOCK_BEFORE"
if reservation_json "$ORDER" >/dev/null 2>&1; then fail "a reservation exists, none was expected"; else pass "no reservation was made"; fi
check "the budget was cancelled" "$(budget_json "$ORDER")" '.status == "CANCELLED"'
check "the saga kept the reason" "$(saga_json "$ORDER")" '.failureReason | contains("Insufficient stock")'
if payment_json "$ORDER" >/dev/null 2>&1; then fail "a payment exists, none was expected"; else pass "nothing was charged"; fi

# ---- dead-letter queues -----------------------------------------------------

step "Dead-letter queues"
for queue in billing-commands inventory-commands execution-commands os-saga-events; do
  count=$(docker exec "$LOCALSTACK_CONTAINER" awslocal sqs get-queue-attributes \
    --queue-url "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/${queue}-dlq" \
    --attribute-names ApproximateNumberOfMessages --query Attributes.ApproximateNumberOfMessages --output text 2>/dev/null)
  expect_equals "${queue}-dlq is empty" "${count:-?}" 0
done

step "Result"
if ((FAILURES == 0)); then
  printf '\033[32mAll checks passed.\033[0m\n'
else
  printf '\033[31m%d check(s) failed.\033[0m\n' "$FAILURES"
fi
exit $((FAILURES > 0))
