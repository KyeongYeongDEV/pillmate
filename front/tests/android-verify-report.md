# T-FE-ANDROID-VERIFY — Android 에뮬레이터 검증 보고서

**일자**: 2026-05-26  
**환경**: Small_Phone_API_35 (Android 15 / API 35, ARM64), Expo SDK 56, RN 0.85.3  
**빌드**: `npx expo run:android --no-build-cache` → `app-debug.apk` (85MB), Gradle 9.3.1  
**설치**: `adb install -r app-debug.apk` → Success  
**Metro**: `http://10.0.2.2:8081` (정상 연결 확인)

---

## ✅ 화면별 검증 결과

### 1. 홈 (home-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| 그룹 셀렉터 (할머니 댁 · 3명) | ✅ PASS | 정상 렌더 |
| 알림 뱃지 (4) | ✅ PASS | 빨간 뱃지 표시 |
| 환영 메시지 (안녕하세요, 민지님) | ✅ PASS | |
| 복약 진행률 (4/6 완료) | ✅ PASS | 파란 프로그레스바 |
| 오늘의 복약 카드 리스트 | ✅ PASS | 아침/점심/저녁/취침 전 |
| 아침 복용 완료 카드 (초록 체크) | ✅ PASS | |
| 점심 지금 드세요 카드 (파란 테두리) | ✅ PASS | 강조 표시 정상 |
| 탭바 (홈/복약/상담/그룹) | ✅ PASS | 4탭 정상 표시 |
| SafeArea (status bar 오버랩 없음) | ✅ PASS | Android 15 edge-to-edge |

### 2. 복약 스케줄 (schedule-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| 캘린더 (2025년 11월) | ✅ PASS | |
| 날짜별 복용 상태 도트 | ✅ PASS | 초록/주황/빨강 |
| 오늘 날짜 강조 (24, 검정 원) | ✅ PASS | |
| 범례 (전체/일부/미복용) | ✅ PASS | |
| 복약 4/6 완료 섹션 | ✅ PASS | |
| FAB (+) 버튼 | ✅ PASS | 탭바 위 부동 |

### 3. 복약 상담 (chat-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| "복약 상담" 제목 | ✅ PASS | |
| Gemini · RAG 검증 온라인 표시 | ✅ PASS | 초록 도트 |
| PillMate AI 초기 메시지 | ✅ PASS | 의료 면책 문구 포함 |
| 사용자 메시지 버블 (파란 배경) | ✅ PASS | |
| AI 응답 (주의 경고 오렌지 텍스트) | ✅ PASS | △ 일부 감기약은 주의 |
| 입력창 ("약에 대해 물어보세요...") | ✅ PASS | |
| + 버튼 / 전송 버튼 | ✅ PASS | |

### 4. 케어 그룹 (group-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| "케어 그룹" 제목 | ✅ PASS | |
| 그룹 카드 (할머니 댁, 3명 아바타) | ✅ PASS | |
| + 초대하기 버튼 | ✅ PASS | |
| QR 코드 버튼 | ✅ PASS | |
| 구성원 리스트 (박순자/김민지/김지훈) | ✅ PASS | |
| 역할 뱃지 (환자/보호자) | ✅ PASS | |
| 온라인 도트 | ✅ PASS | |
| 초대 코드 섹션 (부분) | ✅ PASS | 스크롤 가능 |

### 5. 처방전 등록 허브 (fab-menu-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| "처방전 등록" 제목 + 뒤로가기 | ✅ PASS | |
| 어떻게 등록할까요? 카드 | ✅ PASS | |
| 카메라로 촬영하기 (추천, 검정 배경) | ✅ PASS | |
| 갤러리에서 버튼 | ✅ PASS | |
| 직접 입력 버튼 | ✅ PASS | |
| **약 검색하기 버튼 (파란 점선 테두리)** | ✅ PASS | T-FE-005 핵심 기능 |
| 촬영 팁 카드 | ✅ PASS | |

