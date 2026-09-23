# discord.yml

The server's announcements, posted to Discord through webhooks: an event started or won, SOTW and EOTW, a bounty placed or collected, a team going raidable. The same text as in the game, colours removed. **Nothing is sent until you switch it on and give a webhook URL.**

**How it plays:** [:octicons-arrow-right-24: read the guide](../../server/integrations.md#discord)

Every example on this page is **taken from the shipped `discord.yml`**. Changes apply with `/hcf reload`.

```yaml title="discord.yml"
--8<-- "src/main/resources/discord.yml:discord"
```

| Key | As shipped | What it does |
|---|---|---|
| `enabled` | `false` | Anything sent at all |
| `webhooks` | `announcements: ""` | Webhook URLs, each under a name of your choosing — one per channel. A URL that is not `https://` is ignored with a warning |
| `username` · `avatar-url` | `HCF` · empty | The name and picture the posts appear under; empty for the webhook's own |
| `forward` | events, phases, bounties, raids | The routing rules, in order: `keys` is a key of the [language file](../messages.md), `*` standing for anything; the **first** rule that matches decides; `to` is a webhook's name, or `""` to send nowhere |

**Rules are read top to bottom.** That is how the shipped file keeps the chatty announcements out — progress lines, countdown marks, every capture of a Conquest zone, every block of a Totem — before `events.*` takes all the rest. To post one of them, delete its line; to stop a kind of post, point its rule at `""`.

No post can ping anybody: `@everyone` in a team's name stays text. Posts go one at a time on a thread of their own; a rate limit from Discord is waited out once, then the post is dropped, and at most 100 wait — an outage never piles up in the server's memory.
