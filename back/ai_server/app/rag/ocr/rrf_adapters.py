"""
RrfMatcher 운영 와이어링용 retriever 어댑터.

ExactSinglePort / MultiRetrieverPort 프로토콜을 구현하여
기존 DB 검색 클래스를 RrfMatcher 에 연결한다.

모든 어댑터는 MatchCandidate → Candidate 변환을 담당한다.

Gate A 추가 (2026-06-13):
  TokenIlikeMultiAdapter  — first_token ILIKE (공백·무단위 입력)
  PrefixRelaxMultiAdapter — prefix 4→3글자 ILIKE (오탈자·장음 변이)
  IngredientMultiAdapter  — 영문 성분명·alias 검색 (Tylenol→타이레놀)

Gate A+ 추가 (2026-06-13):
  StrongExactAdapter — dose_amount 무관 강화 exact 단축
    cascade: normalize_for_cascade → salt+form strip → first_token → prefix[:4]
    disambiguation: main_name 우선 → dose 일치 → 최단 이름
    의료 안전: 완전 모호(다른 약류) 시 None 반환 → RRF 위임
"""
from __future__ import annotations

import logging
import re
from decimal import Decimal
from typing import Any

import jamotools

from app.rag.ocr.fuzzy_search import JamoFuzzyRanker, TrigramFuzzySearch
from app.rag.ocr.normalizer import (
    first_english_token,
    first_token,
    normalize_for_cascade,
)
from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate

logger = logging.getLogger(__name__)

_EXACT_SQL = """
SELECT kd_code, name, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE '%' || $1 || '%'
ORDER BY
  CASE WHEN name ILIKE $1        THEN 0
       WHEN name ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  length(name)
LIMIT 5
"""

_ILIKE_MULTI_SQL = """
SELECT kd_code, name, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE '%' || $1 || '%'
ORDER BY
  CASE WHEN name ILIKE $1        THEN 0
       WHEN name ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  length(name)
LIMIT $2
"""

_ILIKE_MULTI_TOP = 10
_VECTOR_TOP_K = 10
_TOKEN_ILIKE_TOP = 10
_PREFIX_ILIKE_TOP = 10
_INGREDIENT_MULTI_TOP = 10

_ENGLISH_UNIT_TOKENS: frozenset[str] = frozenset({"mg", "ml", "mcg", "ug", "g", "iu", "mg/ml"})

_PREFIX_ILIKE_SQL = """
SELECT kd_code, name, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE $1 || '%'
ORDER BY length(name)
LIMIT $2
"""

_INGREDIENT_MULTI_SQL = """
SELECT d.kd_code, d.name, d.name_jamo
FROM drugs d
WHERE d.status = 'ACTIVE'
  AND (
    d.ingredient ILIKE '%' || $1 || '%'
    OR EXISTS (
      SELECT 1
      FROM drug_alias a
      JOIN drug_master dm ON dm.item_seq = a.item_seq
      WHERE dm.legacy_drug_id = d.id
        AND (a.alias ILIKE '%' || $1 || '%' OR $1 ILIKE '%' || a.alias || '%')
    )
  )
ORDER BY
  CASE WHEN d.ingredient ILIKE $1 THEN 0
       WHEN d.ingredient ILIKE $1 || '%' THEN 1
       ELSE 2 END,
  length(d.name)
LIMIT $2
"""


def _dose_in_name(parsed: ParsedItem, drug_name: str) -> bool:
    """parsed.dose_amount が drug_name 에 포함되는지 확인."""
    if not parsed.dose_amount:
        return True
    amount = parsed.dose_amount
    dose_variants = [
        f"{amount}밀리그램",
        f"{amount}밀리",
        f"{amount}mg",
        f"{amount} mg",
        f"{int(amount)}밀리그램",
        f"{int(amount)}밀리",
        f"{int(amount)}mg",
        f"{amount}",
    ]
    name_lower = drug_name.lower()
    return any(v.lower() in name_lower for v in dose_variants)


_FORM_SUFFIX_RE = re.compile(r"(?:정|캡슐|시럽|연고|주사|과립|액|겔|크림|패치|점안)$")


def _has_form_suffix(query: str) -> bool:
    """쿼리가 제형 접미사로 끝나면 True — 브랜드명 수준 쿼리 판단."""
    return bool(_FORM_SUFFIX_RE.search(query))


