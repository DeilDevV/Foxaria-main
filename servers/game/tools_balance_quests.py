# -*- coding: utf-8 -*-
"""Усиление amount в objectives; ruamel сохраняет комментарии и форматирование."""
from __future__ import annotations

import math
import sys
from pathlib import Path

from ruamel.yaml import YAML

ROOT = Path(__file__).resolve().parent
PATH = ROOT / "plugins" / "Foxaria" / "modules" / "quests.yml"

TIER_BASE = {1: 1.55, 2: 1.45, 3: 1.38, 4: 1.32, 5: 1.28}

CAPS = {
    "BLOCK_BREAK": 8000,
    "BLOCK_PLACE": 4000,
    "ENTITY_KILL": 2500,
    "SUBMIT_ITEMS": 12000,
    "FISH": 400,
    "CRAFT_ITEM": 320,
    "ENCHANT_ITEM": 120,
    "CONSUME_ITEM": 200,
    "INTERACT_BLOCK": 500,
    "DAMAGE_TAKEN": 8000,
    "PLAY_MINUTES": 10000,
    "PLAYER_KILL": 200,
}


def ramp(quest_index: int) -> float:
    return 1.0 + (quest_index / 20.0) * 0.42


def scale_amount(tier: int, qidx: int, obj: dict) -> None:
    t = str(obj.get("type", "")).upper()
    raw = obj.get("amount")
    if not isinstance(raw, int):
        return
    base = TIER_BASE.get(tier, 1.35) * ramp(qidx)
    if t == "CRAFT_ITEM" and raw <= 3:
        new_amt = max(raw, min(12, int(math.ceil(raw * min(base, 2.2)))))
    elif t == "CRAFT_ITEM":
        new_amt = int(math.ceil(raw * base))
    else:
        new_amt = int(math.ceil(raw * base))
    cap = CAPS.get(t)
    if cap is not None:
        new_amt = min(new_amt, cap)
    obj["amount"] = max(1, new_amt)


def main() -> None:
    y = YAML()
    y.preserve_quotes = True
    y.indent(mapping=2, sequence=4, offset=2)
    y.width = 4096

    with PATH.open("r", encoding="utf-8") as f:
        data = y.load(f)

    tiers = data.get("tiers") or {}
    for tk in sorted(tiers.keys(), key=lambda x: int(str(x))):
        tier_num = int(str(tk))
        td = tiers[tk]
        quests = td.get("quests") or []
        for qi, quest in enumerate(quests):
            for obj in quest.get("objectives") or []:
                if isinstance(obj, dict):
                    scale_amount(tier_num, qi, obj)

    with PATH.open("w", encoding="utf-8") as f:
        y.dump(data, f)

    print("OK:", PATH)


if __name__ == "__main__":
    main()
