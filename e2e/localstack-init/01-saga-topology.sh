#!/bin/bash
# What the *-infrastructure repositories create on AWS: one command queue per
# service (each with a dead-letter queue), the saga-events topic and the OS
# queue subscribed to it with raw delivery (so the queue body is the envelope).
set -euo pipefail

MAX_RECEIVE_COUNT=3
VISIBILITY_TIMEOUT=10

queue_url() { awslocal sqs get-queue-url --queue-name "$1" --query QueueUrl --output text; }
queue_arn() { awslocal sqs get-queue-attributes --queue-url "$(queue_url "$1")" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text; }

create_queue_with_dlq() {
  local name="$1"
  awslocal sqs create-queue --queue-name "${name}-dlq" >/dev/null
  local attributes
  attributes=$(mktemp)
  cat > "$attributes" <<JSON
{
  "VisibilityTimeout": "${VISIBILITY_TIMEOUT}",
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"$(queue_arn "${name}-dlq")\",\"maxReceiveCount\":\"${MAX_RECEIVE_COUNT}\"}"
}
JSON
  awslocal sqs create-queue --queue-name "$name" --attributes "file://$attributes" >/dev/null
  rm -f "$attributes"
  echo "queue ${name} (+ ${name}-dlq)"
}

for queue in billing-commands inventory-commands execution-commands os-saga-events; do
  create_queue_with_dlq "$queue"
done

TOPIC_ARN=$(awslocal sns create-topic --name saga-events --query TopicArn --output text)
awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "$(queue_arn os-saga-events)" \
  --attributes RawMessageDelivery=true >/dev/null
echo "topic ${TOPIC_ARN} -> os-saga-events (raw)"

touch /tmp/topology-ready
