#!/bin/bash

###############################################################################
# Kafka Setup - SSH + Docker + Topics Creation
# 
# Configuration loaded from .env file:
#   EC2_USER, EC2_PRIVATE_IP, SSH_PORT
#   KAFKA_CLUSTER_ID, KAFKA_CONTAINER_NAME, KAFKA_DOCKER_IMAGE
#   Topic configurations (partitions, retention)
#
# Runtime expectation:
#   Services resolve Kafka as ${EC2_PRIVATE_IP}:${kafka.port}, where kafka.port
#   comes from Config/application.yaml (default 9092).
#
###############################################################################

set -euo pipefail

# Get absolute path of the script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST_ENV_PATH="$SCRIPT_DIR/../../../manifest.env"
ENV_PATH="$SCRIPT_DIR/../../../.env"

# Load manifest.env (required)
if [ -f "$MANIFEST_ENV_PATH" ]; then
  set -a  # Export all variables
  source "$MANIFEST_ENV_PATH"
  set +a  # Stop exporting
else
  echo "Error: manifest.env file not found at $MANIFEST_ENV_PATH"
  exit 1
fi

# Load .env (optional, overrides EC2_PRIVATE_IP if present)
if [ -f "$ENV_PATH" ]; then
  set -a  # Export all variables
  source "$ENV_PATH"
  set +a  # Stop exporting
fi

# Validate required variables from .env
REQUIRED_VARS=(
  "EC2_USER"
  "EC2_PRIVATE_IP"
  "SSH_PORT"
  "KAFKA_CLUSTER_ID"
  "KAFKA_CONTAINER_NAME"
  "KAFKA_DOCKER_IMAGE"
  "KAFKA_ORDERS_PARTITIONS"
  "KAFKA_ORDERS_RETENTION_MS"
  "KAFKA_TRADE_EVENTS_PARTITIONS"
  "KAFKA_TRADE_EVENTS_RETENTION_MS"
  "KAFKA_MARKET_DATA_PARTITIONS"
  "KAFKA_MARKET_DATA_RETENTION_MS"
  "KAFKA_DLT_RETENTION_MS"
)

for var in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "Error: $var is not set in $ENV_PATH!"
    exit 1
  fi
done

echo "=========================================="
echo "Kafka Setup - SSH + Docker + Topics"
echo "=========================================="
echo "Target: $EC2_USER@$EC2_PRIVATE_IP:$SSH_PORT"
echo "Container: $KAFKA_CONTAINER_NAME"
echo "Cluster ID: $KAFKA_CLUSTER_ID"
echo ""

echo "Connecting via SSH..."
ssh -p "$SSH_PORT" "$EC2_USER@$EC2_PRIVATE_IP" \
  "export EC2_PRIVATE_IP='$EC2_PRIVATE_IP' \
   && export CLUSTER_ID='$KAFKA_CLUSTER_ID' \
   && export KAFKA_CONTAINER_NAME='$KAFKA_CONTAINER_NAME' \
   && export KAFKA_DOCKER_IMAGE='$KAFKA_DOCKER_IMAGE' \
   && export KAFKA_ORDERS_PARTITIONS='$KAFKA_ORDERS_PARTITIONS' \
   && export KAFKA_ORDERS_RETENTION_MS='$KAFKA_ORDERS_RETENTION_MS' \
   && export KAFKA_TRADE_EVENTS_PARTITIONS='$KAFKA_TRADE_EVENTS_PARTITIONS' \
   && export KAFKA_TRADE_EVENTS_RETENTION_MS='$KAFKA_TRADE_EVENTS_RETENTION_MS' \
   && export KAFKA_MARKET_DATA_PARTITIONS='$KAFKA_MARKET_DATA_PARTITIONS' \
   && export KAFKA_MARKET_DATA_RETENTION_MS='$KAFKA_MARKET_DATA_RETENTION_MS' \
   && export KAFKA_DLT_RETENTION_MS='$KAFKA_DLT_RETENTION_MS' \
   && bash" << 'REMOTE_SCRIPT_EOF'

echo "=========================================="
echo "Starting Kafka Docker Container"
echo "=========================================="
echo "Container: $KAFKA_CONTAINER_NAME"
echo "Image: $KAFKA_DOCKER_IMAGE"
echo "Private IP: $EC2_PRIVATE_IP"
echo ""

# Stop and remove existing Kafka container if it exists
if docker ps -a --format '{{.Names}}' | grep -q "^$KAFKA_CONTAINER_NAME$"; then
  echo "Removing existing $KAFKA_CONTAINER_NAME container..."
  docker stop "$KAFKA_CONTAINER_NAME" 2>/dev/null || true
  docker rm "$KAFKA_CONTAINER_NAME" 2>/dev/null || true
  sleep 2
fi

# Start Kafka Docker container
echo "Starting Kafka Docker container..."
docker run -d --name "$KAFKA_CONTAINER_NAME" \
  -p "$EC2_PRIVATE_IP:9092:9092" \
  -p "$EC2_PRIVATE_IP:29092:29092" \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,EXTERNAL://:29092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://$EC2_PRIVATE_IP:9092,EXTERNAL://$EC2_PRIVATE_IP:29092 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@127.0.0.1:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE="false" \
  -e CLUSTER_ID="$CLUSTER_ID" \
  "$KAFKA_DOCKER_IMAGE"

