package com.pillmate.caregroup.application.dto;

/**
 * 알약 정보(L2) 공유 설정 화면의 한 행 — owner(요청자) 가 groupId 안에서 이 멤버에게
 * 공유를 켰는지(shared) 를 담는다.
 */
public record ShareSettingView(Long userId, String name, String role, boolean shared) {}
