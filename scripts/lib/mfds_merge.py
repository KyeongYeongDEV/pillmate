"""식약처 3 API 레코드 머지.

머지 우선순위: easy(e약은요) > permit(제품허가) > ident(낱알식별).
None 은 덮어쓰지 않는다. 모든 source 에 값이 없는 키는 결과에서 제외.
"""
from __future__ import annotations

from datetime import date, datetime
from typing import Any, Iterable, Mapping


def _norm(value: Any) -> Any:
    """빈 문자열·공백문자 → None 으로 정규화."""
    if value is None:
        return None
    if isinstance(value, str):
        stripped = value.strip()
        return stripped or None
    return value


def _parse_date(value: Any) -> date | None:
    v = _norm(value)
    if v is None:
        return None
    s = str(v)
    # 식약처 포맷: YYYYMMDD (날짜만)
    try:
        return datetime.strptime(s, "%Y%m%d").date()
    except ValueError:
        # YYYY-MM-DD 같은 변형 허용
        try:
            return datetime.strptime(s, "%Y-%m-%d").date()
        except ValueError:
            return None


def _normalize_etc_otc(value: Any) -> tuple[str | None, bool]:
    """ETC_OTC_NAME/SPCLTY_PBLC 정규화.

    실데이터: '전문의약품' / '일반의약품' / '전문,희귀' / '일반,희귀' 등.
    반환: (정규화 값, is_rare)
    """
    v = _norm(value)
    if v is None:
        return None, False
    s = str(v)
    is_rare = "희귀" in s
    if "전문" in s:
        return "전문의약품", is_rare
    if "일반" in s:
        return "일반의약품", is_rare
    return None, is_rare


def _join_color(c1: Any, c2: Any) -> Any:
    """COLOR_CLASS1 + COLOR_CLASS2 → '흰색,분홍' (NULL 무시)."""
    v1 = _norm(c1)
    v2 = _norm(c2)
    parts = [p for p in (v1, v2) if p]
    if not parts:
        return None
    return ",".join(parts)


def _first_non_none(values: Iterable[Any]) -> Any:
    for v in values:
        nv = _norm(v)
        if nv is not None:
            return nv
    return None


def _from_easy(easy: Mapping[str, Any]) -> dict[str, Any]:
    """e약은요 레코드 → drugs row 컬럼 dict."""
    return {
        "kd_code": _norm(easy.get("itemSeq")),
        "name": _norm(easy.get("itemName")),
        "company": _norm(easy.get("entpName")),
        "efficacy": _norm(easy.get("efcyQesitm")),
        "dosage": _norm(easy.get("useMethodQesitm")),
        "warning": _norm(easy.get("atpnWarnQesitm")),
        "precaution": _norm(easy.get("atpnQesitm")),
        "interaction": _norm(easy.get("intrcQesitm")),
        "side_effect": _norm(easy.get("seQesitm")),
        "storage_method": _norm(easy.get("depositMethodQesitm")),
        "item_image": _norm(easy.get("itemImage")),
        "open_de": _parse_date(easy.get("openDe")),
        "update_de": _parse_date(easy.get("updateDe")),
        "bizrno": _norm(easy.get("bizrno")),
    }


def _from_permit(permit: Mapping[str, Any]) -> dict[str, Any]:
    """제품허가 레코드 → drugs row 컬럼 dict."""
    etc_otc, is_rare = _normalize_etc_otc(permit.get("SPCLTY_PBLC"))
    row: dict[str, Any] = {
        "kd_code": _norm(permit.get("ITEM_SEQ")),
        "name": _norm(permit.get("ITEM_NAME")),
        "company": _norm(permit.get("ENTP_NAME")),
        "permit_no": _norm(permit.get("PRDUCT_PRMISN_NO")),
        "permit_date": _parse_date(permit.get("ITEM_PERMIT_DATE")),
        "cancel_date": _parse_date(permit.get("CANCEL_DATE")),
        "etc_otc": etc_otc,
        "class_name": _norm(permit.get("PRDUCT_TYPE")),
        "main_ingr": _norm(permit.get("ITEM_INGR_NAME")),
        "bizrno": _norm(permit.get("BIZRNO")),
    }
    if is_rare:
        row["is_rare"] = True
    return row


def _from_ident(ident: Mapping[str, Any]) -> dict[str, Any]:
    """낱알식별 레코드 → drugs row 컬럼 dict."""
    etc_otc, is_rare = _normalize_etc_otc(ident.get("ETC_OTC_NAME"))
    row: dict[str, Any] = {
        "kd_code": _norm(ident.get("ITEM_SEQ")),
        "name": _norm(ident.get("ITEM_NAME")),
        "company": _norm(ident.get("ENTP_NAME")),
        "shape_class": _norm(ident.get("DRUG_SHAPE")),
        "color_class": _join_color(ident.get("COLOR_CLASS1"), ident.get("COLOR_CLASS2")),
        "line_front": _norm(ident.get("LINE_FRONT")),
        "line_back": _norm(ident.get("LINE_BACK")),
        "mark_code_front": _first_non_none([ident.get("MARK_CODE_FRONT"), ident.get("PRINT_FRONT")]),
        "mark_code_back": _first_non_none([ident.get("MARK_CODE_BACK"), ident.get("PRINT_BACK")]),
        "chart": _norm(ident.get("CHART")),
        "item_image": _norm(ident.get("ITEM_IMAGE")),
        "form": _norm(ident.get("FORM_CODE_NAME")),
        "etc_otc": etc_otc,
        "permit_date": _parse_date(ident.get("ITEM_PERMIT_DATE")),
        "class_name": _norm(ident.get("CLASS_NAME")),
        "bizrno": _norm(ident.get("BIZRNO")),
    }
    if is_rare:
        row["is_rare"] = True
    return row


def merge_drug_record(
    easy: Mapping[str, Any] | None,
    ident: Mapping[str, Any] | None,
    permit: Mapping[str, Any] | None,
) -> dict[str, Any]:
    """3 API 의 단일 itemSeq 레코드를 머지.

    우선순위: easy > permit > ident. None 은 덮어쓰지 않는다.
    모두 None 인 키는 결과 dict 에서 제외한다.
    """
    if easy is None and ident is None and permit is None:
        raise ValueError("at least one source record is required")

    by_priority: list[dict[str, Any]] = []
    if easy is not None:
        by_priority.append(_from_easy(easy))
    if permit is not None:
        by_priority.append(_from_permit(permit))
    if ident is not None:
        by_priority.append(_from_ident(ident))

    merged: dict[str, Any] = {}
    # 우선순위 높은 순으로 채우고, None 은 덮어쓰지 않는다.
    for source in by_priority:
        for key, value in source.items():
            if value is None:
                continue
            merged.setdefault(key, value)

    return merged
