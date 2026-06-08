"""
영문 brand/INN alias 시드 스크립트 — drug_alias 테이블 INSERT only (DB safety 규정 준수)

실행: uv run python scripts/seed_english_aliases.py
환경변수: POSTGRES_DSN (기본값: postgresql://pillmate:pillmate_local@localhost:5433/pillmate)
"""
from __future__ import annotations

import asyncio
import os

POSTGRES_DSN = os.getenv(
    "POSTGRES_DSN",
    "postgresql://pillmate:pillmate_local@localhost:5433/pillmate",
)

# (alias, item_seq, source, confidence)
# source: 'product' | 'ingredient' | 'bundle' | 'user'
ENGLISH_ALIASES: list[tuple[str, str, str, int]] = [
    # English brand names
    ("Tylenol", "KD001", "product", 95),
    ("Aspirin", "KD002", "product", 95),
    # English INN names → representative product item_seq
    ("Acetaminophen", "KD001", "ingredient", 90),
    ("Amoxicillin", "197900574", "ingredient", 90),       # 일동아목시실린수화물캡슐250mg
    ("Ibuprofen", "199501694", "ingredient", 90),          # 이바펜400mg(이부프로펜)
    ("Metformin", "202000352", "ingredient", 90),           # 유한메트포르민염산염정500mg
    ("Amlodipine", "200704767", "ingredient", 90),          # 동화암로디핀베실산염정5mg
    ("Rosuvastatin", "202002664", "ingredient", 90),        # 로수바엘정10mg(로수바스타틴칼슘)
    ("Omeprazole", "200404039", "ingredient", 90),          # 오프졸캡슐(오메프라졸)
    ("Cetirizine", "199803133", "ingredient", 90),          # 나노텍정(세티리진염산염)
    ("Clarithromycin", "201307946", "ingredient", 90),      # 영일클래리스로마이신정250mg
    ("Gabapentin", "201505159", "ingredient", 90),          # 가바로닌캡슐300mg(가바펜틴)
    ("Zolpidem", "200604149", "ingredient", 90),            # 코닉스정(주석산졸피뎀)
    ("Loperamide", "198400816", "ingredient", 90),          # 대화염산로페라미드캡슐
    ("Glimepiride", "201402847", "ingredient", 90),         # 글리올정4mg(글리메피리드)
    ("Mosapride", "201306230", "ingredient", 90),           # 모사드린정(모사프리드시트르산염이수화물)
    # Korean category aliases → representative product
    ("항히스타민제", "199803133", "ingredient", 80),        # 세티리진
    ("항히스타민", "199803133", "ingredient", 80),
    ("혈압약", "200704767", "ingredient", 80),              # 암로디핀
    ("위장약", "200404039", "ingredient", 80),              # 오메프라졸
    ("당뇨약", "202000352", "ingredient", 80),              # 메트포르민
    ("혈당강하제", "202000352", "ingredient", 80),
    ("콜레스테롤약", "202002664", "ingredient", 80),        # 로수바스타틴
    ("진통소염제", "199501694", "ingredient", 80),          # 이부프로펜
    ("수면제", "200604149", "ingredient", 80),              # 졸피뎀
    ("항생제", "197900574", "ingredient", 80),              # 아목시실린
    ("항생체", "197900574", "ingredient", 75),              # 항생제 오기
]

_INSERT_SQL = """
INSERT INTO drug_alias (alias, item_seq, source, confidence, is_verified)
VALUES ($1, $2, $3, $4, true)
ON CONFLICT (alias, item_seq) DO NOTHING
"""


async def seed(dsn: str) -> None:
    import asyncpg

    pool = await asyncpg.create_pool(dsn)
    try:
        async with pool.acquire() as conn:
            inserted = 0
            skipped = 0
            for alias, item_seq, source, confidence in ENGLISH_ALIASES:
                result = await conn.execute(_INSERT_SQL, alias, item_seq, source, confidence)
                if result == "INSERT 0 1":
                    inserted += 1
                else:
                    skipped += 1
            print(f"Seed complete: {inserted} inserted, {skipped} already exist")
    finally:
        await pool.close()


if __name__ == "__main__":
    asyncio.run(seed(POSTGRES_DSN))
