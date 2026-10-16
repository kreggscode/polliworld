# PolliWorld

**Pollinations AI for Minecraft servers.** One plugin: `/polli ask` answers questions, `/polli image` paints a
picture onto a real in-game map item, `/polli models` reads the live model lists, and talking **villager NPCs**
with personas and per-player memory roam your spawn. Players can **bring their own Pollen**: `/polli signin`
walks them through the device flow and their commands bill to *their* key.

![Ask](docs/ask.png)

## Commands

| Command | What it does |
| --- | --- |
| `/polli ask <question>` | Ask Pollinations; reply in chat with token usage |
| `/polli image <prompt>` | Generate a picture and receive it painted on a **map item** (PNG also saved) |
| `/polli models` | The live `gen.pollinations.ai` model lists, free vs Paid Pollen |
| `/polli npc create <name> [persona]` | Spawn a talking villager NPC (op) |
| `/polli say <npc> <message>` | Ask an NPC directly |
| `/polli bye` | Leave the conversation |
| `/polli signin` | **BYOP**: approve a device code in your browser, then your own commands pay with your own Pollen |
| `/polli status` | Key source, models, picture budget, current conversation |

Right-click an NPC to start chatting: your chat messages go to the NPC (shown as `[→ name]`) until `/polli bye`
or a timeout. Each player has their own memory of each NPC, persisted across restarts.

## Install

1. Drop `PolliWorld-1.0.0.jar` into your server's `plugins/` folder (Paper 1.21.x, Java 21).
2. Start the server, open `plugins/PolliWorld/config.yml`, set `api-key` to your key from
   [enter.pollinations.ai](https://enter.pollinations.ai) — or leave it empty and have every player run
   `/polli signin` with their own Pollen.
3. Pick models: `/polli models` shows what's live; any of them works in `config.yml`.

## Bring your own Pollen

`/polli signin` starts the OAuth **device flow** against `enter.pollinations.ai`: the player opens a link,
approves the code, and their scoped key is stored in `keys.yml` on the server. From then on *their* commands
bill to *their* Pollen — free-tier models included, since `/polli models` lists the `paid_only: false` ones.
If a player has no key, calls fall back to the server's `api-key`, and if neither exists the plugin says so
instead of failing silently.

## API used

- `POST https://gen.pollinations.ai/v1/chat/completions` — `/polli ask`, NPC conversations
- `POST https://gen.pollinations.ai/v1/images/generations` — `/polli image` (b64 response, painted onto a map item)
- `GET https://gen.pollinations.ai/text/models` — `/polli models`
- `POST https://enter.pollinations.ai/api/device/code` + `/api/device/token` — `/polli signin` (BYOP)

All calls run **off the main server thread**; results are handed back on it.

## Building

```sh
./gradlew build     # Java 21; the jar lands in build/libs/PolliWorld-1.0.0.jar
./gradlew test      # unit tests for memory, device flow, budgets, API parsing
```

## Config

See [`config.yml`](src/main/resources/config.yml): `api-key`, `text-model`, `image-model`,
`npc.memory-messages`, `npc.session-timeout-seconds`, `limits.chat-cooldown-seconds`,
`limits.max-images-per-day`, `byop.*`.

---

Built for quest [#17246](https://github.com/pollinations/pollinations/issues/17246) · powered by
[Pollinations](https://pollinations.ai) · pay with your own [Pollen](https://enter.pollinations.ai)
