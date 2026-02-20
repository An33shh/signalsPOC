#!/bin/bash
# Run the full application stack (backend + frontend) in local development mode

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cleanup() {
    echo ""
    echo "Shutting down..."
    [ -n "$MONITOR_PID" ] && kill "$MONITOR_PID" 2>/dev/null
    [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
    [ -n "$OLLAMA_PID" ] && kill "$OLLAMA_PID" 2>/dev/null
    wait 2>/dev/null
    echo "Done."
    exit 0
}

# Prints CPU% and RSS (MB) for a PID, or "---" if not running
proc_stats() {
    local pid=$1
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        ps -p "$pid" -o %cpu=,rss= 2>/dev/null | awk '{printf "cpu=%.1f%% mem=%dMB", $1, $2/1024}'
    else
        printf "not running"
    fi
}

monitor_loop() {
    local interval=30
    while true; do
        sleep "$interval"
        local ts; ts=$(date '+%H:%M:%S')
        local be_stats; be_stats=$(proc_stats "$BACKEND_PID")
        local fe_stats; fe_stats=$(proc_stats "$FRONTEND_PID")
        local ol_status
        if curl -sf --max-time 1 http://localhost:11434/api/tags &>/dev/null; then
            ol_status="up $(proc_stats "$OLLAMA_PID")"
        else
            ol_status="down"
        fi
        echo ""
        echo "[$ts] metrics | backend: $be_stats | frontend: $fe_stats | ollama: $ol_status"
    done
}

trap cleanup INT TERM

echo "================================================"
echo "  Signals POC - Full Stack (Backend + Frontend)"
echo "================================================"
echo ""
echo "Endpoints (after startup):"
echo "  - Frontend:     http://localhost:5173"
echo "  - API:          http://localhost:8080/api/v1"
echo "  - Swagger UI:   http://localhost:8080/swagger-ui.html"
echo "  - H2 Console:   http://localhost:8080/h2-console"
echo "  - Health:       http://localhost:8080/actuator/health"
echo "  - Ollama:       http://localhost:11434"
echo ""
echo "Default credentials: admin/admin123 or user/user123"
echo "================================================"
echo ""

# --- Ollama ---
if command -v ollama &>/dev/null; then
    if ! curl -sf http://localhost:11434/api/tags &>/dev/null; then
        echo "[ollama] Starting Ollama server..."
        ollama serve &>/dev/null &
        OLLAMA_PID=$!
        echo "[ollama] Waiting for Ollama to be ready..."
        for i in $(seq 1 15); do
            curl -sf http://localhost:11434/api/tags &>/dev/null && break
            sleep 1
        done
        echo "[ollama] Ready."
    else
        echo "[ollama] Already running."
        OLLAMA_PID=""
    fi

    # Build the product-tuned signals-poc model from Modelfile if not already present
    if ollama list 2>/dev/null | grep -q "signals-poc"; then
        echo "[ollama] signals-poc model already exists."
    else
        echo "[ollama] Building signals-poc model from Modelfile (pulling llama3.1:8b if needed)..."
        ollama create signals-poc -f "$ROOT_DIR/ai/Modelfile" && \
            echo "[ollama] signals-poc model ready." || \
            echo "[ollama] WARNING: signals-poc model build failed — falling back to llama3.1:8b"
    fi
else
    echo "[ollama] WARNING: ollama not found — AI features will be unavailable."
    OLLAMA_PID=""
fi
echo ""

# --- Backend ---
echo "[backend] Starting Spring Boot (local profile)..."
if [ -f "$SCRIPT_DIR/mvnw" ]; then
    MVN="$SCRIPT_DIR/mvnw"
elif command -v mvn &>/dev/null; then
    MVN="mvn"
else
    echo "ERROR: Maven not found. Install with: brew install maven"
    exit 1
fi

# Lombok annotation processing requires JDK < 21; prefer JDK 17/19 if available
for candidate in \
    "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" \
    "/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
    "/opt/homebrew/opt/openjdk@19/libexec/openjdk.jdk/Contents/Home"; do
    if [ -d "$candidate" ]; then
        export JAVA_HOME="$candidate"
        echo "[backend] Using JAVA_HOME=$JAVA_HOME"
        break
    fi
done

JAVA_HOME="$JAVA_HOME" $MVN -f "$SCRIPT_DIR/pom.xml" spring-boot:run -Dspring-boot.run.profiles=local &
BACKEND_PID=$!

# --- Frontend ---
echo "[frontend] Installing dependencies..."
if ! command -v npm &>/dev/null; then
    echo "ERROR: npm not found. Install Node.js 18+."
    kill "$BACKEND_PID" 2>/dev/null
    exit 1
fi

npm --prefix "$ROOT_DIR/frontend" install && \
echo "[frontend] Starting Vite dev server..." && \
npm --prefix "$ROOT_DIR/frontend" run dev &
FRONTEND_PID=$!

echo ""
echo "Press Ctrl+C to stop all servers."
echo "(metrics printed every 30s)"

monitor_loop &
MONITOR_PID=$!

wait
