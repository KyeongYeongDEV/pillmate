from __future__ import annotations

import Levenshtein

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate

DOSE_MATCH_BONUS: float = 0.5
DOSE_MISMATCH_PENALTY: float = -0.5
FORM_MATCH_BONUS: float = 0.2
ALIAS_PRODUCT_BONUS: float = 0.1
ALIAS_INGREDIENT_BONUS: float = 0.05
ALIAS_USER_BONUS: float = 0.03
JAMO_PENALTY_PER_CHAR: float = 0.05


class DomainReranker:
    def rerank(self, parsed: ParsedItem, candidates: list[Candidate]) -> list[Candidate]:
        for c in candidates:
            c.final_score = (
                c.rrf_score
                + self._dose_score(parsed, c)
                + self._form_score(parsed, c)
                + self._alias_score(c)
                + self._jamo_score(parsed, c)
            )
        return sorted(candidates, key=lambda x: -x.final_score)

    def _dose_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if not parsed.dose_amount or not c.dose_amount:
            return 0.0
        if parsed.dose_amount == c.dose_amount and parsed.dose_unit == c.dose_unit:
            return DOSE_MATCH_BONUS
        return DOSE_MISMATCH_PENALTY

    def _form_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if parsed.form and parsed.form == c.form:
            return FORM_MATCH_BONUS
        return 0.0

    def _alias_score(self, c: Candidate) -> float:
        if c.alias_source == "product":
            return ALIAS_PRODUCT_BONUS
        if c.alias_source == "ingredient":
            return ALIAS_INGREDIENT_BONUS
        if c.alias_source == "user":
            return ALIAS_USER_BONUS
        return 0.0

    def _jamo_score(self, parsed: ParsedItem, c: Candidate) -> float:
        if not parsed.name_jamo or not c.name_jamo:
            return 0.0
        distance = Levenshtein.distance(parsed.name_jamo, c.name_jamo)
        return -distance * JAMO_PENALTY_PER_CHAR
