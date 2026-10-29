# Публикация Nine Lives

Всё для GitHub и Modrinth уже лежит в репозитории. Ниже — что нужно сделать один раз
(аккаунты) и как запускается публикация.

## 1. GitHub

```bash
brew install gh
gh auth login          # GitHub.com → HTTPS → Login with a web browser
```

После входа из корня проекта:

```bash
gh repo create reystin/nine-lives --public --source=. --remote=origin --push
gh repo edit reystin/nine-lives \
  --description "One heart, nine lives: a Fabric mod for Minecraft 26.3 where lives are earned with advancements and shared with friends." \
  --add-topic minecraft --add-topic fabric --add-topic fabric-mod --add-topic minecraft-mod \
  --add-topic hardcore --add-topic advancements --add-topic blaze-and-caves --add-topic ai-assisted
```

Релиз с обоими jar:

```bash
gh release create v1.0.0 \
  build/libs/ninelives-1.0.0+mc26.3.jar \
  build/libs/ninelives-server-1.0.0+mc26.3.jar \
  --title "Nine Lives 1.0.0" --notes-file publish/modrinth/changelog.md
```

Если имя пользователя GitHub не `reystin`, замените его в ссылках README (`docs`
используют относительные пути, менять нужно только команды `gh`).

## 2. Modrinth

1. Создайте PAT: <https://modrinth.com/settings/pats>.
   Нужные scopes: **PROJECT_CREATE**, **VERSION_CREATE**, **PROJECT_WRITE**.
2. Сохраните токен:

   ```bash
   mkdir -p ~/.config/modrinth
   printf '%s' 'mrp_...' > ~/.config/modrinth/token
   chmod 600 ~/.config/modrinth/token
   ```

3. Соберите оба jar и запустите скрипт:

   ```bash
   JAVA_HOME=~/.jdks/jdk-25.0.5+8/Contents/Home ./gradlew build -Pvariant=full
   JAVA_HOME=~/.jdks/jdk-25.0.5+8/Contents/Home ./gradlew build -Pvariant=server
   GH_REPO=https://github.com/ВАШ_ЛОГИН/nine-lives ./publish/modrinth/publish.sh
   ```

Скрипт создаёт черновик проекта `ninelives`, загружает версию `1.0.0+mc26.3`
с двумя файлами (полный jar — основной), ставит иконку, добавляет баннер и
скриншоты в галерею и отправляет проект на модерацию. Итог:
<https://modrinth.com/mod/ninelives>.

> Повторный запуск создаст дубликат проекта; при необходимости продолжайте
> вручную на сайте.

### Если нужно вручную

Все поля для веб-формы: `publish/modrinth/body.md` (описание, уже с пометкой об ИИ),
`publish/modrinth/changelog.md` (changelog), `docs/images/icon-512.png` (иконка),
`docs/images/banner-ninelives.png` и `docs/images/en/*.png` (галерея). Параметры версии:
Minecraft 26.3, Fabric, release, зависимости Fabric API (required) и Mod Menu
(optional).

## 3. Пометка об ИИ

Пометка уже включена в оформление:

- бейдж **AI-assisted** и раздел **AI disclosure** в README (EN/RU);
- тема `ai-assisted` у репозитория;
- раздел **AI disclosure** в описании Modrinth;
- бейдж в баннере не используется — вместо него явный текст в README и описании.
