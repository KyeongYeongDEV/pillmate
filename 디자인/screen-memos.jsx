// PillMate — Screen feature memos (yellow post-it style cards)

// Feature list per screen id. Each line is a short bullet point.
const SCREEN_MEMOS = {
  login: {
    title: '로그인',
    items: [
      '카카오 · 네이버 · Google 3종 소셜 로그인',
      '카카오 "3초 만에 시작" 뱃지로 1순위 강조',
      '이메일로 시작 (대체)',
      '약관·개인정보 동의 자동 포함',
    ],
  },
  onboarding: {
    title: '온보딩',
    items: [
      '"처방전 한 장으로 가족 복약" 가치 제안',
      '처방전 종이 + AI 인식 카드 레이어드 비주얼',
      '3페이지 가이드 (점 인디케이터)',
      '건너뛰기 / 로그인 진입',
    ],
  },
  home: {
    title: '메인 (홈)',
    items: [
      '그룹 셀렉터 (탭하면 다른 그룹으로 전환)',
      '오른쪽 상단 🔔 → 알림 모음 진입',
      '안 읽음 카운트 빨간 뱃지 (예: 4)',
      '오늘 복약 진행률 바 (4/6)',
      '시간대별 카드 4개: 완료 / 현재 / 대기',
      'AI 인사이트 카드 (저녁 미복용 패턴)',
      '그룹 활동 피드 (실시간)',
      '하단 탭바 · 가운데 처방전 등록 FAB',
    ],
  },
  register: {
    title: '처방전 등록 허브',
    items: [
      '탭바 가운데 버튼의 진입 지점',
      '카메라로 촬영 (1순위, 큰 검정 CTA)',
      '갤러리에서 사진 선택',
      '직접 입력 (수동 등록)',
      '촬영 팁 카드',
      '최근 등록 3건 미리보기',
      '※ 추후 구현: 약국 QR 코드 등록 (제휴)',
    ],
  },
  scan: {
    title: '처방전 촬영',
    items: [
      '카메라 라이브 뷰',
      '사각형 가이드 + 코너 브래킷',
      'AI 자동 인식 글래스 뱃지',
      '플래시 토글 · 갤러리 · 수동 입력',
      '캡처 셔터 (76px)',
    ],
  },
  result: {
    title: 'AI 인식 결과',
    items: [
      'Gemini Vision + RAG 매칭 표시',
      '5개 약 추출 · 신뢰도 별 표기',
      '약별 4슬롯 토글 (아침/점심/저녁/취침전)',
      '기본 복용 시간 카드 (탭해서 수정)',
      '낮은 신뢰도 약 경고 (오메가-3)',
      '+ 직접 추가하기 진입',
      '누락된 약: 검색 / 직접 입력 2갈래',
      '처방전 메모 + 빠른 프리필 칩',
    ],
  },
  manual: {
    title: '약 직접 추가',
    items: [
      '약 이름 입력 + 식약처 DB 인라인 검색',
      '실시간 알약 미리보기',
      '모양(원/타원/캡슐) + 8색 컬러 칩',
      '1회 복용량 스텝퍼 + 단위',
      '복용 시간대 4슬롯 토글',
      '복용 기간 프리셋 (7·14·30·90·장기)',
      '메모 입력 + AI 검증 안내',
    ],
  },
  detail: {
    title: '약 상세',
    items: [
      '알약 히어로 + 분류 태그',
      '일일 복용 · 시각 · 남은 일수',
      '효능/용법/주의 3탭',
      '병용금기 경고 카드 (이트라코나졸)',
      '부작용 빈도 칩 (12% 두통 등)',
      '식약처 의약품안전나라 출처',
    ],
  },
  search: {
    title: '약 검색',
    items: [
      'AI 의미 검색 모드 (자연어 OK)',
      '이름/성분/효능 필터',
      '검색어 글자 단위 하이라이트',
      '"복용 중" 뱃지로 가족 약 식별',
      '최근 검색 칩 + 카테고리 그리드 6종',
      '"흰색 동그란 알약" 같은 묘사 검색',
    ],
  },
  rxlist: {
    title: '처방전 목록',
    items: [
      '상태 필터: 복용중 / 복용완료 / 중단',
      '처방 카드 (병원·의사·기간·가족)',
      '진행률 바 + D-day 카운트',
      '임박 처방 주황 강조 (D-1)',
      '복용 완료 시 복약률 표기',
      '처방별 메모 (식후 30분 등)',
      'NEW 뱃지 · FAB로 새 처방 추가',
    ],
  },
  schedule: {
    title: '복약 스케줄',
    items: [
      '월 캘린더 + 일별 복약 히트맵',
      '4가지 상태 점 (전체/일부/미복용/오늘)',
      '오늘 4시간대 카드 (체크 가능)',
      '복약 완료 시 취소선 처리',
      '새 복용 추가 + 이전/다음 월 이동',
    ],
  },
  chat: {
    title: '복약 상담 AI',
    items: [
      'Gemini + RAG 검증 상태 표시',
      '빠른 프롬프트 칩 3종',
      'AI 응답 + 위험 등급',
      '출처 카드 (식약처·학회)',
      'Follow-up 액션 칩',
      '음성·이미지 입력 (확장)',
    ],
  },
  report: {
    title: '건강 리포트',
    items: [
      '월간 92점 점수 링 차트',
      '지난달 대비 +8점 트렌드',
      '일별 복약률 막대 차트 (24일)',
      'AI 분석 카드 (주의 패턴)',
      '맞춤 추천 (식단·운동)',
      '월 셀렉터로 과거 비교',
    ],
  },
  groups: {
    title: '그룹 목록',
    items: [
      '채팅방처럼 여러 그룹 보유',
      '고정/모든 그룹 섹션',
      '필터: 전체/내가 환자/보호자/비공개',
      '그룹별 마지막 이벤트 칩 미리보기',
      '안 읽은 카운트 배지',
      '새 그룹 만들기 (CTA + FAB)',
      '비공개 "내 복약 일지" 그룹 포함',
    ],
  },
  group: {
    title: '그룹 상세',
    items: [
      '겹침 아바타로 그룹 소개',
      '구성원 리스트 (환자/보호자 라벨)',
      '온라인 인디케이터',
      '초대하기 + QR 진입',
      '6자리 초대 코드 + 유효 시간',
      '↓ 스크롤: 최근 일주일 그룹 활동 피드',
      '복약 완료 · 미복용 · AI · 처방전 · 메모',
    ],
  },
  activity: {
    title: '그룹 활동',
    items: [
      '필터: 전체/복약/처방전/AI',
      '일자별 그룹 (오늘/어제/이전)',
      '타임라인 레일 + 이벤트 점 색상',
      '복약 완료/미복용/AI/메모/처방전',
      'CTA 액션 ("알림 조정", "리포트 열기")',
    ],
  },
  notifs: {
    title: '알림 목록',
    items: [
      '안 읽음 카운트 + 모두 읽음',
      '필터 (전체/안 읽음/복약/가족/AI)',
      '알림 타입별 색상 코딩',
      '읽지 않음 파란 점 인디케이터',
      'CTA "복용 완료 / 15분 후"',
      '하단: 알림 설정 진입',
    ],
  },
};

