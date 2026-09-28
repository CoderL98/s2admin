#!/usr/bin/env bash
# s2admin 本地开发脚本
# 用法: ./dev.sh start|stop|restart|status|logs [all|frontend|backend]
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.dev"
FRONTEND_DIR="$ROOT/frontend"
BACKEND_DIR="$ROOT/backend"

FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"

PROXY_SH="${PROXY_SH:-$HOME/proxy.sh}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"

mkdir -p "$RUN_DIR"

# ---------- 颜色 ----------
if [[ -t 1 ]]; then
	C_RESET=$'\033[0m'
	C_BOLD=$'\033[1m'
	C_DIM=$'\033[2m'
	C_RED=$'\033[31m'
	C_GREEN=$'\033[32m'
	C_YELLOW=$'\033[33m'
	C_CYAN=$'\033[36m'
else
	C_RESET= C_BOLD= C_DIM= C_RED= C_GREEN= C_YELLOW= C_CYAN=
fi

info()  { printf '%s\n' "${C_CYAN}==>${C_RESET} $*"; }
ok()    { printf '%s\n' "${C_GREEN}==>${C_RESET} $*"; }
warn()  { printf '%s\n' "${C_YELLOW}==>${C_RESET} $*"; }
err()   { printf '%s\n' "${C_RED}==>${C_RESET} $*" >&2; }
die()   { err "$*"; exit 1; }

usage() {
	cat <<EOF
${C_BOLD}s2admin 开发脚本${C_RESET}

${C_BOLD}用法${C_RESET}
  ./dev.sh <命令> [目标]

${C_BOLD}命令${C_RESET}
  start      启动服务(已在跑则跳过)
  stop       按端口停止服务
  restart    先停再启
  status     查看监听端口、PID、健康状态
  logs       跟踪日志(Ctrl+C 退出)
  help       显示本帮助

${C_BOLD}目标${C_RESET}(默认 all)
  all        前端 + 后端
  frontend   仅前端  (别名: fe, front, ui)
  backend    仅后端  (别名: be, api)

${C_BOLD}示例${C_RESET}
  ./dev.sh start
  ./dev.sh stop backend
  ./dev.sh restart frontend
  ./dev.sh status
  ./dev.sh logs backend

${C_BOLD}环境变量${C_RESET}
  FRONTEND_PORT   前端端口,默认 5173
  BACKEND_PORT    后端端口,默认 8080
  FRONTEND_HOST   前端绑定地址,默认 127.0.0.1
  WAIT_SECONDS    启动等待秒数,默认 90
  PROXY_SH        前端代理脚本,默认 ~/proxy.sh
EOF
}

# ---------- 目标解析 ----------
normalize_target() {
	local t="${1:-all}"
	t="$(printf '%s' "$t" | tr '[:upper:]' '[:lower:]')"
	case "$t" in
		all|both|"") echo all ;;
		frontend|front|fe|ui|web) echo frontend ;;
		backend|back|be|api|java) echo backend ;;
		*) die "未知目标: $1 (可用 all|frontend|backend)" ;;
	esac
}

want_frontend() {
	[[ "$1" == all || "$1" == frontend ]]
}

want_backend() {
	[[ "$1" == all || "$1" == backend ]]
}

# ---------- 端口 / 进程 ----------
pids_on_port() {
	local port="$1"
	local pids=""
	if command -v lsof >/dev/null 2>&1; then
		pids="$(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
	fi
	if [[ -z "$pids" ]] && command -v fuser >/dev/null 2>&1; then
		pids="$(fuser "${port}/tcp" 2>/dev/null | tr -s ' ' '\n' || true)"
	fi
	if [[ -z "$pids" ]] && command -v ss >/dev/null 2>&1; then
		pids="$(ss -lptn "sport = :${port}" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' || true)"
	fi
	printf '%s\n' "$pids" | awk 'NF && !seen[$1]++ { print $1 }'
}

port_in_use() {
	local pids
	pids="$(pids_on_port "$1")"
	[[ -n "$pids" ]]
}

proc_name() {
	local pid="$1"
	if [[ -r "/proc/$pid/comm" ]]; then
		tr -d '\0' < "/proc/$pid/comm"
	else
		ps -p "$pid" -o comm= 2>/dev/null || echo "?"
	fi
}

kill_pid_tree() {
	local pid="$1"
	[[ -n "$pid" && "$pid" =~ ^[0-9]+$ ]] || return 0
	kill -0 "$pid" 2>/dev/null || return 0
	local children
	children="$(pgrep -P "$pid" 2>/dev/null || true)"
	local child
	for child in $children; do
		kill_pid_tree "$child"
	done
	kill -TERM "$pid" 2>/dev/null || true
}

wait_pid_gone() {
	local pid="$1"
	local i
	for i in $(seq 1 20); do
		kill -0 "$pid" 2>/dev/null || return 0
		sleep 0.15
	done
	kill -KILL "$pid" 2>/dev/null || true
}

