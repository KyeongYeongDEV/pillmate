from __future__ import annotations

import re

_BRACKETS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
_WHITESPACE = re.compile(r"\s+")
_UNIT_REGEX = re.compile(r"\d+\s*(?:mg|밀리그[램람]|MG)\b", re.IGNORECASE)
_KOREAN_PREFIX = re.compile(r"^([가-힣]+)")
_ENGLISH_WORD = re.compile(r"[A-Za-z]+")


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