def _prefix_compatible(query: str, drug_name: str) -> bool:
    """쿼리와 drug_name 이 접두 포함 관계인지 확인.

    허용:
      - drug_name 주성분명이 query 로 시작 (query 가 이름의 접두사)
      - query 가 drug_name 주성분명으로 시작 (이름이 쿼리보다 짧음)
      - 제조사 접두사(2~4자) 제거 후 drug_name 이 query 로 시작

    거부 (의료 안전):
      - query 가 drug_name 의 가운데/끝에서만 나타남
        예) '이세틸정' ⊂ '케이세틸정' (offset=1 → 2 미만 → 거부)
    """
    main = drug_name.split("(")[0].strip()
    if main.startswith(query) or query.startswith(main):
        return True
    for offset in range(2, 5):
        if len(main) > offset and main[offset:].startswith(query):
            return True
    return False


def _row_to_candidate(row: Any) -> Candidate:
    name_jamo = row["name_jamo"] or jamotools.split_syllables(row["name"])
    return Candidate(
        item_seq=row["kd_code"],
        name=row["name"],
        dose_amount=None,
        dose_unit=None,
        form=None,
        alias_source=None,
        name_jamo=name_jamo,
    )


class ExactIlikeAdapter:
    """ExactSinglePort — 함량 포함 prefix ILIKE + dose 검증."""

    def __init__(self, pool: Any) -> None:
        self._pool = pool

    async def search_single(self, parsed: ParsedItem) -> Candidate | None:
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_EXACT_SQL, parsed.name)
        for row in rows:
            if _dose_in_name(parsed, row["name"]):
                return _row_to_candidate(row)
        return None


class IlikeMultiAdapter:
    """MultiRetrieverPort — 복수 ILIKE 후보 반환."""

    def __init__(self, pool: Any, top: int = _ILIKE_MULTI_TOP) -> None:
        self._pool = pool
        self._top = top

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_ILIKE_MULTI_SQL, parsed.name, self._top)
        return [_row_to_candidate(r) for r in rows]


class TrigramMultiAdapter:
    """MultiRetrieverPort — pg_trgm + Jamo 유사도 검색."""

    def __init__(
        self,
        trgm_search: TrigramFuzzySearch,
        ranker: JamoFuzzyRanker,
    ) -> None:
        self._trgm = trgm_search
        self._ranker = ranker

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        fuzzy_hits = await self._trgm.search(parsed.name)
        if not fuzzy_hits:
            return []
        ranked = self._ranker.rerank(parsed.name_jamo, fuzzy_hits, prefix_match=True)
        return [
            Candidate(
                item_seq=fc.kd_code,
                name=fc.name,
                dose_amount=None,
                dose_unit=None,
                form=None,
                alias_source=None,
                name_jamo=fc.name_jamo,
            )
            for fc in ranked
        ]


class VectorMultiAdapter:
    """MultiRetrieverPort — pgvector 의미 유사도 검색."""

    def __init__(self, retriever: Any, top_k: int = _VECTOR_TOP_K) -> None:
        self._retriever = retriever
        self._top_k = top_k

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        results = await self._retriever.search(parsed.name, top_k=self._top_k)
        return [
            Candidate(
                item_seq=r.kd_code,
                name=r.name,
                dose_amount=None,
                dose_unit=None,
                form=None,
                alias_source=None,
                name_jamo=jamotools.split_syllables(r.name),
            )
            for r in results
        ]


# ──────────────────────────────────────────────────────────
# Gate A 추가 어댑터 — legacy cascade fallback 이식 (2026-06-13)
# ──────────────────────────────────────────────────────────


class TokenIlikeMultiAdapter:
    """MultiRetrieverPort — first_token ILIKE (공백 분리·무단위 숫자 입력 처리).

    예: '타이레놀 500' → first_token='타이레놀' → ILIKE 검색
        '암로디핀5'   → first_token='암로디핀' → ILIKE 검색
    """

    def __init__(self, pool: Any, top: int = _TOKEN_ILIKE_TOP) -> None:
        self._pool = pool
        self._top = top

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        token = first_token(parsed.raw)
        if not token or token == parsed.name:
            return []
        async with self._pool.acquire() as conn:
            rows = await conn.fetch(_ILIKE_MULTI_SQL, token, self._top)
        return [_row_to_candidate(r) for r in rows]