stop_port() {
	local name="$1"
	local port="$2"
	local pids
	pids="$(pids_on_port "$port")"
	if [[ -z "$pids" ]]; then
		info "$name 未在端口 $port 监听"
		return 0
	fi
	info "停止 $name (端口 $port, PID: $(echo "$pids" | tr '\n' ' '))"
	local pid
	for pid in $pids; do
		# 先杀监听进程的父进程组(pnpm / mvn),避免留下僵尸启动器
		local ppid
		ppid="$(ps -o ppid= -p "$pid" 2>/dev/null | tr -d ' ' || true)"
		if [[ -n "$ppid" && "$ppid" != 1 ]]; then
			local pcomm
			pcomm="$(proc_name "$ppid")"
			case "$pcomm" in
				pnpm|npm|node|mvn|java|bash|sh) kill_pid_tree "$ppid" ;;
			esac
		fi
		kill_pid_tree "$pid"
	done
	for pid in $pids; do
		wait_pid_gone "$pid"
	done
	# 仍占用则强杀
	pids="$(pids_on_port "$port")"
	if [[ -n "$pids" ]]; then
		warn "$name 仍占用 $port,发送 SIGKILL"
		for pid in $pids; do
			kill -KILL "$pid" 2>/dev/null || true
		done
		sleep 0.2
	fi
	if port_in_use "$port"; then
		die "$name 未能释放端口 $port"
	fi
	ok "$name 已停止"
}

wait_port() {
	local name="$1"
	local port="$2"
	local i
	for i in $(seq 1 "$WAIT_SECONDS"); do
		if port_in_use "$port"; then
			return 0
		fi
		sleep 1
	done
	err "$name 在 ${WAIT_SECONDS}s 内未监听 $port,最近日志:"
	tail -n 30 "$RUN_DIR/${name}.log" 2>/dev/null || true
	return 1
}

http_ok() {
	local url="$1"
	command -v curl >/dev/null 2>&1 || return 1
	curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null
}

# ---------- 工具探测 ----------
prefer_linux_path() {
	# WSL 下避开 Windows 的 pnpm/node,优先本机
	local dir="$HOME/.vfox/sdks/nodejs/bin"
	if [[ -d "$dir" ]]; then
		case ":$PATH:" in
			*":$dir:"*) ;;
			*) export PATH="$dir:$PATH" ;;
		esac
	fi
}

load_proxy() {
	if [[ -f "$PROXY_SH" ]]; then
		# shellcheck disable=SC1090
		source "$PROXY_SH" >/dev/null
	fi
}

resolve_java() {
	if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
		return 0
	fi
	if [[ -x "$HOME/.vfox/sdks/java/bin/java" ]]; then
		export JAVA_HOME="$HOME/.vfox/sdks/java"
		export PATH="$JAVA_HOME/bin:$PATH"
		return 0
	fi
	if command -v java >/dev/null 2>&1; then
		return 0
	fi
	return 1
}

resolve_mvn() {
	if command -v mvn >/dev/null 2>&1; then
		command -v mvn
		return 0
	fi
	if [[ -x "$BACKEND_DIR/mvnw" ]]; then
		echo "$BACKEND_DIR/mvnw"
		return 0
	fi
	local c
	for c in \
		"$HOME/.sdkman/candidates/maven/current/bin/mvn" \
		/usr/share/maven/bin/mvn \
		/opt/maven/bin/mvn
	do
		if [[ -x "$c" ]]; then
			echo "$c"
			return 0
		fi
	done
	return 1
}

resolve_pnpm() {
	prefer_linux_path
	if [[ -x "$HOME/.vfox/sdks/nodejs/bin/pnpm" ]]; then
		echo "$HOME/.vfox/sdks/nodejs/bin/pnpm"
		return 0
	fi
	if command -v pnpm >/dev/null 2>&1; then
		command -v pnpm
		return 0
	fi
	if command -v corepack >/dev/null 2>&1; then
		echo "corepack pnpm"
		return 0
	fi
	return 1
}

# ---------- 启动 ----------
start_backend() {
	if port_in_use "$BACKEND_PORT"; then
		warn "后端已在端口 $BACKEND_PORT 运行 (PID: $(pids_on_port "$BACKEND_PORT" | tr '\n' ' '))"
		return 0
	fi
	resolve_java || die "未找到 Java,请安装 JDK 25+ 或设置 JAVA_HOME"
	local mvn
	mvn="$(resolve_mvn)" || die "未找到 Maven。请安装 mvn,或在 backend/ 放入 mvnw"
	info "启动后端: $mvn spring-boot:run  (端口 $BACKEND_PORT)"
	# SQLite 数据文件目录(默认数据源,见 application.yml)
	mkdir -p "$BACKEND_DIR/data"
	(
		cd "$BACKEND_DIR"
		export JAVA_HOME="${JAVA_HOME:-}"
		# 拆开 setsid,便于记录会话 PID
		setsid "$mvn" spring-boot:run -DskipTests
	) >"$RUN_DIR/backend.log" 2>&1 &
	echo $! >"$RUN_DIR/backend.pid"
	if wait_port backend "$BACKEND_PORT"; then
		ok "后端已启动  http://127.0.0.1:${BACKEND_PORT}"
	else
		return 1
	fi
}

