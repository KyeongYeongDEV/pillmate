from __future__ import annotations

import re
from decimal import Decimal

from app.rag.ocr.parser import ParsedItem
from app.rag.ocr.rrf import Candidate, MatchDecision, MatchDecisionType

ABS_THRESHOLD = Decimal("0.70")
MARGIN_THRESHOLD = Decimal("0.05")
DOSE_TOLERANCE = Decimal("0.10")

_DOSE_RE = re.compile(
    r"(\d+(?:\.\d+)?)\s*(?:mg|밀리그램|밀리그람|밀리그|밀리|mcg|µg|ug)",
    re.IGNORECASE,
)


class MatchDecider:
    def decide(self, parsed: ParsedItem, ranked: list[Candidate]) -> MatchDecision:
        if not ranked:
            return MatchDecision(
                type=MatchDecisionType.MANUAL,
                primary=None,
                options=[],
                reason="no_match",
            )

        top1 = ranked[0]
        top2 = ranked[1] if len(ranked) >= 2 else None

        if Decimal(str(top1.final_score)) < ABS_THRESHOLD:
            return MatchDecision(
                type=MatchDecisionType.MANUAL,
                primary=None,
                options=ranked[:3],
                reason="low_score",
            )

        if top2 and self._margin_too_small(top1, top2):
            return MatchDecision(
                type=MatchDecisionType.CONFIRM,
                primary=top1,
                options=ranked[:3],
                reason="ambiguous",
            )

        if not parsed.dose_amount:
            variants = self._dose_variants(ranked, top1.name)
            if len(variants) > 1:
                return MatchDecision(
                    type=MatchDecisionType.CONFIRM,
                    primary=top1,
                    options=variants,
                    reason="dose_unknown",
                )

        # OCR 용량 ≠ 후보 약품명 용량 → 사용자 확인 필수 (의료 안전)
        if parsed.dose_amount and self._dose_mismatch(parsed.dose_amount, top1.name):
            return MatchDecision(
                type=MatchDecisionType.CONFIRM,
                primary=top1,
                options=ranked[:3],
                reason="dose_mismatch",
            )

        return MatchDecision(
            type=MatchDecisionType.AUTO,
            primary=top1,
            options=[top1],
            reason="confident",
        )

    @staticmethod
    def _margin_too_small(top1: Candidate, top2: Candidate) -> bool:
        margin = Decimal(str(top1.final_score)) - Decimal(str(top2.final_score))
        return margin < MARGIN_THRESHOLD

    @staticmethod
    def _dose_variants(candidates: list[Candidate], name: str) -> list[Candidate]:
        seen: dict[tuple, Candidate] = {}
        for c in candidates:
            if c.name == name and c.dose_amount is not None:
                key = (c.dose_amount, c.dose_unit)
                seen.setdefault(key, c)
        return list(seen.values())

    @staticmethod
    def _dose_mismatch(query_dose: Decimal, candidate_name: str) -> bool:
        """OCR 용량과 후보 약품명의 용량 불일치 → True (CONFIRM 필요).

        후보에 용량 미기재 시에도 True: 검증 불가 = 보수적 CONFIRM.
        """
        m = _DOSE_RE.search(candidate_name)
        if m is None:
            return True  # 후보에 용량 없음 → 검증 불가 → CONFIRM
        candidate_dose = Decimal(m.group(1))
        ratio = abs(candidate_dose - query_dose) / query_dose
        return ratio > DOSE_TOLERANCE
