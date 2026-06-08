from __future__ import annotations

import re

_BRACKETS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
_WHITESPACE = re.compile(r"\s+")
_UNIT_REGEX = re.compile(r"\d+\s*(?:mg|밀리그[램람]|밀리|밀|mcg|ug|MG)", re.IGNORECASE)
_KOREAN_PREFIX = re.compile(r"^([가-힣]+)")
_ENGLISH_WORD = re.compile(r"[A-Za-z]+")

# 식약처 등록 제조사 prefix (OCR 오인식 variant 포함)
MANUFACTURER_PREFIXES: frozenset[str] = frozenset({
    # 종근당 계열 (OCR typo 포함)
    "종근당", "중근당", "종근당건강", "종근당바이오",
    # 한미약품
    "한미", "한미약품",
    # 일동제약
    "일동", "일동제약",
    # 대웅제약
    "대웅", "대웅제약", "대웅바이오",
    # 유한양행
    "유한", "유한양행",
    # CJ헬스케어
    "씨제이", "씨제이헬스케어",
    # 동아에스티
    "동아", "동아에스티", "동아에스앤씨",
    # 보령제약
    "보령", "보령제약",
    # 한독
    "한독", "한독약품",
    # 녹십자
    "녹십자", "녹십자약품", "녹십자홀딩스",
    # 광동제약
    "광동", "광동제약",
    # 삼진제약
    "삼진", "삼진제약",
    # 명문제약
    "명문", "명문제약",
    # 태극제약
    "태극", "태극제약",
    # 한국화이자
    "한국화이자", "화이자",
    # 한국노바티스
    "한국노바티스", "노바티스",
    # 한국아스트라제네카
    "한국아스트라제네카", "아스트라제네카",
    # 한국얀센
    "한국얀센", "얀센",
    # 한국로슈
    "한국로슈", "로슈",
    # 한국MSD
    "한국엠에스디", "엠에스디",
    # 한국릴리
    "한국릴리", "릴리",
    # 한국베링거인겔하임
    "한국베링거인겔하임", "베링거인겔하임",
    # 한국바이엘
    "한국바이엘", "바이엘",
    # 한국산도스
    "한국산도스", "산도스",
    # 환인제약
    "환인", "환인제약",
    # 부광약품
    "부광", "부광약품",
    # 동화약품
    "동화", "동화약품",
    # 경동제약
    "경동", "경동제약",
    # 구주제약
    "구주", "구주제약",
    # 하나제약
    "하나제약",
    # 제일약품
    "제일", "제일약품",
})

# 정렬: 긴 prefix 우선 매칭
_SORTED_PREFIXES: list[str] = sorted(MANUFACTURER_PREFIXES, key=len, reverse=True)


def strip_manufacturer_prefix(name: str) -> str:
    """제조사명 prefix 제거. 제거 후 2자 미만이면 원본 반환."""
    for prefix in _SORTED_PREFIXES:
        if name.startswith(prefix):
            remainder = name[len(prefix):]
            if len(remainder) >= 2:
                return remainder
    return name


def normalize_for_cascade(name: str) -> str:
    """제조사 strip → normalize_drug_name 순서로 cascade 검색용 정규화."""
    stripped = strip_manufacturer_prefix(name)
    normalized = normalize_drug_name(stripped)
    if normalized:
        return normalized
    return normalize_drug_name(name) or name


def normalize_drug_name(raw: str) -> str:
    """OCR로 받은 약 이름을 DB 매칭용으로 정규화."""
    name = _BRACKETS.sub("", raw)
    name = _UNIT_REGEX.sub("", name)
    name = _WHITESPACE.sub(" ", name).strip()
    return name


def first_token(name: str) -> str | None:
    """정규화 후 한글 prefix (영문/숫자/단위 제외)."""
    normalized = normalize_drug_name(name)
    if not normalized:
        return None
    match = _KOREAN_PREFIX.match(normalized)
    return match.group(1) if match else None


def first_english_token(name: str) -> str | None:
    """정규화 후 첫 영문 단어 (성분명 검색용)."""
    normalized = normalize_drug_name(name)
    if not normalized:
        return None
    match = _ENGLISH_WORD.search(normalized)
    return match.group(0) if match else None
