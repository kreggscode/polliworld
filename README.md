# PolliWorld

[![ci](https://github.com/kreggscode/polliworld/actions/workflows/ci.yml/badge.svg)](https://github.com/kreggscode/polliworld/actions/workflows/ci.yml)
[![Made with pollinations.ai](https://raw.githubusercontent.com/pollinations/pollinations/main/packages/ui/src/brand/badge-made-with.svg)](https://pollinations.ai/?ref=badge)

**Pollinations AI for Minecraft servers.** One plugin: `/polli ask` answers questions, `/polli image` paints a
picture onto a real in-game map item, `/polli models` reads the live model lists, and talking **villager NPCs**
with personas and per-player memory roam your spawn. Players can **bring their own Pollen**: `/polli signin`
walks them through the device flow and their commands bill to *their* key.

![The picture a plugin command produced, hung in an item frame at spawn](evidence/village-on-item-frame.png)

## Commands

| Command | What it does |
| --- | --- |
| `/polli ask <question>` | Ask Pollinations; reply in chat with token usage and the **Pollen cost** of the request |
| `/polli image <prompt>` | Generate a picture and receive it painted on a **map item** (PNG also saved, cost shown) |
| `/polli models` | The live `gen.pollinations.ai` model lists, free vs Paid Pollen |
| `/polli npc create <name> [persona]` | Spawn a talking villager NPC (op) |
| `/polli say <npc> <message>` | Ask an NPC directly |
| `/polli bye` | Leave the conversation |
| `/polli signin` | **BYOP**: approve a device code in your browser, then your own commands pay with your own Pollen |
| `/polli status` | Key source, models, picture budget, current conversation, **live Pollen balance** |

Right-click an NPC to start chatting: your chat messages go to the NPC (shown as `[→ name]`) until `/polli bye`
or a timeout. Each player has their own memory of each NPC, persisted across restarts.

## Install

1. Download `PolliWorld-1.0.0.jar` from the [Releases page](https://github.com/kreggscode/polliworld/releases)
   and drop it into your server's `plugins/` folder (Paper 1.21.x, Java 21).
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
- `GET https://gen.pollinations.ai/account/key/usage` — the **Pollen cost** printed after every answer and picture
- `GET https://gen.pollinations.ai/account/balance` — the Pollen balance in `/polli status`
- `POST https://enter.pollinations.ai/api/device/code` + `/api/device/token` — `/polli signin` (BYOP)

All calls run **off the main server thread**; results are handed back on it.

## Evidence

[`evidence/`](evidence/) holds output from the plugin running on a real Paper 1.21.11 server:
the annotated [console log](evidence/console-log.txt) (Bjorn answering in character with token counts,
224 live models listed, restart persistence, the image run) and the exact PNGs the plugin produced,
including [art hung in an item frame at spawn](evidence/village-on-item-frame.png).

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