class PrefixRelaxMultiAdapter:
    """MultiRetrieverPort — prefix 4→3글자 ILIKE (오탈자·복합 성분명 처리).

    예: '타이레늘정500' → first_token='타이레늘정' → prefix[:4]='타이레늘' → ILIKE '타이레늘%'
        (없으면) prefix[:3]='타이레' → ILIKE '타이레%' → 타이레놀... 계열 히트
        '오메프라졸장용캡슐' → prefix[:4]='오메프라' → 오메프라졸 계열 히트
    """

    def __init__(self, pool: Any, top: int = _PREFIX_ILIKE_TOP) -> None:
        self._pool = pool
        self._top = top

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        base = first_token(parsed.name) or parsed.name
        seen_kd: set[str] = set()
        candidates: list[Candidate] = []
        async with self._pool.acquire() as conn:
            for length in (4, 3):
                if len(base) < length:
                    continue
                prefix = base[:length]
                rows = await conn.fetch(_PREFIX_ILIKE_SQL, prefix, self._top)
                for r in rows:
                    if r["kd_code"] not in seen_kd:
                        seen_kd.add(r["kd_code"])
                        candidates.append(_row_to_candidate(r))
        return candidates


class IngredientMultiAdapter:
    """MultiRetrieverPort — 영문 성분명·alias 검색 (legacy cascade ingredient 이식).

    예: 'Tylenol'     → alias ILIKE '%Tylenol%' → 타이레놀500mg
        'Amoxicillin' → alias ILIKE '%Amoxicillin%' → 아목시실린 계열
    """

    def __init__(self, pool: Any, top: int = _INGREDIENT_MULTI_TOP) -> None:
        self._pool = pool
        self._top = top

    async def search(self, parsed: ParsedItem) -> list[Candidate]:
        queries = self._build_queries(parsed)
        if not queries:
            return []
        seen_kd: set[str] = set()
        candidates: list[Candidate] = []
        async with self._pool.acquire() as conn:
            for q in queries:
                rows = await conn.fetch(_INGREDIENT_MULTI_SQL, q, self._top)
                for r in rows:
                    if r["kd_code"] not in seen_kd:
                        seen_kd.add(r["kd_code"])
                        candidates.append(_row_to_candidate(r))
        return candidates

    def _build_queries(self, parsed: ParsedItem) -> list[str]:
        queries: list[str] = []
        eng = first_english_token(parsed.raw)
        if eng and eng.lower() not in _ENGLISH_UNIT_TOKENS and len(eng) >= 3:
            queries.append(eng)
        if parsed.name and any("가" <= c <= "힣" for c in parsed.name):
            queries.append(parsed.name)
        return queries


# ──────────────────────────────────────────────────────────────────────────────
# Gate A+ — StrongExactAdapter (2026-06-13)
# ──────────────────────────────────────────────────────────────────────────────

# 식약처 DB 약품명에서 salt/form 접미사를 반복 제거해 약품 핵심어를 추출.
# 긴 쪽부터 먼저 매칭하기 위해 순서 중요.
_SALT_FORM_RE = re.compile(
    r"(?:마그네슘삼수화물|마그네슘이수화물|삼수화물|이수화물|수화물"
    r"|구연산염|시트르산염수화물|시트르산염|타르타르산염|말레산염"
    r"|숙시산염|베실산염|황산염|인산염|인산|나트륨|칼슘|칼륨|주석산"
    r"|산화물|염산염|마그네슘"
    r"|장용캡슐|장용정|서방캡슐|서방정|속붕정|당의정"
    r"|연질캡슐|경질캡슐|장용|서방|속붕|당의|연질|경질"
    r"|캡슐|정|주사제|주사|주|액|시럽|과립|겔|크림|연고|패치|점안"
    r")+$"
)


def _strip_salt(name: str) -> str:
    """반복적으로 salt·form 접미사 제거. 결과가 2자 미만이면 직전 값 반환."""
    current = name
    while True:
        stripped = _SALT_FORM_RE.sub("", current).strip()
        if stripped == current or len(stripped) < 2:
            return current
        current = stripped


def _in_main_name(query: str, drug_name: str) -> bool:
    """query 가 drug_name 의 괄호 앞 주성분명 부분에 포함되는지."""
    main = drug_name.split("(")[0]
    return query in main


_STRONG_EXACT_SQL = """
SELECT kd_code, name, name_jamo
FROM drugs
WHERE status = 'ACTIVE'
  AND name ILIKE '%' || $1 || '%'
ORDER BY
  CASE
    WHEN name ILIKE $1           THEN 0
    WHEN name ILIKE $1 || '%'    THEN 1
    WHEN split_part(name,'(',1) ILIKE '%' || $1 || '%' THEN 2
    ELSE 3
  END,
  length(name)
LIMIT 15
"""

_STRONG_EXACT_MIN_LEN = 2
_STRONG_EXACT_PREFIX_MIN = 4


