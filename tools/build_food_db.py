#!/usr/bin/env python3
"""Builds core/database/src/main/assets/food/food.db for Room's createFromAsset.

Source: tools/food_data.csv (approximate per-portion values in the style of the Indian Food
Composition Tables, IFCT 2017, and typical home recipes). Optionally merges a filtered
Open Food Facts CSV export (tab-separated, with code, product_name, energy-kcal_100g,
proteins_100g, carbohydrates_100g, fat_100g, countries_tags) for packaged foods sold in India:

    python3 tools/build_food_db.py [path/to/off-export.csv]

The table schema is read from Room's exported schema, so the asset always matches the entity.
"""
import csv, json, os, sqlite3, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA = os.path.join(ROOT, "core/database/schemas/app.loop.core.database.food.FoodDatabase/1.json")
OUT = os.path.join(ROOT, "core/database/src/main/assets/food/food.db")
CSV = os.path.join(ROOT, "tools/food_data.csv")

schema = json.load(open(SCHEMA))["database"]
entity = schema["entities"][0]
os.makedirs(os.path.dirname(OUT), exist_ok=True)
if os.path.exists(OUT):
    os.remove(OUT)
db = sqlite3.connect(OUT)
db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
rows = []
for i, r in enumerate(csv.DictReader(open(CSV, encoding="utf-8")), start=1):
    rows.append((i, r["name"], r["aliases"], r["unit"], float(r["kcal"]), float(r["grams"]),
                 float(r["protein"]), float(r["carbs"]), float(r["fat"]), None))

if len(sys.argv) > 1:  # Optional Open Food Facts subset, per 100 g.
    next_id = 100_000
    with open(sys.argv[1], encoding="utf-8") as f:
        for r in csv.DictReader(f, delimiter="\t"):
            if "en:india" not in (r.get("countries_tags") or ""):
                continue
            try:
                kcal = float(r["energy-kcal_100g"]) / 100
            except (KeyError, ValueError):
                continue
            name = (r.get("product_name") or "").strip()
            code = (r.get("code") or "").strip()
            if not name or not code:
                continue
            f100 = lambda k: float(r.get(k) or 0) / 100
            rows.append((next_id, name, "", "GRAM", kcal, 1.0, f100("proteins_100g"),
                         f100("carbohydrates_100g"), f100("fat_100g"), code))
            next_id += 1

db.executemany("INSERT INTO food VALUES (?,?,?,?,?,?,?,?,?,?)", rows)
db.execute(f"PRAGMA user_version = {schema['version']}")
db.commit()
db.execute("VACUUM")
db.close()
print(f"Wrote {len(rows)} foods to {OUT}")
