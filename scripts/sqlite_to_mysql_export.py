#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sqlite3
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Export Foxaria SQLite databases into a MySQL-compatible SQL dump.")
    parser.add_argument("--main-sqlite", required=True, help="Path to the main Foxaria sqlite database.")
    parser.add_argument("--proxy-sqlite", required=True, help="Path to the FoxariaProxy sqlite database.")
    parser.add_argument("--out", required=True, help="Output .sql file path.")
    parser.add_argument("--database", default="foxaria", help="Target MySQL database name.")
    return parser.parse_args()


def quote_ident(name: str) -> str:
    return "`" + name.replace("`", "``") + "`"


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, bytes):
        return "0x" + value.hex()
    text = str(value)
    text = text.replace("\\", "\\\\").replace("'", "''").replace("\r", "\\r").replace("\n", "\\n")
    return "'" + text + "'"


def infer_varchar_length(column_name: str, default_len: int = 255) -> int:
    lower = column_name.lower()
    if "uuid" in lower:
        return 36
    if lower.endswith("_id") or lower == "id":
        return 128
    if "name" in lower:
        return 128
    if "key" in lower:
        return 128
    return default_len


def convert_column_definition(line: str) -> str:
    stripped = line.strip().lstrip(",").rstrip(",")
    match = re.match(r'^("?[\w]+"?)\s+(.*)$', stripped)
    if not match:
        return line
    name, rest = match.groups()
    normalized_name = name.strip('"`')
    mysql_type = rest
    mysql_type = re.sub(r"(?i)\bINTEGER\s+PRIMARY\s+KEY\s+AUTOINCREMENT\b", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY", mysql_type)
    mysql_type = re.sub(r"(?i)\bINT\s+PRIMARY\s+KEY\s+AUTOINCREMENT\b", "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY", mysql_type)
    mysql_type = re.sub(r"(?i)\bBOOLEAN\b", "TINYINT(1)", mysql_type)

    if re.search(r"(?i)\bTEXT\s+PRIMARY\s+KEY\b", mysql_type):
        mysql_type = re.sub(
            r"(?i)\bTEXT\s+PRIMARY\s+KEY\b",
            f"VARCHAR({infer_varchar_length(normalized_name)}) PRIMARY KEY",
            mysql_type,
        )
    else:
        mysql_type = re.sub(r"(?i)\bTEXT\b", "TEXT", mysql_type)

    mysql_type = re.sub(r'(?i)\bINTEGER\b', "INT", mysql_type)
    return f"  {quote_ident(normalized_name)} {mysql_type}"


def convert_create_sql(sql: str, table_name: str) -> str:
    sql = sql.strip()
    header = f"CREATE TABLE IF NOT EXISTS {quote_ident(table_name)} ("
    inner = sql[sql.find("(") + 1 : sql.rfind(")")]
    lines = split_sql_parts(inner)
    converted: list[str] = []
    for raw in lines:
        stripped = raw.strip().lstrip(",").rstrip(",")
        upper = stripped.upper()
        if upper.startswith("PRIMARY KEY") or upper.startswith("UNIQUE") or upper.startswith("CONSTRAINT") or upper.startswith("FOREIGN KEY"):
            converted.append("  " + stripped)
            continue
        converted.append(convert_column_definition(raw))
    body = ",\n".join(converted)
    return f"{header}\n{body}\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"


def split_sql_parts(inner: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    depth = 0
    in_single = False
    in_double = False
    prev = ""
    for ch in inner:
        if ch == "'" and not in_double and prev != "\\":
            in_single = not in_single
        elif ch == '"' and not in_single and prev != "\\":
            in_double = not in_double
        elif not in_single and not in_double:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth = max(0, depth - 1)
            elif ch == "," and depth == 0:
                part = "".join(current).strip()
                if part:
                    parts.append(part)
                current = []
                prev = ch
                continue
        current.append(ch)
        prev = ch
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def write_table_dump(conn: sqlite3.Connection, table_name: str, create_sql: str, out):
    out.write(f"\n--\n-- Table structure for {table_name}\n--\n")
    out.write(f"DROP TABLE IF EXISTS {quote_ident(table_name)};\n")
    out.write(convert_create_sql(create_sql, table_name))
    out.write("\n")

    cur = conn.execute(f'SELECT * FROM "{table_name}"')
    columns = [desc[0] for desc in cur.description]
    rows = cur.fetchall()
    out.write(f"\n-- Data for {table_name}: {len(rows)} rows\n")
    if not rows:
        return

    col_sql = ", ".join(quote_ident(col) for col in columns)
    batch_size = 200
    for start in range(0, len(rows), batch_size):
        batch = rows[start : start + batch_size]
        values_sql = []
        for row in batch:
            values_sql.append("(" + ", ".join(sql_literal(value) for value in row) + ")")
        out.write(f"INSERT INTO {quote_ident(table_name)} ({col_sql}) VALUES\n")
        out.write(",\n".join(values_sql))
        out.write(";\n")


def write_database_dump(db_path: Path, label: str, out):
    conn = sqlite3.connect(str(db_path))
    try:
        rows = conn.execute(
            "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).fetchall()
        out.write(f"\n-- ============================================================\n")
        out.write(f"-- Source: {label}\n")
        out.write(f"-- File: {db_path}\n")
        out.write(f"-- ============================================================\n")
        for table_name, create_sql in rows:
            write_table_dump(conn, table_name, create_sql, out)
    finally:
        conn.close()


def main():
    args = parse_args()
    main_db = Path(args.main_sqlite).resolve()
    proxy_db = Path(args.proxy_sqlite).resolve()
    out_path = Path(args.out).resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)

    with out_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write("-- Foxaria SQLite -> MySQL export\n")
        out.write("SET NAMES utf8mb4;\n")
        out.write("SET FOREIGN_KEY_CHECKS=0;\n")
        out.write(f"CREATE DATABASE IF NOT EXISTS {quote_ident(args.database)} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n")
        out.write(f"USE {quote_ident(args.database)};\n")
        write_database_dump(main_db, "main Foxaria", out)
        write_database_dump(proxy_db, "FoxariaProxy", out)
        out.write("\nSET FOREIGN_KEY_CHECKS=1;\n")

    print(f"Exported MySQL dump: {out_path}")


if __name__ == "__main__":
    main()
