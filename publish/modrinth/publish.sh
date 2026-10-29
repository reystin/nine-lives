#!/usr/bin/env bash
# Публикация Nine Lives на Modrinth: черновик проекта, версия с двумя jar,
# иконка, галерея и отправка на модерацию.
#
# Токен: ~/.config/modrinth/token (chmod 600) или переменная MODRINTH_TOKEN.
# Нужные scopes PAT: PROJECT_CREATE, VERSION_CREATE, PROJECT_WRITE.
#
# Повторный запуск: если проект уже создан, скрипт завершится на 400 и покажет ответ.
set -euo pipefail

API="https://api.modrinth.com/v2"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$ROOT/publish/modrinth"
FULL_JAR="$ROOT/build/libs/ninelives-1.0.0+mc26.3.jar"
SERVER_JAR="$ROOT/build/libs/ninelives-server-1.0.0+mc26.3.jar"
ICON="$ROOT/docs/images/icon-512.png"
GITHUB_REPO="${GH_REPO:-https://github.com/reystin/nine-lives}"

TOKEN="${MODRINTH_TOKEN:-}"
if [[ -z "$TOKEN" && -f "$HOME/.config/modrinth/token" ]]; then
	TOKEN="$(tr -d '[:space:]' < "$HOME/.config/modrinth/token")"
fi
if [[ -z "$TOKEN" ]]; then
	cat >&2 <<'MSG'
Токен Modrinth не найден.
Создайте PAT: https://modrinth.com/settings/pats
Scopes: PROJECT_CREATE, VERSION_CREATE, PROJECT_WRITE.
Сохраните в ~/.config/modrinth/token (chmod 600) или export MODRINTH_TOKEN=...
MSG
	exit 1
fi

for file in "$FULL_JAR" "$SERVER_JAR" "$ICON" "$HERE/body.md" "$HERE/changelog.md"; do
	[[ -f "$file" ]] || { echo "Нет файла: $file" >&2; exit 1; }
done

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

urlenc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1]))' "$1"; }

show() { # файл с ответом
	if [[ -s "$1" ]]; then python3 -m json.tool "$1" 2>/dev/null || cat "$1"; fi
}