// Memo card — yellow post-it style, designed to sit next to a phone frame
function MemoCard({ memoKey }) {
  const m = SCREEN_MEMOS[memoKey];
  if (!m) return null;
  return (
    <div style={{
      width: 230, padding: '20px 18px',
      background: '#FEF4A8',
      boxShadow: '0 4px 12px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.06)',
      fontFamily: 'var(--font-sans)',
      color: '#5a4a2a',
      borderRadius: 2,
      transform: 'rotate(0.6deg)',
      flexShrink: 0,
      marginTop: 16,
      position: 'relative',
    }}>
      {/* tape */}
      <div style={{
        position: 'absolute', top: -12, left: '50%',
        transform: 'translateX(-50%) rotate(-3deg)',
        width: 56, height: 18,
        background: 'rgba(217, 168, 40, 0.35)',
        borderRadius: 1,
      }} />
      <div style={{
        fontSize: 11, fontWeight: 700, letterSpacing: '0.08em',
        textTransform: 'uppercase', color: '#8a6f2a',
        marginBottom: 4,
      }}>화면 기능</div>
      <div style={{
        fontSize: 18, fontWeight: 700, color: '#3d2f12',
        letterSpacing: '-0.015em', marginBottom: 14,
      }}>{m.title}</div>
      <ul style={{
        margin: 0, padding: 0, listStyle: 'none',
        display: 'flex', flexDirection: 'column', gap: 8,
      }}>
        {m.items.map((it, i) => (
          <li key={i} style={{
            fontSize: 13, lineHeight: '18px', color: '#5a4a2a',
            display: 'flex', gap: 6, alignItems: 'flex-start',
          }}>
            <span style={{ color: '#a07820', fontWeight: 700, lineHeight: '18px', flexShrink: 0 }}>·</span>
            <span>{it}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

Object.assign(window, { SCREEN_MEMOS, MemoCard });
