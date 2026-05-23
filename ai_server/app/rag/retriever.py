from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class RetrievedDrug:
    kd_code: str
    name: str
    efficacy: str | None
    dosage: str | None
    main_ingr: str | None

    def to_context_block(self) -> str:
        lines = [f"- 약품명: {self.name} (kdCode={self.kd_code})"]
        if self.main_ingr:
            lines.append(f"  주성분: {self.main_ingr}")
        if self.efficacy:
            lines.append(f"  효능: {self.efficacy}")
        if self.dosage:
            lines.append(f"  용법: {self.dosage}")
        return "\n".join(lines)


class DrugRetriever(Protocol):
    async def search(self, query: str, top_k: int) -> list[RetrievedDrug]:
        ...
