#!/bin/bash

set -euo pipefail

# Script to consume one message from a Kafka topic (by partition+offset)
# inside the remote Docker Kafka container and republish it (preserving key).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST_ENV_PATH="$SCRIPT_DIR/../../../manifest.env"
ENV_PATH="$SCRIPT_DIR/../../../.env"

# Load manifest.env (required)
if [ -f "$MANIFEST_ENV_PATH" ]; then
  set -a; source "$MANIFEST_ENV_PATH"; set +a
else
  echo "Error: manifest.env file not found at $MANIFEST_ENV_PATH"
  exit 1
fi

# Load optional .env (overrides)
if [ -f "$ENV_PATH" ]; then
  set -a; source "$ENV_PATH"; set +a
fi

usage() {
  cat <<USAGE
Usage: $0 --partition N --offset N [--topic orders] [--replay-topic orders]

Example:
  $0 --partition 0 --offset 123 --topic orders --replay-topic orders

This SSHs to the host from manifest.env/.env and runs kafka-console-consumer
inside the Kafka container to fetch one message, then republishes it with
the console-producer preserving the key.
USAGE
  exit 1
}

PARTITION=""
OFFSET=""
TOPIC="orders"
REPLAY_TOPIC="orders"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --partition) PARTITION="$2"; shift 2;;
    --offset) OFFSET="$2"; shift 2;;
    --topic) TOPIC="$2"; shift 2;;
    --replay-topic) REPLAY_TOPIC="$2"; shift 2;;
    -h|--help) usage;;
    *) echo "Unknown arg: $1"; usage;;
  esac
done

if [ -z "$PARTITION" ] || [ -z "$OFFSET" ]; then
  echo "Error: --partition and --offset are required"
  usage
fi

REQUIRED_VARS=("EC2_USER" "EC2_PRIVATE_IP" "SSH_PORT" "KAFKA_CONTAINER_NAME")
for v in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!v:-}" ]; then
    echo "Error: $v is not set in manifest.env/.env"
    exit 1
  fi
done

echo "Will connect to $EC2_USER@$EC2_PRIVATE_IP:$SSH_PORT"
echo "Kafka container: $KAFKA_CONTAINER_NAME"
echo "Reading topic='$TOPIC' partition=$PARTITION offset=$OFFSET and replaying to '$REPLAY_TOPIC'"


ssh -p "$SSH_PORT" "$EC2_USER@$EC2_PRIVATE_IP" \
  "export KAFKA_CONTAINER_NAME='$KAFKA_CONTAINER_NAME' \
   && export TOPIC='$TOPIC' \
   && export PARTITION='$PARTITION' \
   && export OFFSET='$OFFSET' \
   && export REPLAY_TOPIC='$REPLAY_TOPIC' \
   && bash -s" <<'REMOTE_EOF'
set -euo pipefail

TMPFILE="/tmp/order_replay_$$.txt"

echo "Consuming one message from topic=$TOPIC partition=$PARTITION offset=$OFFSET into $TMPFILE"

# Consume one message (print key + value separated by a tab)
# Run the consumer inside the container but redirect its stdout to a file on the remote host
docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic "$TOPIC" --partition "$PARTITION" --offset "$OFFSET" --max-messages 1 \
  --formatter-property print.key=true --formatter-property key.separator=$'\t' > "$TMPFILE"

if [ ! -s "$TMPFILE" ]; then
  echo "Error: no message consumed (empty file $TMPFILE)" >&2
  exit 2
fi

# Filter out any lines that do not contain a tab (key separator) which may be
# produced by deprecation/warning text. Use a cleaned file for publishing.
CLEAN_TMP="${TMPFILE}.clean"
grep $'\t' "$TMPFILE" > "$CLEAN_TMP" || true

if [ ! -s "$CLEAN_TMP" ]; then
  echo "Error: consumed output did not contain key separator (file $TMPFILE):" >&2
  sed -n '1,200p' "$TMPFILE" >&2
  rm -f "$TMPFILE" "$CLEAN_TMP"
  exit 3
fi

rm -f "$TMPFILE"
mv "$CLEAN_TMP" "$TMPFILE"

echo "Consumed message:"
sed -n '1,200p' "$TMPFILE"
echo ""

echo "Re-publishing message to topic=$REPLAY_TOPIC (preserving key)"
# Stream the remote tmp file into the producer running inside the container
docker exec -i "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic "$REPLAY_TOPIC" --reader-property parse.key=true --reader-property key.separator=$'\t' < "$TMPFILE"

echo "Publish complete. Removing temp file $TMPFILE"
rm -f "$TMPFILE"

echo "✅ Replay finished"
REMOTE_EOF

echo "Done."
