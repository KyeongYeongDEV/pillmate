"""Unit tests for build_product_aliases.py — alias generation (no DB)."""
from __future__ import annotations

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from build_product_aliases import build_aliases_for_product


def test_full_name_alias_confidence_100():
    aliases = build_aliases_for_product("타이레놀500mg정", "200001234")
    full = next((a for a in aliases if a["alias"] == "타이레놀500mg정"), None)
    assert full is not None
    assert full["confidence"] == 100
    assert full["source"] == "product"


def test_dose_variant_alias_confidence_85():
    aliases = build_aliases_for_product("타이레놀500mg정", "200001234")
    dose_stripped = next(
        (a for a in aliases if "타이레놀" in a["alias"] and "500mg" not in a["alias"]),
        None,
    )
    assert dose_stripped is not None, "dose-stripped variant must exist"
    assert dose_stripped["confidence"] == 85


def test_no_dose_no_duplicate_alias():
    aliases = build_aliases_for_product("타이레놀정", "200001234")
    alias_texts = [a["alias"] for a in aliases]
    assert len(alias_texts) == len(set(alias_texts)), "alias texts must be unique"
    assert "타이레놀정" in alias_texts


def test_alias_jamo_field_present():
    aliases = build_aliases_for_product("타이레놀500mg정", "200001234")
    for a in aliases:
        assert "alias_jamo" in a, "every alias must have alias_jamo"
