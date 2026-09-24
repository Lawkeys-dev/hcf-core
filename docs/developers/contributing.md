# Contributing

Contributions are welcome — bug reports, fixes, documentation, and features that fit the project's scope.

## Reporting a bug

Open an [issue](https://github.com/Lawkeys-dev/hcf-core/issues) with:

- the HCFCore version (`/hcf version`) and the Paper build;
- what you did, what you expected, and what happened;
- the relevant part of `logs/latest.log`, and the configuration involved.

## Proposing a change

1. Read [`CONTRIBUTING.md`](https://github.com/Lawkeys-dev/hcf-core/blob/main/CONTRIBUTING.md): the stack, the conventions and the rules every change follows.
2. For anything larger than a fix, open an issue first to talk it through.
3. Branch from `main`, one topic per pull request, and target `main`.
4. `./gradlew build` passes locally, with no new warning.
5. Update `docs/` in the same pull request when a command, a permission, a setting or a placeholder changes.
6. Try in game what the unit tests cannot see — listeners, commands, anything that talks to Bukkit — and say what you tried.

## The rules in short

- **No game logic in commands or listeners**: the rules live in plain Java, in the module's manager, with unit tests.
- **Never block the main thread**: every database, file or network operation runs asynchronously.
- **Every gameplay value is configuration**, and every player-facing text is a key in `lang/en.yml`.
- **Official documentation only**: Paper's docs and javadocs, the libraries' own — and say so when something could not be confirmed there.
- **No code copied from a commercial plugin.**
- **No gendered pronoun for a player** — in code, messages or configuration.

## License

HCFCore is released under the [MIT License](https://github.com/Lawkeys-dev/hcf-core/blob/main/LICENSE). By contributing, you agree that your contribution is released under the same license.
