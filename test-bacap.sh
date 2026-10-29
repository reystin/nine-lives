#!/bin/bash
# Тест авто-установки BACAP на dev-сервере (runServer + консоль stdin).
# Аргумент 1: "ok" — разрешённые домены; "fail" — неверный домен (проверка отката).
set -e
cd "$(dirname "$0")"
export JAVA_HOME="$HOME/.jdks/jdk-25.0.5+8/Contents/Home"

MODE="${1:-ok}"
if [ "$MODE" = "fail" ]; then
  python3 - <<'EOF'
import json, pathlib
p = pathlib.Path('run/config/ninelives.json')
d = json.loads(p.read_text())
d['allowedDomains'] = ['example.invalid']
p.write_text(json.dumps(d, indent=2))
print('[test] allowedDomains заморожены на example.invalid — ждём ошибку и откат')
EOF
else
  python3 - <<'EOF'
import json, pathlib
p = pathlib.Path('run/config/ninelives.json')
d = json.loads(p.read_text())
d['allowedDomains'] = ['api.modrinth.com', 'cdn.modrinth.com']
p.write_text(json.dumps(d, indent=2))
print('[test] домены разрешены — ждём успешную установку')
EOF
fi

mkdir -p run
[ -f run/eula.txt ] || echo "eula=true" > run/eula.txt

(sleep 50
 echo "ninelives datapack status"
 sleep 3
 echo "ninelives datapack install"
 sleep 60
 echo "ninelives datapack status"
 sleep 3
 echo "stop"
) | ./gradlew runServer -Pvariant=full --console=plain > "/tmp/bacap_server_$MODE.log" 2>&1

echo "exit=$?"
grep -E "Nine Lives" "/tmp/bacap_server_$MODE.log" | tail -12