### 6. 약 검색 (prescription-search-android.png)
| 항목 | 결과 | 비고 |
|------|------|------|
| 검색바 (autoFocus, 파란 테두리) | ✅ PASS | Android 소프트 키보드 정상 |
| 취소 버튼 | ✅ PASS | |
| 필터 칩 (AI 의미 검색/이름/성분/효능) | ✅ PASS | |
| 최근 검색 칩 (메트포르민/오메가-3/글리메피리드) | ✅ PASS | |
| 카테고리 그리드 | ✅ PASS | |
| 최근 검색 칩 탭 → 검색 실행 | ✅ PASS | "메트포르민" 탭 → 1건 결과 |
| 검색 결과 카드 (메트포르민정 500mg) | ✅ PASS | |
| Highlight 컴포넌트 (파란 강조) | ✅ PASS | "메트포르민" 파란 하이라이트 |
| 출처: 식품의약품안전처 의약품안전나라 | ✅ PASS | 의료 안전 필수 |
| + 버튼 | ✅ PASS | |

---

## Android vs iOS 차이 비교

| 항목 | iOS | Android | 판정 |
|------|-----|---------|------|
| 상태바 | SafeArea 자동 처리 | edge-to-edge (Android 15) | ✅ 동일 |
| 탭바 위치 | 하단 SafeArea 적용 | 하단 제스처 바 위 | ✅ 정상 |
| 폰트 렌더링 | SF Pro | Noto Sans KR | ✅ 한글 정상 |
| 카드 그림자 | box-shadow | elevation | ✅ 동일 외관 |
| FAB 위치 | 탭바 중앙 위 | 탭바 중앙 위 | ✅ 동일 |
| 키보드 타입 | iOS 기본 | QWERTY English (에뮬레이터) | ✅ 동작 정상 |
| 터치 피드백 | iOS 기본 | Material ripple | ✅ 각 플랫폼 적절 |
| StatusBar 색상 | 투명 | edge-to-edge (무시됨) | P2 (정보성) |
| 소프트 백 버튼 | N/A | 하드웨어/제스처 지원 | ✅ 정상 |
| 한글 입력 | URL 인코딩 → 정상 | Recent chip 탭 → 정상 | ✅ 정상 |

---

## 발견된 이슈

### P2 (정보성 — 기능 영향 없음)

**[AND-01] StatusBar edge-to-edge 경고**
- 증상: Logcat `StatusBarModule: Ignored status bar change, current activity is edge-to-edge.`
- 원인: Android 15에서 edge-to-edge가 강제됨. React Native의 `StatusBar.setBarStyle()` 무시됨
- 영향: 시각적 레이아웃 정상. 상태바 텍스트 색상만 고정 (흰색)
- 권고: Phase 2에서 `android:windowLightStatusBar` 설정 추가

**[AND-02] Reselect identity function warning**
- 증상: `The result function returned its own inputs without modification`
- 원인: RTK Query 내부 selector 구성 방식 (라이브러리 이슈)
- 영향: 없음 (성능 minor)
- 권고: RTK Query 업그레이드 시 자동 해결 예상

**[AND-03] 에뮬레이터 Korean IME 입력 제한**
- 증상: `adb shell input text`로 한글 직접 입력 불가 (URL-encoded 리터럴 출력)
- 해결: Recent Search Chip 탭으로 한글 검색 트리거 → 정상 동작 확인
- 실기기 영향: 없음 (실기기는 한글 키보드 정상 지원)

---

## 스크린샷 목록

| 파일 | 내용 |
|------|------|
| `home-android.png` | 홈 화면 (복약 현황, 탭바) |
| `schedule-android.png` | 복약 스케줄 (캘린더, FAB) |
| `chat-android.png` | 복약 상담 (AI 응답, 입력창) |
| `group-android.png` | 케어 그룹 (멤버 카드, 초대) |
| `fab-menu-android.png` | 처방전 등록 허브 (약 검색하기 버튼) |
| `prescription-search-android.png` | 약 검색 결과 (메트포르민, 하이라이트, 출처) |

---

## 종합 판정

**Android 에뮬레이터 검증: PASS**

- 6개 화면 모두 정상 렌더링
- iOS와 동등한 UX (플랫폼 적절 차이 허용)
- P0/P1 이슈 없음
- T-FE-005 핵심 기능 (약 검색 → Register Hub 연동) Android 동작 확인
- 의료 안전 규칙 (식약처 출처 표시) Android 정상

`DONE_FE_T-FE-ANDROID-VERIFY`
