from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal

import jamotools

_DOSE_REGEX = re.compile(
    r"(?P<amount>\d+(?:\.\d+)?)\s*(?P<unit>mg|mcg|µg|g|ml|IU|밀리그?[램람]|마이크로그램)",
    re.IGNORECASE,
)
_FORM_KEYWORDS = ["정", "캅셀", "캡슐", "시럽", "현탁액", "연고", "크림", "주사", "패치", "산", "과립"]
_NAME_PREFIX = re.compile(r"^[가-힣A-Za-z][가-힣A-Za-z\-]*")

VALID_UNITS = {"mg", "mcg", "µg", "g", "ml", "IU"}


@dataclass(frozen=True)
class ParsedItem:
    raw: str
    name: str
    dose_amount: Decimal | None
    dose_unit: str | None
    form: str | None
    name_jamo: str
    is_valid: bool
    validation_errors: list[str]


def parse_drug_item(raw: str) -> ParsedItem:
    dose_amount, dose_unit, after_dose = _extract_dose(raw)
    name = _extract_name(after_dose)
    form = _detect_form(name)
    name_jamo = jamotools.split_syllables(name)
    is_valid, validation_errors = _validate(name, dose_unit)
    return ParsedItem(
        raw=raw,
        name=name,
        dose_amount=dose_amount,
        dose_unit=dose_unit,
        form=form,
        name_jamo=name_jamo,
        is_valid=is_valid,
        validation_errors=validation_errors,
    )


def to_jamo(text: str) -> str:
    """한글 → 자모 풀어쓰기. 영문/숫자는 그대로."""
    return jamotools.split_syllables(text)


def _extract_dose(text: str) -> tuple[Decimal | None, str | None, str]:
    match = _DOSE_REGEX.search(text)
    if not match:
        return None, None, text
    amount = Decimal(match.group("amount"))
    unit = _normalize_unit(match.group("unit"))
    cleaned = (text[: match.start()] + text[match.end() :]).strip()
    return amount, unit, cleaned


def _normalize_unit(raw: str) -> str:
    raw_l = raw.lower()
    if "밀리그" in raw_l:
        return "mg"
    if "마이크로" in raw_l:
        return "mcg"
    if raw_l == "µg":
        return "mcg"
    if raw_l == "iu":
        return "IU"
    return raw_l


def _extract_name(text: str) -> str:
    cleaned = re.sub(r"\([^)]*\)", "", text).strip()
    match = _NAME_PREFIX.match(cleaned)
    return match.group(0) if match else cleaned[:50]


def _detect_form(name: str) -> str | None:
    for kw in _FORM_KEYWORDS:
        if kw in name:
            return kw
    return None


def _validate(name: str, dose_unit: str | None) -> tuple[bool, list[str]]:
    errors: list[str] = []
    if len(name) < 2:
        errors.append("name_too_short")
    if dose_unit and dose_unit not in VALID_UNITS:
        errors.append(f"unknown_unit:{dose_unit}")
    return len(errors) == 0, errors