class StrongExactAdapter:
    """강화된 exact 단축 — dose_amount 유무 무관.

    query cascade (우선순위 순):
      1. normalize_for_cascade(parsed.name)  — 제조사 제거 + 단위 제거
      2. _strip_salt(cascade)                — salt/form 접미사 반복 제거
      3. first_token(parsed.name)            — 한글 첫 토큰
      4. stripped[:4] (require_main_hit=True) — prefix 4글자 (OCR 오탈자용)
         ※ prefix 는 짧고 모호해서 괄호 내 성분명 우연 매칭 위험 있음.
            main_name 에 포함될 때만 단축; 아니면 RRF 위임 (의료 안전).
         ※ cascade/stripped/token 은 INN 수준 특이도 → 괄호 내 허용.

    disambiguation (의료 안전):
      - prefix 쿼리: main_name 필수 (require_main_hit=True)
      - 나머지:    main_name 우선, 없으면 parenthetical 허용
      dose 일치 → 최단 이름 순.
    """

    def __init__(self, pool: Any) -> None:
        self._pool = pool

    async def search_single(self, parsed: ParsedItem) -> Candidate | None:
        """ExactSinglePort 인터페이스."""
        queries = self._build_queries(parsed)
        async with self._pool.acquire() as conn:
            for query, require_main_hit in queries:
                rows = await conn.fetch(_STRONG_EXACT_SQL, query)
                if rows:
                    best = self._pick_best(query, rows, parsed, require_main_hit)
                    if best is not None:
                        return best
        return None

    def _build_queries(self, parsed: ParsedItem) -> list[tuple[str, bool]]:
        """(query, require_main_hit) 쌍 목록 반환.

        require_main_hit=True  → 쿼리가 drug.name 괄호 앞 본명에 없으면 단축 금지.
        require_main_hit=False → 괄호 안 성분명 매칭 허용 (INN 수준 쿼리).
        """
        seen: set[str] = set()
        result: list[tuple[str, bool]] = []

        def _add(q: str | None, require_main_hit: bool = False) -> None:
            if q and len(q) >= _STRONG_EXACT_MIN_LEN and q not in seen:
                seen.add(q)
                result.append((q, require_main_hit))

        cascade = normalize_for_cascade(parsed.name)
        _add(cascade)

        stripped = _strip_salt(cascade) if cascade else None
        _add(stripped)

        _add(first_token(parsed.name))

        # prefix[:4]: OCR 오탈자 처리. 한글 입력만.
        # 짧아서 모호 → main_name 강제(require_main_hit=True).
        # 영문 전용 입력은 괄호 내 수출명 우연 매칭 방지.
        base = stripped or cascade or parsed.name or ""
        has_korean = any("가" <= c <= "힣" for c in parsed.name)
        if has_korean and len(base) >= _STRONG_EXACT_PREFIX_MIN + 1:
            _add(base[:_STRONG_EXACT_PREFIX_MIN], require_main_hit=True)

        return result

    @staticmethod
    def _pick_best(
        query: str, rows: list[Any], parsed: ParsedItem,
        require_main_hit: bool = False,
    ) -> Candidate | None:
        """의료 안전 disambiguation — 이름·용량 이중 검증.

        require_main_hit=True: 괄호 앞 main_name 에 없으면 None → RRF 위임.
        _prefix_compatible 강화: 브랜드명 쿼리(형태 접미사)가 후보명 가운데/끝에서만
          나타나면 None → RRF 위임 (이세틸정→케이세틸정 방지).
        dose 불일치: dose_hits 없으면 None → RRF 위임 (의료 안전).
        우선순위: prefix_compatible main_name → dose 일치 → 최단 이름.
        """
        candidates = [_row_to_candidate(r) for r in rows]

        prefix_hits = [c for c in candidates if _prefix_compatible(query, c.name)]
        main_hits = prefix_hits if prefix_hits else [
            c for c in candidates if _in_main_name(query, c.name)
        ]

        if require_main_hit and not prefix_hits:
            return None

        # 브랜드명 쿼리(형태 접미사 보유)가 후보명과 접두 포함 관계 없으면 단축 금지
        if _has_form_suffix(query) and not prefix_hits:
            return None

        pool = main_hits if main_hits else candidates

        if parsed.dose_amount:
            dose_hits = [c for c in pool if _dose_in_name(parsed, c.name)]
            if dose_hits:
                pool = dose_hits
            else:
                # 용량 명시했는데 매칭 없음 → 단축 금지, RRF 위임 (의료 안전)
                return None

        return pool[0] if pool else None
