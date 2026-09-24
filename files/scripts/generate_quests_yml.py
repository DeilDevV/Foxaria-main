# Генерирует modules/quests.yml (5 ступеней × 21 квест, многоцелевые квесты).
# Запуск из корня репозитория: py -3 scripts/generate_quests_yml.py
from __future__ import annotations

import pathlib
from typing import Any

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT_SHOP = ROOT / "modules" / "foxaria-shop" / "src" / "main" / "resources" / "modules" / "quests.yml"
OUT_BOOT = ROOT / "modules" / "foxaria-bootstrap" / "src" / "main" / "resources" / "modules" / "quests.yml"
OUT_TEST = ROOT / "servers" / "game" / "plugins" / "Foxaria" / "modules" / "quests.yml"

TIER_TITLES = [
    ("&eСтупень I — Начало", ["&7Первые шаги: охота, добыча, простые комбинации.", "&7Квесты идут по порядку — 21 штука."]),
    ("&6Ступень II — Закалка", ["&7Сложнее цифры и чаще несколько целей в одном квесте."]),
    ("&bСтупень III — Ремесло", ["&7Рыбалка, крафт, сдача ресурсов и опасные мобы."]),
    ("&dСтупень IV — Испытание", ["&7Долгие цепочки и жёсткие комбинации."]),
    ("&5Ступень V — Вершина", ["&7Финальные испытания для самых упорных."]),
]


def yml_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


def scale(base: int, tier: int, q: int, spread: float = 0.12) -> int:
    """Рост сложности от номера ступени и слота."""
    m = 1.0 + (tier - 1) * 0.42 + (q - 1) * spread + (tier - 1) * (q - 1) * 0.008
    return max(1, int(round(base * m)))


def money_for(tier: int, q: int, n_obj: int) -> str:
    v = 12 + tier * 10 + q * 3 + n_obj * 5 + (tier + q) % 7
    return str(v)


def obj_line(o: dict[str, Any]) -> list[str]:
    # Вложенность как у description: (10 пробелов у «-», 12 у полей записи)
    lines = [
        "          - type: %s" % o["type"],
        "            amount: %d" % o["amount"],
    ]
    if o.get("entity"):
        lines.append('            entity: "%s"' % yml_escape(o["entity"]))
    if o.get("material"):
        lines.append('            material: "%s"' % yml_escape(o["material"]))
    if o.get("label"):
        lines.append('            label: "%s"' % yml_escape(o["label"]))
    return lines


def quest_block(
    tier: int,
    q: int,
    title: str,
    description: list[str],
    objectives: list[dict[str, Any]],
    items_reward: list[tuple[str, int]] | None = None,
) -> str:
    oid = "t%d_q%02d" % (tier, q)
    lines: list[str] = [
        '      - id: "%s"' % oid,
        '        title: "%s"' % yml_escape(title),
        "        description:",
    ]
    for d in description:
        lines.append('          - "%s"' % yml_escape(d))
    lines.append("        objectives:")
    for o in objectives:
        lines.extend(obj_line(o))
    lines.append("        rewards:")
    lines.append('          money: "%s"' % money_for(tier, q, len(objectives)))
    lines.append("          items:")
    # Тестовые предметы (можно править в quests.yml)
    lines.append("            - { material: EMERALD, amount: %d }" % (1 + (tier + q) % 6))
    lines.append("            - { material: GOLD_INGOT, amount: %d }" % (2 + (tier * 2 + q) % 10))
    if tier >= 2:
        lines.append("            - { material: IRON_INGOT, amount: %d }" % (4 + q % 12))
    if tier >= 3:
        lines.append("            - { material: DIAMOND, amount: %d }" % max(1, tier - 2))
    if tier >= 4:
        lines.append("            - { material: EXPERIENCE_BOTTLE, amount: %d }" % (4 + q % 8))
    if tier >= 5 and q % 7 == 0:
        lines.append("            - { material: NETHERITE_SCRAP, amount: 1 }")
    if items_reward:
        for mat, amt in items_reward:
            lines.append("            - { material: %s, amount: %d }" % (mat, amt))
    return "\n".join(lines)


