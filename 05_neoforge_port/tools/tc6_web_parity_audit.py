#!/usr/bin/env python3
"""
Thaumcraft 6 -> NeoForge 1.21.1 parity audit helper.

This helper compares the legacy TC6 source/resources with the current NeoForge port
and writes two reports:

  build/reports/tc6_web_parity_audit.md
  build/reports/tc6_web_parity_audit.json

It intentionally does not modify game code. It is meant to be run before each
web-port checkpoint so missing resources, missing recipes and unsafe vanilla
placeholder recipes are visible before producing a test JAR.
"""

from __future__ import annotations

import argparse
import json
import os
from collections import defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Iterable


SPECIAL_RECIPE_TOKENS = {
    "arcane",
    "alchemy",
    "crucible",
    "infusion",
    "matrix",
    "pedestal",
    "essentia",
    "jar",
    "tube",
    "buffer",
    "filter",
    "bellows",
    "furnace",
    "infernal",
    "cluster",
    "golem",
    "seal",
    "focus",
    "foci",
    "lens",
    "gauntlet",
    "wand",
    "thaumometer",
    "goggles",
    "hungry",
    "void",
    "eldritch",
    "cult",
    "traveller",
    "traveler",
    "runic",
    "charm",
    "amulet",
    "ring",
    "belt",
    "bauble",
    "curio",
    "mirror",
    "lamp",
    "thaumium",
    "voidmetal",
    "salismundus",
    "salis_mundus",
}

VANILLA_RECIPE_TYPES = {
    "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless",
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
    "minecraft:stonecutting",
    "crafting_shaped",
    "crafting_shapeless",
    "smelting",
}

RESOURCE_SUBDIRS = (
    "assets/thaumcraft/blockstates",
    "assets/thaumcraft/lang",
    "assets/thaumcraft/models",
    "assets/thaumcraft/particles",
    "assets/thaumcraft/sounds",
    "assets/thaumcraft/textures",
)

TEXTURE_TOKENS_THAT_SHOULD_EXIST = (
    "rift",
    "portal",
    "taint",
    "cult",
    "golem",
    "traveller",
    "traveler",
    "hungry",
    "essentia",
    "jar",
    "tube",
    "focus",
    "gauntlet",
)


@dataclass(frozen=True)
class RecipeRecord:
    path: str
    recipe_type: str
    outputs: tuple[str, ...]
    inputs: tuple[str, ...]


@dataclass(frozen=True)
class SuspiciousRecipe:
    path: str
    recipe_type: str
    output: str
    matched_tokens: tuple[str, ...]
    reason: str