echo "✓ Kafka container started"
sleep 3
echo ""

# Wait for Kafka to be ready
echo "Waiting for Kafka broker to be ready..."
for i in {1..30}; do
  if docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 &>/dev/null 2>&1; then
    echo "✓ Kafka broker is ready"
    break
  fi
  echo -n "."
  sleep 1
done
echo ""

# Check existing topics
echo "=========================================="
echo "Checking Existing Topics"
echo "=========================================="
EXISTING_TOPICS=$(docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")
if [[ -n "$EXISTING_TOPICS" ]]; then
  echo "$EXISTING_TOPICS"
else
  echo "(No topics found)"
fi
echo ""

# Create topics
echo "=========================================="
echo "Creating Kafka Topics"
echo "=========================================="
echo ""

# Main topics
echo "Creating: orders ($KAFKA_ORDERS_PARTITIONS partitions, retention: $KAFKA_ORDERS_RETENTION_MS ms)"
if ! echo "$EXISTING_TOPICS" | grep -q "^orders$"; then
  docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic orders --partitions $KAFKA_ORDERS_PARTITIONS --replication-factor 1 --config retention.ms=$KAFKA_ORDERS_RETENTION_MS 2>/dev/null || true
  echo "  ✓ Created"
else
  echo "  ✓ Already exists"
fi

echo "Creating: trade-events ($KAFKA_TRADE_EVENTS_PARTITIONS partitions, retention: $KAFKA_TRADE_EVENTS_RETENTION_MS ms)"
if ! echo "$EXISTING_TOPICS" | grep -q "^trade-events$"; then
  docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic trade-events --partitions $KAFKA_TRADE_EVENTS_PARTITIONS --replication-factor 1 --config retention.ms=$KAFKA_TRADE_EVENTS_RETENTION_MS 2>/dev/null || true
  echo "  ✓ Created"
else
  echo "  ✓ Already exists"
fi

echo "Creating: market-data ($KAFKA_MARKET_DATA_PARTITIONS partitions, retention: $KAFKA_MARKET_DATA_RETENTION_MS ms)"
if ! echo "$EXISTING_TOPICS" | grep -q "^market-data$"; then
  docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic market-data --partitions $KAFKA_MARKET_DATA_PARTITIONS --replication-factor 1 --config retention.ms=$KAFKA_MARKET_DATA_RETENTION_MS 2>/dev/null || true
  echo "  ✓ Created"
else
  echo "  ✓ Already exists"
fi
echo ""

# Dead-letter topics
echo "Creating Dead-Letter Topics (DLT)..."
for DLT_TOPIC in orders.DLT trade-events.DLT market-data.DLT; do
  if ! echo "$EXISTING_TOPICS" | grep -q "^$DLT_TOPIC$"; then
    DLT_PARTITIONS=$KAFKA_ORDERS_PARTITIONS
    if [[ "$DLT_TOPIC" == "trade-events.DLT" ]]; then
      DLT_PARTITIONS=$KAFKA_TRADE_EVENTS_PARTITIONS
    elif [[ "$DLT_TOPIC" == "market-data.DLT" ]]; then
      DLT_PARTITIONS=$KAFKA_MARKET_DATA_PARTITIONS
    fi
    echo "Creating: $DLT_TOPIC ($DLT_PARTITIONS partitions, retention: $KAFKA_DLT_RETENTION_MS ms)"
    docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic "$DLT_TOPIC" --partitions $DLT_PARTITIONS --replication-factor 1 --config retention.ms=$KAFKA_DLT_RETENTION_MS 2>/dev/null || true
    echo "  ✓ Created"
  else
    echo "✓ $DLT_TOPIC (already exists)"
  fi
done
echo ""

# Verify
echo "=========================================="
echo "Final Topic Summary"
echo "=========================================="
docker exec "$KAFKA_CONTAINER_NAME" /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe
echo ""

# Verify all 6 expected topics exist
echo "=========================================="
echo "Verifying All 6 Kafka Topics"
echo "=========================================="

EXPECTED_TOPICS=(
  "orders"
  "trade-events"
  "market-data"
  "orders.DLT"
  "trade-events.DLT"
  "market-data.DLT"
)

ALL_TOPICS_CREATED=true

for TOPIC in "${EXPECTED_TOPICS[@]}"; do
  echo "Checking topic: $TOPIC"

  if docker exec "$KAFKA_CONTAINER_NAME" \
      /opt/kafka/bin/kafka-topics.sh \
      --describe \
      --topic "$TOPIC" \
      --bootstrap-server localhost:9092 \
      >/dev/null 2>&1; then

    echo "  ✓ $TOPIC exists"

  else

    echo "  ✗ $TOPIC DOES NOT EXIST"
    ALL_TOPICS_CREATED=false

  fi
done

echo ""

if [[ "$ALL_TOPICS_CREATED" != "true" ]]; then
  echo "=========================================="
  echo "✗ Kafka Topic Verification FAILED"
  echo "=========================================="
  exit 1
fi

echo "=========================================="
echo "✓ All 6 Topics Verified Successfully"
echo "=========================================="
echo ""

REMOTE_SCRIPT_EOF

echo "=========================================="
echo "✓ All done!"
echo "=========================================="
