"""Unit tests for build_drug_master_from_drugs.py — pure function tests (no DB)."""
from __future__ import annotations

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from build_drug_master_from_drugs import extract_dose_from_name, build_master_row


def test_extract_dose_mg():
    amount, unit = extract_dose_from_name("타이레놀500mg정")
    assert amount == "500"
    assert unit == "mg"


def test_extract_dose_missing():
    amount, unit = extract_dose_from_name("타이레놀정")
    assert amount is None
    assert unit is None


def test_extract_dose_decimal():
    amount, unit = extract_dose_from_name("세티리진0.5mg정")
    assert amount == "0.5"
    assert unit == "mg"


def test_build_master_row_sets_item_seq_and_legacy_id():
    row = build_master_row(
        drug_id=42,
        kd_code="200001234",
        name="타이레놀500mg정",
        ingredient=None,
        item_image=None,
    )
    assert row["item_seq"] == "200001234"
    assert row["product_name"] == "타이레놀500mg정"
    assert row["legacy_drug_id"] == 42
    assert row["source"] == "drugs"


def test_build_master_row_idempotent_same_conflict_key():
    row1 = build_master_row(drug_id=1, kd_code="X001", name="약A정", ingredient=None, item_image=None)
    row2 = build_master_row(drug_id=1, kd_code="X001", name="약A정", ingredient=None, item_image=None)
    assert row1["item_seq"] == row2["item_seq"]
    assert row1["product_name"] == row2["product_name"]
