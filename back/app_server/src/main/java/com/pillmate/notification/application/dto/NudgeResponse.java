package com.pillmate.notification.application.dto;

// alreadyNotified=false: 이번 요청으로 FCM 발송됨 / true: 당사자가 방금 이미(다른 발신자·dose 로) 알림받아 발송 생략됨
public record NudgeResponse(boolean alreadyNotified) {
}
