from __future__ import annotations

import re

_BRACKETS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
_WHITESPACE = re.compile(r"\s+")
_UNIT_MG_ENG = re.compile(r"(\d+)\s*mg\b", re.IGNORECASE)
_LEADING_WORD = re.compile(r"^([가-힣A-Za-z]+)")


def normalize_drug_name(raw: str) -> str:
    """OCR로 받은 약 이름을 DB 매칭용으로 정규화."""
    name = _BRACKETS.sub("", raw)
    name = _UNIT_MG_ENG.sub(r"\1밀리그람", name)
    name = _WHITESPACE.sub(" ", name).strip()
    return name


def first_token(name: str) -> str | None:
    """정규화 후 첫 의미 토큰 (공백/숫자 직전까지)."""
    normalized = normalize_drug_name(name)
    if not normalized:
        return None
    match = _LEADING_WORD.match(normalized)
    return match.group(1) if match else None