def normalize_rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def is_json_file(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() == ".json"


def load_json(path: Path) -> Any | None:
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except UnicodeDecodeError:
        try:
            return json.loads(path.read_text(encoding="cp1251"))
        except Exception:
            return None
    except Exception:
        return None


def walk_files(root: Path) -> Iterable[Path]:
    if not root.exists():
        return []
    return (p for p in root.rglob("*") if p.is_file())


def find_first_existing(candidates: Iterable[Path]) -> Path | None:
    for candidate in candidates:
        if candidate.exists():
            return candidate
    return None


def discover_port_root(repo_root: Path, explicit: str | None) -> Path:
    if explicit:
        return Path(explicit).resolve()
    direct = repo_root / "05_neoforge_port"
    if direct.exists():
        return direct.resolve()
    for build_gradle in repo_root.rglob("build.gradle"):
        if (build_gradle.parent / "src" / "main" / "resources").exists():
            return build_gradle.parent.resolve()
    raise SystemExit("Could not discover port root. Pass --port-root.")


def discover_legacy_root(repo_root: Path, explicit: str | None) -> Path:
    if explicit:
        return Path(explicit).resolve()
    preferred = repo_root / "Thaumcraft-6-Source-Code-master"
    if preferred.exists():
        return preferred.resolve()
    for marker in repo_root.rglob("mcmod.info"):
        candidate = marker.parent
        if (candidate / "src" / "main" / "resources" / "assets" / "thaumcraft").exists():
            return candidate.resolve()
    for assets in repo_root.rglob("assets/thaumcraft"):
        if "Thaumcraft" in str(assets):
            return assets.parents[2].resolve()
    raise SystemExit("Could not discover legacy TC6 root. Pass --legacy-root.")


def resource_roots(project_root: Path) -> list[Path]:
    roots = [project_root / "src" / "main" / "resources"]
    generated = project_root / "src" / "generated" / "resources"
    if generated.exists():
        roots.append(generated)
    return [r for r in roots if r.exists()]


def collect_resources(project_root: Path) -> dict[str, set[str]]:
    out: dict[str, set[str]] = defaultdict(set)
    for root in resource_roots(project_root):
        for sub in RESOURCE_SUBDIRS:
            base = root / sub
            for file_path in walk_files(base):
                out[sub].add(normalize_rel(file_path, base))
    return out


def collect_json_recipe_roots(project_root: Path) -> list[Path]:
    roots: list[Path] = []
    for root in resource_roots(project_root):
        data_root = root / "data"
        if not data_root.exists():
            continue
        for namespace in data_root.iterdir():
            recipes = namespace / "recipes"
            if recipes.exists():
                roots.append(recipes)
    return roots


def collect_ids(value: Any) -> set[str]:
    ids: set[str] = set()
    if isinstance(value, str):
        if ":" in value and not value.startswith("#"):
            ids.add(value)
    elif isinstance(value, dict):
        for key in ("item", "id"):
            candidate = value.get(key)
            if isinstance(candidate, str) and ":" in candidate and not candidate.startswith("#"):
                ids.add(candidate)
        for nested in value.values():
            ids.update(collect_ids(nested))
    elif isinstance(value, list):
        for nested in value:
            ids.update(collect_ids(nested))
    return ids


def recipe_outputs(data: Any) -> set[str]:
    if not isinstance(data, dict):
        return set()
    outputs: set[str] = set()
    for key in ("result", "output", "results"):
        if key in data:
            outputs.update(collect_ids(data[key]))
    return outputs


def recipe_inputs(data: Any) -> set[str]:
    if not isinstance(data, dict):
        return set()
    ignored = {"result", "output", "results", "conditions", "type", "group", "category", "show_notification"}
    values = [value for key, value in data.items() if key not in ignored]
    return collect_ids(values)


def collect_recipes(project_root: Path) -> list[RecipeRecord]:
    records: list[RecipeRecord] = []
    roots = collect_json_recipe_roots(project_root)
    for recipes_root in roots:
        for path in recipes_root.rglob("*.json"):
            data = load_json(path)
            if not isinstance(data, dict):
                continue
            records.append(
                RecipeRecord(
                    path=path.relative_to(project_root).as_posix(),
                    recipe_type=str(data.get("type", "minecraft:crafting_shaped")),
                    outputs=tuple(sorted(recipe_outputs(data))),
                    inputs=tuple(sorted(recipe_inputs(data))),
                )
            )
    return records


def collect_declared_items_from_models(project_root: Path) -> set[str]:
    items: set[str] = set()
    for root in resource_roots(project_root):
        item_models = root / "assets" / "thaumcraft" / "models" / "item"
        if not item_models.exists():
            continue
        for path in item_models.rglob("*.json"):
            rel = normalize_rel(path, item_models)[:-5]
            items.add("thaumcraft:" + rel)
    return items


def collect_declared_blocks_from_blockstates(project_root: Path) -> set[str]:
    blocks: set[str] = set()
    for root in resource_roots(project_root):
        blockstates = root / "assets" / "thaumcraft" / "blockstates"
        if not blockstates.exists():
            continue
        for path in blockstates.rglob("*.json"):
            rel = normalize_rel(path, blockstates)[:-5]
            blocks.add("thaumcraft:" + rel)
    return blocks


def tokens_in_id(identifier: str) -> tuple[str, ...]:
    normalized = identifier.lower().replace("-", "_").replace("/", "_")
    return tuple(sorted(token for token in SPECIAL_RECIPE_TOKENS if token in normalized))


def find_suspicious_vanilla_recipes(records: Iterable[RecipeRecord]) -> list[SuspiciousRecipe]:
    suspicious: list[SuspiciousRecipe] = []
    for record in records:
        if record.recipe_type not in VANILLA_RECIPE_TYPES:
            continue
        for output in record.outputs:
            if not output.startswith("thaumcraft:"):
                continue
            matched = tokens_in_id(output + " " + record.path)
            if not matched:
                continue
            suspicious.append(
                SuspiciousRecipe(
                    path=record.path,
                    recipe_type=record.recipe_type,
                    output=output,
                    matched_tokens=matched,
                    reason="Thaumcraft special mechanic output is still exposed through a vanilla recipe type.",
                )
            )
    return suspicious


def diff_sets(legacy: dict[str, set[str]], port: dict[str, set[str]]) -> dict[str, list[str]]:
    missing: dict[str, list[str]] = {}
    for subdir in RESOURCE_SUBDIRS:
        missing[subdir] = sorted(legacy.get(subdir, set()) - port.get(subdir, set()))
    return missing


def summarize_focus_textures(missing_textures: list[str]) -> dict[str, list[str]]:
    focus: dict[str, list[str]] = {}
    for token in TEXTURE_TOKENS_THAT_SHOULD_EXIST:
        matches = [p for p in missing_textures if token in p.lower()]
        if matches:
            focus[token] = matches[:50]
    return focus


def first_n(values: Iterable[str], n: int = 80) -> list[str]:
    return list(values)[:n]


def write_reports(
    out_dir: Path,
    repo_root: Path,
    port_root: Path,
    legacy_root: Path,
    missing_resources: dict[str, list[str]],
    missing_recipe_outputs: list[str],
    suspicious: list[SuspiciousRecipe],
    legacy_items: set[str],
    port_items: set[str],
    legacy_blocks: set[str],
    port_blocks: set[str],
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "repo_root": str(repo_root),
        "port_root": str(port_root),
        "legacy_root": str(legacy_root),
        "missing_resources": missing_resources,
        "missing_recipe_outputs": missing_recipe_outputs,
        "suspicious_vanilla_recipes": [asdict(item) for item in suspicious],
        "legacy_model_items_count": len(legacy_items),
        "port_model_items_count": len(port_items),
        "missing_model_items": sorted(legacy_items - port_items),
        "legacy_blockstates_count": len(legacy_blocks),
        "port_blockstates_count": len(port_blocks),
        "missing_blockstates": sorted(legacy_blocks - port_blocks),
        "focused_missing_textures": summarize_focus_textures(missing_resources.get("assets/thaumcraft/textures", [])),
    }
    (out_dir / "tc6_web_parity_audit.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    lines: list[str] = []
    lines.append("# TC6 Web Parity Audit Report")
    lines.append("")
    lines.append(f"Port root: `{port_root}`")
    lines.append(f"Legacy root: `{legacy_root}`")
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- Legacy item model IDs: `{len(legacy_items)}`")
    lines.append(f"- Port item model IDs: `{len(port_items)}`")
    lines.append(f"- Missing item model IDs: `{len(legacy_items - port_items)}`")
    lines.append(f"- Legacy blockstate IDs: `{len(legacy_blocks)}`")
    lines.append(f"- Port blockstate IDs: `{len(port_blocks)}`")
    lines.append(f"- Missing blockstate IDs: `{len(legacy_blocks - port_blocks)}`")
    lines.append(f"- Missing legacy recipe outputs: `{len(missing_recipe_outputs)}`")
    lines.append(f"- Suspicious vanilla placeholder recipes: `{len(suspicious)}`")
    lines.append("")

    lines.append("## Missing resources by resource group")
    lines.append("")
    for subdir, values in missing_resources.items():
        lines.append(f"### `{subdir}` — missing `{len(values)}`")
        for item in first_n(values):
            lines.append(f"- `{item}`")
        if len(values) > 80:
            lines.append(f"- ... `{len(values) - 80}` more")
        lines.append("")

    lines.append("## Missing legacy recipe outputs")
    lines.append("")
    for item in first_n(missing_recipe_outputs, 120):
        lines.append(f"- `{item}`")
    if len(missing_recipe_outputs) > 120:
        lines.append(f"- ... `{len(missing_recipe_outputs) - 120}` more")
    lines.append("")

    lines.append("## Suspicious vanilla placeholder recipes")
    lines.append("")
    for item in suspicious[:160]:
        token_text = ", ".join(item.matched_tokens)
        lines.append(f"- `{item.path}` -> `{item.output}` via `{item.recipe_type}`; tokens: `{token_text}`")
    if len(suspicious) > 160:
        lines.append(f"- ... `{len(suspicious) - 160}` more")
    lines.append("")

    lines.append("## Focused missing texture groups")
    lines.append("")
    focused = summarize_focus_textures(missing_resources.get("assets/thaumcraft/textures", []))
    for token, values in focused.items():
        lines.append(f"### `{token}`")
        for item in values:
            lines.append(f"- `{item}`")
        lines.append("")

    (out_dir / "tc6_web_parity_audit.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit TC6 legacy parity against the NeoForge port.")
    parser.add_argument("--repo-root", default=".", help="Repository root. Default: current directory.")
    parser.add_argument("--port-root", default=None, help="NeoForge port root. Default: auto-discover 05_neoforge_port.")
    parser.add_argument("--legacy-root", default=None, help="Legacy TC6 source root. Default: auto-discover Thaumcraft-6-Source-Code-master.")
    parser.add_argument("--out-dir", default=None, help="Report output directory. Default: <port-root>/build/reports.")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    port_root = discover_port_root(repo_root, args.port_root)
    legacy_root = discover_legacy_root(repo_root, args.legacy_root)
    out_dir = Path(args.out_dir).resolve() if args.out_dir else port_root / "build" / "reports"

    legacy_resources = collect_resources(legacy_root)
    port_resources = collect_resources(port_root)
    missing_resources = diff_sets(legacy_resources, port_resources)

    legacy_recipes = collect_recipes(legacy_root)
    port_recipes = collect_recipes(port_root)
    legacy_outputs = {output for record in legacy_recipes for output in record.outputs if output.startswith("thaumcraft:")}
    port_outputs = {output for record in port_recipes for output in record.outputs if output.startswith("thaumcraft:")}
    missing_recipe_outputs = sorted(legacy_outputs - port_outputs)

    suspicious = find_suspicious_vanilla_recipes(port_recipes)

    legacy_items = collect_declared_items_from_models(legacy_root)
    port_items = collect_declared_items_from_models(port_root)
    legacy_blocks = collect_declared_blocks_from_blockstates(legacy_root)
    port_blocks = collect_declared_blocks_from_blockstates(port_root)

    write_reports(
        out_dir=out_dir,
        repo_root=repo_root,
        port_root=port_root,
        legacy_root=legacy_root,
        missing_resources=missing_resources,
        missing_recipe_outputs=missing_recipe_outputs,
        suspicious=suspicious,
        legacy_items=legacy_items,
        port_items=port_items,
        legacy_blocks=legacy_blocks,
        port_blocks=port_blocks,
    )

    print(f"Wrote {out_dir / 'tc6_web_parity_audit.md'}")
    print(f"Wrote {out_dir / 'tc6_web_parity_audit.json'}")
    print(f"Suspicious vanilla placeholder recipes: {len(suspicious)}")
    print(f"Missing legacy recipe outputs: {len(missing_recipe_outputs)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
