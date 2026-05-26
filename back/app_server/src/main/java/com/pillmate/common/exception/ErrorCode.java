package com.pillmate.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    DRUG_NOT_FOUND("PILL_001", "약을 찾을 수 없습니다."),
    DRUG_SEARCH_EMPTY_QUERY("PILL_002", "검색어를 입력해주세요."),
    DRUG_INTERACTION_CHECK_FAILED("PILL_003", "약물 상호작용 확인에 실패했습니다."),
    ITEM_SEQ_NOT_FOUND("PILL_006", "해당 item_seq 의 약품을 찾을 수 없습니다."),
    ALIAS_NOT_FOUND("PILL_007", "약품 alias 를 찾을 수 없습니다."),
    GROUP_NOT_FOUND("PILL_010", "케어 그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED("PILL_011", "해당 그룹에 접근 권한이 없습니다."),
    GROUP_INVITE_CODE_INVALID("PILL_012", "유효하지 않은 초대 코드입니다."),
    GROUP_INVITE_CODE_EXPIRED("PILL_013", "만료된 초대 코드입니다."),
    GROUP_INVITE_CODE_USED("PILL_014", "이미 사용된 초대 코드입니다."),
    GROUP_ALREADY_MEMBER("PILL_015", "이미 그룹에 가입되어 있습니다."),
    PRESCRIPTION_NOT_FOUND("PILL_020", "처방전을 찾을 수 없습니다."),
    PRESCRIPTION_DRUG_NOT_MATCHED("PILL_021", "처방전 약품을 식약처 DB에서 찾을 수 없습니다."),
    PRESCRIPTION_ITEMS_EMPTY("PILL_022", "처방 약 목록이 비어 있습니다."),
    CANDIDATE_NOT_FOUND("PILL_023", "후보 약품을 찾을 수 없습니다."),
    CANDIDATE_ALREADY_RESOLVED("PILL_024", "이미 확인된 후보 약품입니다."),
    CANDIDATE_OPTION_INVALID("PILL_025", "선택한 약품이 후보 목록에 없습니다."),
    SCHEDULE_NOT_FOUND("PILL_030", "복약 스케줄을 찾을 수 없습니다."),
    SCHEDULE_CONFLICT("PILL_031", "동일 시간대에 이미 스케줄이 존재합니다."),
    INVALID_REQUEST("PILL_040", "잘못된 요청입니다."),
    OCR_UPSTREAM_TIMEOUT("PILL_050", "OCR 서비스 응답 시간이 초과되었습니다."),
    OCR_UPSTREAM_FAILED("PILL_051", "OCR 서비스 호출에 실패했습니다."),
    OCR_REQUEST_INVALID("PILL_052", "OCR 요청이 잘못되었습니다."),
    OCR_EMPTY("PILL_053", "처방전에서 추출된 약품 정보가 없습니다."),
    REPORT_NOT_FOUND("PILL_060", "건강 리포트를 찾을 수 없습니다."),
    REPORT_REFRESH_RATE_LIMITED("PILL_061", "리포트 새로고침은 하루 1회만 가능합니다."),
    REPORT_GENERATION_FAILED("PILL_062", "리포트 생성에 실패했습니다."),
    INTERNAL_SERVER_ERROR("PILL_999", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;
}