def build_quest(tier: int, q: int) -> str:
    """Один квест: уникальное описание + набор целей по сложности."""
    d_index = (tier - 1) * 21 + (q - 1)
    s = scale
    bonus: list[tuple[str, int]] | None = None

    # Шаблоны смещаются по слоту, чтобы не было скучного повтора подряд
    pid = (d_index * 11 + tier * 5 + q * 3) % 31

    def EK(ent: str, base: int) -> dict[str, Any]:
        return {"type": "ENTITY_KILL", "entity": ent, "amount": s(base, tier, q)}

    def BB(mat: str, base: int) -> dict[str, Any]:
        return {"type": "BLOCK_BREAK", "material": mat, "amount": s(base, tier, q, 0.1)}

    def BP(mat: str, base: int) -> dict[str, Any]:
        return {"type": "BLOCK_PLACE", "material": mat, "amount": s(base, tier, q, 0.1)}

    def CI(mat: str, base: int) -> dict[str, Any]:
        return {"type": "CONSUME_ITEM", "material": mat, "amount": min(32, s(base, tier, q))}

    def CR(mat: str, base: int) -> dict[str, Any]:
        return {"type": "CRAFT_ITEM", "material": mat, "amount": s(base, tier, q)}

    def SU(mat: str, base: int) -> dict[str, Any]:
        return {"type": "SUBMIT_ITEMS", "material": mat, "amount": s(base, tier, q, 0.15)}

    def FI(base: int) -> dict[str, Any]:
        return {"type": "FISH", "amount": s(base, tier, q)}

    def EN(base: int) -> dict[str, Any]:
        return {"type": "ENCHANT_ITEM", "amount": max(1, min(24, s(base, tier, q)))}

    def PL(base: int) -> dict[str, Any]:
        return {"type": "PLAY_MINUTES", "amount": s(base, tier, q, 0.2)}

    def IB(mat: str, base: int) -> dict[str, Any]:
        return {"type": "INTERACT_BLOCK", "material": mat, "amount": s(base, tier, q)}

    def DT(base: int) -> dict[str, Any]:
        return {"type": "DAMAGE_TAKEN", "amount": s(base, tier, q, 0.18)}

    title = ""
    desc: list[str] = []
    objs: list[dict[str, Any]] = []
    bonus: list[tuple[str, int]] | None = None

    if pid == 0:
        title = "&fОхотники на нечисть"
        desc = [
            "&7Стая зомби не даст покоя лагерю — разберитесь с ними и со скелетами.",
            "&7Сначала прикончите зомби, затем скелетов — порядок в списке целей не важен для игры.",
        ]
        objs = [EK("ZOMBIE", 4), EK("SKELETON", 3)]
    elif pid == 1:
        title = "&fГнилой трофей"
        desc = [
            "&7Убейте зомби и соберите гнилую плоть — докажите, что выдержали бой.",
            "&7Плоть можно сдать через меню квеста (ЛКМ по ячейке), когда цель «сдача» активна.",
        ]
        objs = [EK("ZOMBIE", 5), SU("ROTTEN_FLESH", 10)]
    elif pid == 2:
        title = "&fПервый рубеж"
        desc = ["&7Сломайте дёрн и принесите землю — простой старт для новичка.", "&7Дёрн встречается на поверхности; копайте лопатой быстрее."]
        objs = [BB("GRASS_BLOCK", 6), SU("DIRT", 16)]
    elif pid == 3:
        title = "&fСвет во тьме"
        desc = ["&7Нарубите брёвен и поставьте факелы — без света далеко не уйдёте.", "&7Факелы ставятся из палок и угля или угля древесного."]
        objs = [BB("OAK_LOG", 12), BP("TORCH", 8)]
    elif pid == 4:
        title = "&fУлов дня"
        desc = ["&7Поймайте рыбу у водоёма — расслабьтесь и наберитесь терпения.", "&7Подойдёт любая рыба, считается успешный заброс."]
        objs = [FI(3)]
    elif pid == 5:
        title = "&fСтол ремесленника"
        desc = ["&7Скрафтите верстак и сдайте его — покажите, что умеете собирать схемы.", "&7Сначала скрафтите, затем сдайте через меню квеста."]
        objs = [CR("CRAFTING_TABLE", 1), SU("CRAFTING_TABLE", 1)]
    elif pid == 6:
        title = "&fКаменный фундамент"
        desc = ["&7Добудьте булыжник и сложите из него прочную стену.", "&7Кирка ускорит добычу камня под землёй."]
        objs = [BB("COBBLESTONE", 24), BP("COBBLESTONE", 16)]
    elif pid == 7:
        title = "&fПекарь"
        desc = ["&7Скрафтите хлеб и съешьте его — энергия нужна в дороге.", "&7Пшеницу можно вырастить или найти в деревнях."]
        objs = [CR("BREAD", 4), CI("BREAD", 4)]
    elif pid == 8:
        title = "&fПаутина и кости"
        desc = ["&7Пауки и скелеты — классика подземелий. Справьтесь с обоими.", "&7Смотрите под ноги: скелеты стреляют с дистанции."]
        objs = [EK("SPIDER", 4), EK("SKELETON", 4)]
    elif pid == 9:
        title = "&fУгольная жила"
        desc = ["&7Добудьте уголь — топливо и материал для факелов.", "&7Ищите жилы на уровне Y≈48–0."]
        objs = [BB("COAL_ORE", 12)]
    elif pid == 10:
        title = "&fЖелезная воля"
        desc = ["&7Выплавьте слитки и сдайте железо — первый шаг к броне и инструментам.", "&7Печь и уголь ускорят плавку."]
        objs = [CR("IRON_INGOT", 4), SU("IRON_INGOT", 4)]
    elif pid == 11:
        title = "&fШипы в траве"
        desc = ["&7Криперы не любят близких контактов. Уничтожьте несколько штук.", "&7Отходите назад после удара, чтобы не взорваться."]
        objs = [EK("CREEPER", 3)]
    elif pid == 12:
        title = "&fКопание к западу"
        desc = ["&7Сломайте много камня и получите урон — иногда цена опыта больная.", "&7Еда восстанавливает здоровье между ударами."]
        objs = [BB("STONE", 40), DT(25)]
    elif pid == 13:
        title = "&fСундуки деревни"
        desc = ["&7Откройте сундуки в деревне или у себя — привыкайте к хранению лута.", "&7ПКМ по сундуку засчитывает взаимодействие."]
        objs = [IB("CHEST", 6)]
    elif pid == 14:
        title = "&fМинуты тишины"
        desc = ["&7Побудьте онлайн — иногда прогресс измеряется временем, а не кликами.", "&7Счётчик тикает раз в минуту на сервере."]
        objs = [PL(4)]
    elif pid == 15:
        title = "&fОстрый взгляд"
        desc = ["&7Зачаруйте предмет — пусть магия коснётся вашего снаряжения.", "&7Опыт можно получить с мобов и печи."]
        objs = [EN(3)]
    elif pid == 16:
        title = "&fПесчаные работы"
        desc = ["&7Добудьте песок — для стекла и строительства.", "&7Пляжи и пустыни богаты песком."]
        objs = [BB("SAND", 32), BP("SANDSTONE", 8)]
    elif pid == 17:
        title = "&fМорковный урожай"
        desc = ["&7Вырастите или соберите морковь и сдайте урожай.", "&7Фермы на грядках дают стабильный запас."]
        objs = [SU("CARROT", 32)]
    elif pid == 18:
        title = "&fТёмный зверь"
        desc = ["&7Эндермены опасны, но с ними справляются смелые. Одолейте пару особей.", "&7Смотрите в глаза только если готовы к телепорту."]
        objs = [EK("ENDERMAN", 2)]
    elif pid == 19:
        title = "&fДвойной улов"
        desc = ["&7Рыба и уголь — сытость и свет в одном флаконе.", "&7Сочетайте спокойную рыбалку с шахтой."]
        objs = [FI(4), BB("COAL_ORE", 8)]
    elif pid == 20:
        title = "&fПолевая кухня"
        desc = ["&7Скрафтите печь и сдайте её — базовый блок для плавки.", "&7Печь делается из булыжника."]
        objs = [CR("FURNACE", 1), SU("FURNACE", 1)]
    elif pid == 21:
        title = "&fЗарубежный рейд"
        desc = ["&7Зомби и пауки часто идут парами ночью — выдержите натиск.", "&7Держитесь на открытой местности с факелами."]
        objs = [EK("ZOMBIE", 6), EK("SPIDER", 5)]
    elif pid == 22:
        title = "&fЛесоруб"
        desc = ["&7Разные породы дерева пригодятся для строительства.", "&7Срубите дуб и ель — два типа брёвен."]
        objs = [BB("OAK_LOG", 16), BB("SPRUCE_LOG", 16)]
    elif pid == 23:
        title = "&fКостяной лук"
        desc = ["&7Скелеты оставляют кости — соберите их и сдайте.", "&7Кости ещё и для муки useful."]
        objs = [EK("SKELETON", 8), SU("BONE", 16)]
    elif pid == 24:
        title = "&fСтеклянная мастерская"
        desc = ["&7Песок в печи превращается в стекло — сделайте блоки и поставьте окна.", "&7Стекло хрупкое — не ставьте рядом с криперами."]
        objs = [CR("GLASS", 16), BP("GLASS", 8)]
    elif pid == 25:
        title = "&fГолодный путник"
        desc = ["&7Съешьте жареную курицу — быстрый источник сытости.", "&7Кур можно разводить семенами пшеницы."]
        objs = [CI("COOKED_CHICKEN", 6)]
    elif pid == 26:
        title = "&fПшеница и хлеб"
        desc = ["&7Соберите пшеницу и скрафтите хлеб — базовый продукт.", "&7Потом сдайте хлеб как доказательство запасов."]
        objs = [SU("WHEAT", 48), CR("BREAD", 8), SU("BREAD", 8)]
    elif pid == 27:
        title = "&fГлубина и свет"
        desc = ["&7Спуститесь за камнем и поставьте факелы в шахте.", "&7Не забывайте про воду и еду."]
        objs = [BB("DEEPSLATE", 32), BP("TORCH", 20)]
    elif pid == 28:
        title = "&fЧешуйница"
        desc = ["&7Слаймы сыплются из спавнеров и болот — одолейте их и принесите медь.", "&7Медь плавится в печи из сырой руды."]
        objs = [EK("SLIME", tier + 2), SU("COPPER_INGOT", 6)]
    elif pid == 29:
        title = "&fАлмазный блеск"
        desc = ["&7Добудьте алмазы — редкая награда за труд.", "&7Ищите на уровне Y≈-59 в 1.18+."]
        objs = [BB("DIAMOND_ORE", 4)]
    elif pid == 30:
        title = "&fКузница"
        desc = ["&7Скрафтите наковальню и взаимодействуйте с ней — чините и переименовывайте вещи.", "&7Наковальня из железных блоков."]
        objs = [CR("ANVIL", 1), IB("ANVIL", 3)]
    else:
        # Запасной богатый шаблон по индексу
        mobs = ["ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "HUSK", "STRAY", "WITCH"]
        mats = ["OAK_LOG", "STONE", "IRON_ORE", "GOLD_ORE", "SAND", "GRAVEL", "CLAY"]
        m1 = mobs[d_index % len(mobs)]
        m2 = mats[(d_index + tier) % len(mats)]
        title = "&fКомбинированный вызов №%d" % (q + tier * 7)
        desc = [
            "&7Сложное задание: мобы, блоки и время — всё сразу.",
            "&7Чем выше ступень, тем больше цифры — не сдавайтесь.",
        ]
        objs = [
            EK(m1, 5 + tier),
            BB(m2, 18 + q),
            PL(3 + tier),
        ]

    if tier >= 4 and q % 9 == 0:
        bonus = [("GOLDEN_APPLE", 1)]
    elif tier >= 3 and q % 11 == 0:
        bonus = [("EMERALD", 3)]

    title = "%s &7(&8%d·%02d&7)" % (title, tier, q)
    return quest_block(tier, q, title, desc, objs, bonus)


def build_tier(tier: int) -> str:
    title, menu_desc = TIER_TITLES[tier - 1]
    parts = [
        '  "%d":' % tier,
        '    menu-title: "%s"' % yml_escape(title),
        "    menu-description:",
    ]
    for d in menu_desc:
        parts.append('      - "%s"' % yml_escape(d))
    parts.append("    quests:")
    for q in range(1, 22):
        parts.append(build_quest(tier, q))
    return "\n".join(parts)


def main() -> None:
    header = r"""# =============================================================================
# КВЕСТЫ FOXARIA — UTF-8 без BOM, отступы пробелами
# В каждой ступени ровно 21 квест. У квеста список objectives: (одна или несколько целей).
# SUBMIT_ITEMS — сдача через ЛКМ по активной ячейке в меню ступени.
# =============================================================================

_admin-docs:
  types: |
    ENTITY_KILL, PLAYER_KILL, BLOCK_BREAK, BLOCK_PLACE, CONSUME_ITEM, CRAFT_ITEM,
    FISH, ENCHANT_ITEM, PLAY_MINUTES, INTERACT_BLOCK, DAMAGE_TAKEN, SUBMIT_ITEMS

tiers:
"""
    body = "\n".join(build_tier(t) for t in range(1, 6))
    text = header + body + "\n"
    OUT_SHOP.parent.mkdir(parents=True, exist_ok=True)
    OUT_BOOT.parent.mkdir(parents=True, exist_ok=True)
    OUT_SHOP.write_text(text, encoding="utf-8")
    OUT_BOOT.write_text(text, encoding="utf-8")
    if OUT_TEST.parent.exists():
        OUT_TEST.parent.mkdir(parents=True, exist_ok=True)
        OUT_TEST.write_text(text, encoding="utf-8")
        print("Wrote", OUT_TEST)
    print("Wrote", OUT_SHOP)
    print("Wrote", OUT_BOOT)


if __name__ == "__main__":
    main()
