![Nine Lives](https://raw.githubusercontent.com/reystin/nine-lives/main/docs/images/banner-ninelives.png)

# Nine Lives

**Everyone has exactly one heart. Lives are earned with advancements, spent on death, and can be given to friends.**

Nine Lives is a Fabric mod for **Minecraft 26.3** that turns the game into a shared
hardcore run. Max health is locked at one heart for every player, while absorption,
golden apples and totems still work like vanilla. Advancing earns extra lives; dying
spends one; at zero lives you become a spectator until someone transfers a life to you.

## Features

- **1 HP cap** — food, enchants, effects, attribute modifiers and commands cannot raise
  max health. Absorption, golden apples, totems and Health Boost keep working.
- **9 starting lives**, no upper limit. A death spends one life; a totem save does not.
- **15 seconds of respawn protection**, cancelled early if you attack.
- **0 lives → spectator**, revived only by a life transfer from another player.
- **Life transfer** with a card UI. The giver always keeps at least one life.
- **Two advancement modes** with once-only counting and progress import:
  - **Vanilla:** +1 life per 10 advancements and +1 for each of 13 curated hard
    advancements (all effects, all potions, all biomes, all mobs, all cats/wolves/
    animals, balanced diet, dragon, beacon, armor trims, ...).
  - **Blaze and Caves:** +1 life per 50 BACAP advancements and +1 per super challenge.
- **BACAP auto-install** — choosing the BACAP mode downloads the datapack from
  Modrinth on the server, verifies the SHA-1 from the API response, enables it and
  reloads without leaving the world. Only allow-listed official domains are used and
  the datapack is never bundled in the jar.
- **Vanilla clients can join** with the server-only jar: Tab shows `♥12 · ★45 Name`,
  plus chat, action bar and title feedback.
- **RU/EN localization**, dark glass burgundy-and-gold interface, animated HUD, toasts,
  particles and sounds (full build).

## Which file to download?

| Build | File | Where to install |
|---|---|---|
| Full (client + server) | `ninelives-1.0.0+mc26.3.jar` | Server **and every player**, or just `mods/` in singleplayer. Includes the full interface. |
| Server-only | `ninelives-server-1.0.0+mc26.3.jar` | **Server only.** Players can use vanilla clients. |

Requirements: Minecraft **26.3**, Fabric Loader **0.20.1+**, Fabric API, Java **25**.
Mod Menu is optional for the client settings screen.

> Both builds come from the same source. The server jar excludes client classes,
> textures and the client mixin config; the full jar works on both sides.

## Advancement modes

The world host picks the mode on first join, or an operator changes it later with
`/ninelives mode`. Until a mode is chosen, `defaultMode` from the config applies.
Each advancement counts exactly once, and progress earned before installing the mod is
imported on first join.

## Commands

```
/ninelives get|info [player]          status
/ninelives give <player> [amount]     transfer lives (giver keeps at least 1)
/ninelives set|add|reset <player> [n] operator
/ninelives mode <vanilla|bc|clear>    operator
/ninelives datapack status|install    operator
/ninelives config reload              operator
```

## Configuration

Server: `config/ninelives.json` — advancements per life, hard list, super challenges,
BACAP tab root, respawn protection, default mode, server messages language (en/ru),
download allow-list, vanilla fallback on install failure.

Client (full build): `config/ninelives-client.json` — HUD, animations, particles,
sounds, UI scale.

## AI disclosure

This project is developed **with AI assistance** (Qoder). The author defines the
design, reviews every change, runs the game tests and verifies in-game behavior.
Branding and textures are generated procedurally by project scripts, not by an image
model.

## Links

- Source code and issues: {{GITHUB}}
- License: MIT

---

## По-русски

**У всех игроков ровно одно сердце. Жизни зарабатываются достижениями, тратятся на
смерть и передаются другим игрокам.**

Fabric-мод для **Minecraft 26.3**: максимум здоровья зафиксирован на 1 HP (поглощение,
золотые яблоки и тотемы работают как в ванили). Старт с 9 жизнями, смерть тратит одну,
после возрождения — 15 секунд защиты. При нуле жизней игрок становится наблюдателем,
пока ему не передадут жизнь.

Два режима достижений:

- **Ванильные** — +1 жизнь за каждые 10 достижений и +1 за каждое из 13 курируемых
  хард-достижений.
- **Blaze and Caves** — +1 жизнь за каждые 50 достижений BACAP и +1 за каждый
  супер-челлендж. Датапак автоматически скачивается с Modrinth на сервере, проверяется
  по SHA-1 из ответа API и включается без выхода из мира. В jar датапак не входит,
  разрешены только официальные домены.

Серверная сборка (`ninelives-server`) позволяет играть с ванильным клиентом: жизни
видны в Tab, чате, action bar и title. Полная сборка добавляет экран жизней, HUD,
тосты, анимации и настройки через Mod Menu.

Проект разрабатывается с использованием ИИ (Qoder); автор проверяет каждое изменение
и запускает геймтесты. Лицензия MIT.
