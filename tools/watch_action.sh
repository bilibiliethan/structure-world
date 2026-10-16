#!/bin/bash
# 轮询 GitHub Actions 运行状态，直到完成（最多约 20 分钟）
# 用法: watch_action.sh [head_branch]   head_branch 即分支名或标签名，用于筛选运行
REPO=bilibiliethan/structure-world
PY="C:/Users/admin/.workbuddy/binaries/python/envs/default/Scripts/python.exe"
FILTER="${1:-}"
export FILTER
API="https://api.github.com/repos/$REPO/actions/runs"

RUN_ID=""
for i in $(seq 1 20); do
  JSON=$(curl -sS -m 25 --ssl-no-revoke "$API?per_page=10" 2>/dev/null)
  LINE=$(printf '%s' "$JSON" | "$PY" -c "
import sys, json, os
filt = os.environ.get('FILTER', '')
try:
    d = json.load(sys.stdin)
except Exception:
    print('PARSE_FAIL'); raise SystemExit
rs = d.get('workflow_runs') or []
if filt:
    rs = [r for r in rs if (r.get('head_branch') or '') == filt]
if not rs:
    print('NO_RUN'); raise SystemExit
r = rs[0]
print('%s|%s|%s|%s' % (r.get('id'), r.get('status'), r.get('conclusion'), r.get('head_sha','')[:7]))
" 2>/dev/null)
  echo "[等待工作流出现 $i] $LINE"
  case "$LINE" in
    *"|completed|"*) RUN_ID=$(printf '%s' "$LINE" | cut -d'|' -f1); break;;
    "NO_RUN"|"PARSE_FAIL") ;;
    *) RUN_ID=$(printf '%s' "$LINE" | cut -d'|' -f1) ;;
  esac
  sleep 15
done

if [ -z "$RUN_ID" ]; then
  echo "未能获取运行 ID，放弃轮询"
  exit 1
fi
echo "RUN_ID=$RUN_ID"
echo "FILTER=$FILTER"

# 等它跑完
for i in $(seq 1 60); do
  JSON=$(curl -sS -m 25 --ssl-no-revoke "$API/$RUN_ID" 2>/dev/null)
  LINE=$(printf '%s' "$JSON" | "$PY" -c "
import sys, json
d = json.load(sys.stdin)
print('%s|%s|%s' % (d.get('status'), d.get('conclusion'), d.get('html_url')))
" 2>/dev/null)
  echo "[轮询 $i] $LINE"
  case "$LINE" in
    "completed|"*) break;;
  esac
  sleep 20
done

echo ""
echo "================ 各 job 与步骤结果 ================"
curl -sS -m 25 --ssl-no-revoke "$API/$RUN_ID/jobs" 2>/dev/null | "$PY" -c "
import sys, json
d = json.load(sys.stdin)
for j in d.get('jobs', []):
    print('JOB: %s | %s | %s' % (j.get('name'), j.get('status'), j.get('conclusion')))
    for s in j.get('steps', []):
        print('   %-28s %-10s %s' % (s.get('name'), s.get('status'), s.get('conclusion')))
" 2>/dev/null
