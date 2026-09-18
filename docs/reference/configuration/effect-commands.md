# effect-commands.yml

Commands that give an effect until death: `/speed`, `/strength` and your own.

**How it plays:** [:octicons-arrow-right-24: read the guide](../../gameplay/effect-commands.md)

Every example on this page is **taken from the shipped `effect-commands.yml`**. `/hcf reload` applies a new effect, level or permission; **adding, removing or renaming a command or an alias needs a restart**.

## Switch

```yaml title="effect-commands.yml"
--8<-- "src/main/resources/effect-commands.yml:enabled"
```

`enabled: false` at startup registers no command; switched off by `/hcf reload`, the commands answer that they are switched off until the next restart.

## Commands

```yaml title="effect-commands.yml"
--8<-- "src/main/resources/effect-commands.yml:commands"
```

| Key | As shipped | What it does |
|---|---|---|
| `commands.<name>` | nine commands | The command, without its slash: letters, digits, `_` and `-` |
| `effect` | — | The effect: `speed`, `strength`, `jump_boost`, `fire_resistance`… (another namespace is written in full, `mypack:glow`) |
| `level` | — | `1` is level I, up to 255 |
| `aliases` | one or two each | Other names; a name or alias belongs to the first command that claims it, and never takes over another plugin's command |
| `permission` | `hcfcore.effect.<name>` | Who may use it; operators by default |

A command with no effect, a level out of range or a name already taken is left out, with a warning in the console.
