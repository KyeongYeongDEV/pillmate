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
    SCHEDULE_PERIOD_ENDED("PILL_032", "복약 기간이 종료되어 시간을 수정할 수 없습니다."),
    SCHEDULE_INVALID_TIME_OF_DAY("PILL_033", "유효하지 않은 복약 시간대입니다."),
    SCHEDULE_INVALID_PERIOD("PILL_034", "복약 시작일이 종료일보다 늦을 수 없습니다."),
    PRESCRIPTION_PERIOD_ENDED("PILL_035", "복약 기간이 종료된 처방입니다."),
    INVALID_REQUEST("PILL_040", "잘못된 요청입니다."),
    DOSE_LOG_DATE_LOCKED("PILL_041", "지난 날짜의 복약 기록은 수정할 수 없습니다."),
    OCR_UPSTREAM_TIMEOUT("PILL_050", "OCR 서비스 응답 시간이 초과되었습니다."),
    OCR_UPSTREAM_FAILED("PILL_051", "OCR 서비스 호출에 실패했습니다."),
    OCR_REQUEST_INVALID("PILL_052", "OCR 요청이 잘못되었습니다."),
    OCR_EMPTY("PILL_053", "처방전에서 추출된 약품 정보가 없습니다."),
    REPORT_NOT_FOUND("PILL_060", "건강 리포트를 찾을 수 없습니다."),
    REPORT_REFRESH_RATE_LIMITED("PILL_061", "리포트 새로고침은 하루 1회만 가능합니다."),
    REPORT_GENERATION_FAILED("PILL_062", "리포트 생성에 실패했습니다."),
    PATIENT_ACCESS_DENIED("PILL_016", "해당 환자의 데이터에 접근 권한이 없습니다."),
    ACTIVITY_FEED_NOT_FOUND("PILL_070", "활동 피드를 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND("PILL_080", "알림을 찾을 수 없습니다."),
    NOT_NOTIFICATION_OWNER("PILL_081", "해당 알림에 접근 권한이 없습니다."),
    INVALID_NOTIFICATION_DOSE_LOG("PILL_082", "알림 발송 대상 복약 기록을 찾을 수 없습니다."),
    INVITE_CODE_EXPIRED_OR_INVALID("PILL_096", "유효하지 않거나 만료된 초대 코드입니다."),
    INVITE_CACHE_UNAVAILABLE("PILL_097", "초대 코드 검증 서비스에 일시적으로 연결할 수 없습니다."),
    KAKAO_AUTH_FAILED("PILL_083", "카카오 인증에 실패했습니다."),
    INVALID_AUTH_TOKEN("PILL_084", "인증 토큰이 유효하지 않습니다."),
    LOGIN_CODE_NOT_FOUND("PILL_085", "유효하지 않은 로그인 코드입니다."),
    LOGIN_CODE_EXPIRED("PILL_086", "로그인 코드가 만료되었습니다. 다시 로그인해 주세요."),
    RATE_LIMIT_EXCEEDED("PILL_090", "오늘 사용량을 초과했어요. 내일 다시 시도해 주세요."),
    ADMIN_ACCESS_DENIED("PILL_091", "관리자 전용 기능입니다."),
    ACCOUNT_WITHDRAWN("PILL_092", "탈퇴한 계정입니다. 다시 로그인해 주세요."),
    INTERNAL_SERVER_ERROR("PILL_999", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String message;
}
