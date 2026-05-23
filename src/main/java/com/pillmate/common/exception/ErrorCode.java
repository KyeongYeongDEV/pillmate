package com.pillmate.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    DRUG_NOT_FOUND("PILL_001", "약을 찾을 수 없습니다."),
    DRUG_SEARCH_EMPTY_QUERY("PILL_002", "검색어를 입력해주세요."),
    DRUG_INTERACTION_CHECK_FAILED("PILL_003", "약물 상호작용 확인에 실패했습니다."),
    GROUP_NOT_FOUND("PILL_010", "케어 그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED("PILL_011", "해당 그룹에 접근 권한이 없습니다."),
    PRESCRIPTION_NOT_FOUND("PILL_020", "처방전을 찾을 수 없습니다."),
    PRESCRIPTION_DRUG_NOT_MATCHED("PILL_021", "처방전 약품을 식약처 DB에서 찾을 수 없습니다."),
    PRESCRIPTION_ITEMS_EMPTY("PILL_022", "처방 약 목록이 비어 있습니다."),
    SCHEDULE_NOT_FOUND("PILL_030", "복약 스케줄을 찾을 수 없습니다."),
    SCHEDULE_CONFLICT("PILL_031", "동일 시간대에 이미 스케줄이 존재합니다."),
    INVALID_REQUEST("PILL_040", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR("PILL_999", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;
}