# ---------------------------------------------------------------- проект
python3 - "$HERE/body.md" "$GITHUB_REPO" "$TMP/project.json" <<'PY'
import json, sys
body = open(sys.argv[1], encoding="utf-8").read().replace("{{GITHUB}}", sys.argv[2])
data = {
    "slug": "ninelives",
    "title": "Nine Lives",
    "description": "Everyone has one heart. Earn extra lives with advancements, spend them on death, and share them with friends.",
    "body": body,
    "categories": ["adventure", "game-mechanics", "utility"],
    "additional_categories": ["minigame", "management"],
    "project_type": "mod",
    "environment": ["client_and_server"],
    "client_side": "optional",
    "server_side": "required",
    "license_id": "MIT",
    "source_url": sys.argv[2],
    "issues_url": sys.argv[2] + "/issues",
    "is_draft": True,
    "initial_versions": [],
}
json.dump(data, open(sys.argv[3], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY

echo "== создаю черновик проекта"
CODE="$(curl -sS -o "$TMP/project-resp.json" -w '%{http_code}' \
	-X POST "$API/project" \
	-H "Authorization: Bearer $TOKEN" \
	-F "data=@$TMP/project.json;type=application/json" \
	-F "icon=@$ICON;type=image/png")"
if [[ "$CODE" != "200" ]]; then
	echo "Ошибка создания проекта (HTTP $CODE):" >&2
	show "$TMP/project-resp.json" >&2
	exit 1
fi
PROJECT_ID="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["id"])' "$TMP/project-resp.json")"
SLUG="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1])).get("slug") or "ninelives")' "$TMP/project-resp.json")"
echo "проект: $PROJECT_ID ($SLUG)"

# ---------------------------------------------------------------- версия
python3 - "$HERE/changelog.md" "$PROJECT_ID" "$TMP/version.json" <<'PY'
import json, sys
changelog = open(sys.argv[1], encoding="utf-8").read()
data = {
    "project_id": sys.argv[2],
    "name": "Nine Lives 1.0.0 for Minecraft 26.3",
    "version_number": "1.0.0+mc26.3",
    "changelog": changelog,
    "dependencies": [
        {"project_id": "P7dR8mSH", "dependency_type": "required"},
        {"project_id": "mOgUt4GM", "dependency_type": "optional"},
    ],
    "game_versions": ["26.3"],
    "version_type": "release",
    "loaders": ["fabric"],
    "featured": True,
    "environment": "client_and_server",
    "file_parts": ["full", "server"],
    "primary_file": "full",
    "status": "listed",
}
json.dump(data, open(sys.argv[3], "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY

echo "== загружаю версию 1.0.0+mc26.3"
CODE="$(curl -sS -o "$TMP/version-resp.json" -w '%{http_code}' \
	-X POST "$API/version" \
	-H "Authorization: Bearer $TOKEN" \
	-F "data=@$TMP/version.json;type=application/json" \
	-F "full=@$FULL_JAR;type=application/java-archive" \
	-F "server=@$SERVER_JAR;type=application/java-archive")"
if [[ "$CODE" != "200" ]]; then
	echo "Ошибка загрузки версии (HTTP $CODE):" >&2
	show "$TMP/version-resp.json" >&2
	exit 1
fi
echo "версия загружена"

# ---------------------------------------------------------------- галерея
add_image() { # файл | заголовок | описание | featured | порядок
	local file="$1" title="$2" desc="$3" featured="$4" ordering="$5"
	local code
	code="$(curl -sS -o "$TMP/gallery-resp.json" -w '%{http_code}' \
		-X POST "$API/project/$PROJECT_ID/gallery?ext=png&featured=$featured&title=$(urlenc "$title")&description=$(urlenc "$desc")&ordering=$ordering" \
		-H "Authorization: Bearer $TOKEN" \
		-H "Content-Type: image/png" \
		--data-binary "@$file")"
	if [[ "$code" == "204" ]]; then
		echo "галерея: $title"
	else
		echo "предупреждение: галерея «$title» не загрузилась (HTTP $code)" >&2
		show "$TMP/gallery-resp.json" >&2 || true
	fi
}

add_image "$ROOT/docs/images/banner-ninelives.png" "Nine Lives" "One heart. Nine lives." true 0
add_image "$ROOT/docs/images/en/ninelives_screen_overview.png" "Overview" "Lives, progress and mode" false 1
add_image "$ROOT/docs/images/en/ninelives_screen_advancements.png" "Advancements" "Hard achievements and nearest progress" false 2
add_image "$ROOT/docs/images/en/ninelives_mode_select.png" "Mode selection" "Vanilla or Blaze and Caves" false 3
add_image "$ROOT/docs/images/en/ninelives_datapack_progress.png" "BACAP auto-install" "The datapack is downloaded and verified on the server" false 4
add_image "$ROOT/docs/images/en/ninelives_bacap_advancements.png" "Super challenges" "BACAP super challenges" false 5
add_image "$ROOT/docs/images/en/ninelives_screen_players.png" "Players" "Life transfer between players" false 6

# ---------------------------------------------------------------- модерация
echo "== отправляю на модерацию"
CODE="$(curl -sS -o "$TMP/submit-resp.json" -w '%{http_code}' \
	-X PATCH "$API/project/$PROJECT_ID" \
	-H "Authorization: Bearer $TOKEN" \
	-H "Content-Type: application/json" \
	-d '{"requested_status":"approved"}')"
if [[ "$CODE" == "204" ]]; then
	echo "проект отправлен на модерацию"
else
	echo "предупреждение: не удалось отправить на модерацию (HTTP $CODE)." >&2
	echo "Откройте https://modrinth.com/mod/$SLUG и нажмите Submit for review." >&2
	show "$TMP/submit-resp.json" >&2 || true
fi

echo
echo "Готово: https://modrinth.com/mod/$SLUG"
