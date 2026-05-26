"""RED tests for scripts.lib.mfds_merge.merge_drug_record.

머지 우선순위: easy(e약은요) > permit(제품허가) > ident(낱알식별).
None 은 덮어쓰지 않는다. 모두 None 인 키는 결과 dict 에서 제외.
"""
from __future__ import annotations

from datetime import date

from scripts.lib.mfds_merge import merge_drug_record


def _easy(**overrides):
    base = {
        "itemSeq": "200800123",
        "itemName": "이지명",
        "entpName": "이지회사",
        "efcyQesitm": "두통 완화",
        "useMethodQesitm": "1일 3회",
        "atpnWarnQesitm": "경고",
        "atpnQesitm": "주의",
        "intrcQesitm": "상호작용",
        "seQesitm": "부작용",
        "depositMethodQesitm": "실온 보관",
        "itemImage": "https://e.example/img.jpg",
        "openDe": "20120101",
        "updateDe": "20240101",
        "bizrno": "1234567890",
    }
    base.update(overrides)
    return base


def _ident(**overrides):
    base = {
        "ITEM_SEQ": "200800123",
        "ITEM_NAME": "이덴트명",
        "ENTP_NAME": "이덴트회사",
        "DRUG_SHAPE": "원형",
        "COLOR_CLASS1": "흰색",
        "COLOR_CLASS2": "분홍",
        "LINE_FRONT": "-",
        "LINE_BACK": "+",
        "MARK_CODE_FRONT": "ABC",
        "MARK_CODE_BACK": "XYZ",
        "PRINT_FRONT": None,
        "PRINT_BACK": None,
        "CHART": "원형 흰색 ABC|XYZ",
        "ITEM_IMAGE": "https://i.example/img.jpg",
        "FORM_CODE_NAME": "정제",
        "ETC_OTC_NAME": "일반의약품",
        "ITEM_PERMIT_DATE": "20080820",
        "CLASS_NAME": "해열진통소염제",
        "BIZRNO": "1234567890",
    }
    base.update(overrides)
    return base


def _permit(**overrides):
    base = {
        "ITEM_SEQ": "200800123",
        "ITEM_NAME": "퍼밋명",
        "ENTP_NAME": "퍼밋회사",
        "PRDUCT_PRMISN_NO": "200800123",
        "ITEM_PERMIT_DATE": "20080820",
        "CANCEL_DATE": None,
        "CANCEL_NAME": "정상",
        "SPCLTY_PBLC": "일반의약품",
        "PRDUCT_TYPE": "[01140]해열진통소염제",
        "ITEM_INGR_NAME": "아세트아미노펜 500mg",
        "PERMIT_KIND_CODE": "허가",
        "BIZRNO": "1234567890",
    }
    base.update(overrides)
    return base


def test_merge_all_three():
    """같은 itemSeq 가 3 API 모두에 존재 → 모든 컬럼이 채워진다."""
    row = merge_drug_record(_easy(), _ident(), _permit())

    assert row["kd_code"] == "200800123"
    # 이름: easy 우선
    assert row["name"] == "이지명"
    # 효능: easy 만 가짐
    assert row["efficacy"] == "두통 완화"
    # 모양: ident 만 가짐
    assert row["shape_class"] == "원형"
    # 허가번호: permit 만 가짐
    assert row["permit_no"] == "200800123"
    # 주성분: permit
    assert row["main_ingr"] == "아세트아미노펜 500mg"
    # 약효분류: easy 없음, permit 우선
    assert row["class_name"] == "[01140]해열진통소염제"


def test_merge_easy_only():
    """e약은요에만 있는 itemSeq → ident/permit None."""
    row = merge_drug_record(_easy(), None, None)

    assert row["kd_code"] == "200800123"
    assert row["name"] == "이지명"
    assert row["efficacy"] == "두통 완화"
    # 낱알 정보 없음 → 키 자체 제외
    assert "shape_class" not in row
    assert "color_class" not in row
    # 허가 정보 없음
    assert "permit_no" not in row


def test_merge_ident_only():
    """낱알식별에만 있는 itemSeq → easy/permit None."""
    row = merge_drug_record(None, _ident(), None)

    assert row["kd_code"] == "200800123"
    assert row["name"] == "이덴트명"
    assert row["shape_class"] == "원형"
    assert row["color_class"] == "흰색,분홍"
    # easy 의 효능 등은 없음
    assert "efficacy" not in row
    assert "warning" not in row


def test_priority_easy_overrides_permit():
    """같은 컬럼이 easy 와 permit 둘 다 → easy 채택."""
    row = merge_drug_record(_easy(itemName="이지명우선"), None, _permit(ITEM_NAME="퍼밋명"))
    assert row["name"] == "이지명우선"


def test_priority_none_does_not_override():
    """easy=None 인 컬럼은 permit 값이 채워야 한다."""
    easy = _easy(itemName=None)
    row = merge_drug_record(easy, None, _permit(ITEM_NAME="퍼밋명"))
    assert row["name"] == "퍼밋명"


def test_date_yyyymmdd_to_date():
    """ITEM_PERMIT_DATE 'YYYYMMDD' → date 객체."""
    row = merge_drug_record(None, None, _permit(ITEM_PERMIT_DATE="20080820"))
    assert row["permit_date"] == date(2008, 8, 20)


def test_color_concat():
    """COLOR_CLASS1+COLOR_CLASS2 → 콤마 join, NULL 무시."""
    row1 = merge_drug_record(None, _ident(COLOR_CLASS1="흰색", COLOR_CLASS2="분홍"), None)
    assert row1["color_class"] == "흰색,분홍"

    row2 = merge_drug_record(None, _ident(COLOR_CLASS1="흰색", COLOR_CLASS2=None), None)
    assert row2["color_class"] == "흰색"

    row3 = merge_drug_record(None, _ident(COLOR_CLASS1=None, COLOR_CLASS2="분홍"), None)
    assert row3["color_class"] == "분홍"
