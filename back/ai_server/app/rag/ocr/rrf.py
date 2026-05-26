from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum

RRF_K = 60


@dataclass
class Candidate:
    item_seq: str
    name: str
    dose_amount: Decimal | None
    dose_unit: str | None
    form: str | None
    alias_source: str | None
    name_jamo: str | None
    exact_rank: int | None = None
    trgm_rank: int | None = None
    jamo_rank: int | None = None
    vector_rank: int | None = None
    rrf_score: float = 0.0
    final_score: float = 0.0


def fuse_rrf(
    retriever_results: dict[str, list[Candidate]],
    k: int = RRF_K,
) -> list[Candidate]:
    score_map: dict[str, Candidate] = {}
    for retriever_name, candidates in retriever_results.items():
        for rank, c in enumerate(candidates):
            existing = score_map.setdefault(c.item_seq, c)
            existing.rrf_score += 1.0 / (k + rank + 1)
            setattr(existing, f"{retriever_name}_rank", rank + 1)
    return sorted(score_map.values(), key=lambda x: -x.rrf_score)


class MatchDecisionType(str, Enum):
    AUTO = "AUTO"
    CONFIRM = "CONFIRM"
    MANUAL = "MANUAL"


@dataclass(frozen=True)
class MatchDecision:
    type: MatchDecisionType
    primary: Candidate | None
    options: list[Candidate]
    reason: str
