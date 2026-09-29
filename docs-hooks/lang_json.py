"""MkDocs hook: the plugin's messages, for the Theme Builder.

Writes ``tools/lang-en.json`` into the built site: every text of
``src/main/resources/lang/en.yml`` by its full key, as the plugin ships it. The
Theme Builder reads it to list, preview and override any message - so the page
always offers the texts of the version it is published with.
"""

import json
import os

import yaml

SOURCE = os.path.join("src", "main", "resources", "lang", "en.yml")


def _flatten(node, prefix, out):
    for key, value in node.items():
        path = f"{prefix}.{key}" if prefix else str(key)
        if isinstance(value, dict):
            _flatten(value, path, out)
        elif isinstance(value, str):
            out[path] = value


def on_post_build(config, **kwargs):
    root = os.path.dirname(config["config_file_path"])
    with open(os.path.join(root, SOURCE), encoding="utf-8") as file:
        texts = {}
        _flatten(yaml.safe_load(file) or {}, "", texts)
    target = os.path.join(config["site_dir"], "tools", "lang-en.json")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as file:
        json.dump(texts, file, ensure_ascii=False, separators=(",", ":"))