start_frontend() {
	if port_in_use "$FRONTEND_PORT"; then
		warn "前端已在端口 $FRONTEND_PORT 运行 (PID: $(pids_on_port "$FRONTEND_PORT" | tr '\n' ' '))"
		return 0
	fi
	[[ -d "$FRONTEND_DIR" ]] || die "找不到前端目录: $FRONTEND_DIR"
	local pnpm
	pnpm="$(resolve_pnpm)" || die "未找到 pnpm。请安装 Node.js / pnpm"
	load_proxy
	if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
		info "前端依赖未安装,执行 pnpm install"
		(
			cd "$FRONTEND_DIR"
			# shellcheck disable=SC2086
			$pnpm install
		) || die "pnpm install 失败"
	fi
	info "启动前端: $pnpm dev --host $FRONTEND_HOST --port $FRONTEND_PORT"
	(
		cd "$FRONTEND_DIR"
		# shellcheck disable=SC2086
		setsid $pnpm dev --host "$FRONTEND_HOST" --port "$FRONTEND_PORT"
	) >"$RUN_DIR/frontend.log" 2>&1 &
	echo $! >"$RUN_DIR/frontend.pid"
	if wait_port frontend "$FRONTEND_PORT"; then
		ok "前端已启动  http://${FRONTEND_HOST}:${FRONTEND_PORT}"
	else
		return 1
	fi
}

cmd_start() {
	local target
	target="$(normalize_target "${1:-all}")"
	local failed=0
	if want_backend "$target"; then
		start_backend || failed=1
	fi
	if want_frontend "$target"; then
		start_frontend || failed=1
	fi
	echo
	cmd_status "$target"
	return "$failed"
}

cmd_stop() {
	local target
	target="$(normalize_target "${1:-all}")"
	if want_frontend "$target"; then
		stop_port frontend "$FRONTEND_PORT"
		rm -f "$RUN_DIR/frontend.pid"
	fi
	if want_backend "$target"; then
		stop_port backend "$BACKEND_PORT"
		rm -f "$RUN_DIR/backend.pid"
	fi
}

cmd_restart() {
	local target="${1:-all}"
	cmd_stop "$target"
	cmd_start "$target"
}

print_svc() {
	local name="$1"
	local port="$2"
	local health_url="$3"
	local pids
	pids="$(pids_on_port "$port")"
	if [[ -z "$pids" ]]; then
		printf '  %-10s  %s%-7s%s  port %-5s  %s\n' "$name" "$C_RED" "stopped" "$C_RESET" "$port" "${C_DIM}—${C_RESET}"
		return
	fi
	local health="listening"
	if [[ -n "$health_url" ]] && http_ok "$health_url"; then
		health="healthy"
	fi
	local names=""
	local pid
	for pid in $pids; do
		names+="${pid}/$(proc_name "$pid") "
	done
	printf '  %-10s  %s%-7s%s  port %-5s  %s  %s\n' \
		"$name" "$C_GREEN" "running" "$C_RESET" "$port" "$health" "${C_DIM}${names}${C_RESET}"
}

cmd_status() {
	local target
	target="$(normalize_target "${1:-all}")"
	printf '%s\n' "${C_BOLD}s2admin 开发服务${C_RESET}"
	if want_backend "$target"; then
		print_svc backend "$BACKEND_PORT" "http://127.0.0.1:${BACKEND_PORT}/api/auth/captcha"
	fi
	if want_frontend "$target"; then
		print_svc frontend "$FRONTEND_PORT" "http://${FRONTEND_HOST}:${FRONTEND_PORT}/"
	fi
}

cmd_logs() {
	local target
	target="$(normalize_target "${1:-all}")"
	local files=()
	if want_backend "$target"; then
		files+=("$RUN_DIR/backend.log")
	fi
	if want_frontend "$target"; then
		files+=("$RUN_DIR/frontend.log")
	fi
	local f
	for f in "${files[@]}"; do
		[[ -f "$f" ]] || : >"$f"
	done
	info "跟踪日志: ${files[*]}  (Ctrl+C 退出)"
	tail -n 50 -F "${files[@]}"
}

# ---------- 入口 ----------
main() {
	local cmd="${1:-help}"
	shift || true
	case "$cmd" in
		start)   cmd_start "${1:-all}" ;;
		stop)    cmd_stop "${1:-all}" ;;
		restart) cmd_restart "${1:-all}" ;;
		status)  cmd_status "${1:-all}" ;;
		logs|log) cmd_logs "${1:-all}" ;;
		-h|--help|help) usage ;;
		*)
			err "未知命令: $cmd"
			usage
			exit 1
			;;
	esac
}

main "$@"
