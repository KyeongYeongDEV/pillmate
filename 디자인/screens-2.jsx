// PillMate — Screens part 2: Detail · Schedule · Chat · Report · Group

// ────────────────────────────────────────────────────────────────
// 5. Drug Detail — efficacy / side-effect / 병용금기
// ────────────────────────────────────────────────────────────────
function ScreenDetail() {
  return (
    <div style={{ ...screenStyle, background: '#fff' }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="약 정보"
        right={<Icon name="share" size={22} stroke={1.8} />}
      />

      <div style={{ flex: 1, overflow: 'auto' }}>
        {/* hero — pill visual + names */}
        <div style={{ ...padX, paddingTop: 12, paddingBottom: 24 }}>
          <div style={{
            background: 'var(--c-blue-95)', borderRadius: 20, padding: '32px 20px',
            display: 'flex', flexDirection: 'column', alignItems: 'center',
          }}>
            <PillVisual color="lightBlue" size={86} />
            <div style={{ fontSize: 11, color: 'var(--c-blue-40)', fontWeight: 700, marginTop: 18, letterSpacing: '0.06em' }}>
              혈압강하제 · ARB
            </div>
            <div style={{ fontSize: 22, fontWeight: 700, marginTop: 4, letterSpacing: '-0.018em', color: C.text }}>
              암로디핀정 5mg
            </div>
            <div style={{ fontSize: 13, color: C.muted, marginTop: 2 }}>
              Amlodipine Besylate · 한미약품
            </div>
          </div>
        </div>

        {/* quick-stats row */}
        <div style={{ ...padX, display: 'flex', gap: 8, marginBottom: 28 }}>
          {[
            ['일일 복용', '1정'],
            ['복용 시각', '아침'],
            ['남은 일수', '23일'],
          ].map(([k, v]) => (
            <div key={k} style={{ flex: 1, background: '#fff', border: `1px solid ${C.line}`, borderRadius: 12, padding: '12px 10px', textAlign: 'center' }}>
              <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, letterSpacing: '0.03em' }}>{k}</div>
              <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4, letterSpacing: '-0.012em' }}>{v}</div>
            </div>
          ))}
        </div>

        {/* tabs */}
        <div style={{ ...padX, display: 'flex', gap: 0, borderBottom: `1px solid ${C.line}`, marginBottom: 4 }}>
          {['효능·효과', '용법·용량', '주의사항'].map((t, i) => (
            <div key={t} style={{
              flex: 1, paddingBottom: 12, textAlign: 'center',
              fontSize: 14, fontWeight: 700,
              color: i === 0 ? C.text : C.alt,
              borderBottom: i === 0 ? `2px solid ${C.text}` : '2px solid transparent',
              marginBottom: -1, letterSpacing: '-0.005em',
            }}>{t}</div>
          ))}
        </div>

        {/* efficacy content */}
        <div style={{ ...padX, paddingTop: 20, paddingBottom: 16 }}>
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 10, letterSpacing: '-0.015em' }}>이런 분께 처방돼요</div>
          {[
            '본태성 고혈압',
            '관상동맥질환에 의한 만성 안정형 협심증',
            '혈관경련성 협심증 (이형 협심증)',
          ].map((t, i) => (
            <div key={i} style={{ display: 'flex', gap: 10, padding: '8px 0', fontSize: 14, color: C.muted, lineHeight: '22px' }}>
              <div style={{ width: 5, height: 5, borderRadius: '50%', background: C.text, marginTop: 9, flexShrink: 0 }} />
              {t}
            </div>
          ))}
        </div>

        {/* 병용금기 warning card */}
        <div style={{ ...padX, paddingTop: 8, paddingBottom: 16 }}>
          <div style={{
            background: 'var(--c-red-95)', borderRadius: 14, padding: '16px 16px',
            border: `1px solid var(--c-red-90)`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--c-red-30)' }}>
              <Icon name="warn" size={18} stroke={2} />
              <div style={{ fontSize: 14, fontWeight: 700, letterSpacing: '-0.01em' }}>병용금기 1건</div>
            </div>
            <div style={{ marginTop: 12, padding: '10px 12px', background: '#fff', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 10 }}>
              <PillVisual color="pink" size={28} />
              <div style={{ flex: 1, fontSize: 13, color: C.text, fontWeight: 600 }}>이트라코나졸 (항진균제)</div>
              <Icon name="chevronR" size={18} stroke={2} />
            </div>
            <div style={{ fontSize: 12, color: 'var(--c-red-30)', marginTop: 10, lineHeight: '18px' }}>
              혈중 농도가 상승해 부작용 위험이 커집니다. 처방의와 반드시 상의하세요.
            </div>
          </div>
        </div>

        {/* side effects */}
        <div style={{ ...padX, paddingTop: 12, paddingBottom: 16 }}>
          <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 10, letterSpacing: '-0.015em' }}>대표 부작용</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {[
              ['두통', 0.12], ['안면홍조', 0.08], ['하지부종', 0.07],
              ['어지러움', 0.04], ['심계항진', 0.02], ['피로감', 0.01],
            ].map(([k, v]) => (
              <div key={k} style={{
                padding: '8px 12px', borderRadius: 9999,
                background: C.fill, fontSize: 13, color: C.text,
                display: 'flex', alignItems: 'center', gap: 6,
              }}>
                {k}
                <span style={{ fontSize: 11, color: C.alt, fontWeight: 600 }}>{(v * 100).toFixed(0)}%</span>
              </div>
            ))}
          </div>
        </div>

        {/* source */}
        <div style={{ ...padX, paddingTop: 12, paddingBottom: 24 }}>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
            background: C.bgAlt, borderRadius: 12,
          }}>
            <div style={{
              width: 28, height: 28, borderRadius: 6,
              background: '#fff', border: `1px solid ${C.line}`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 11, fontWeight: 700, color: 'var(--c-blue-45)',
            }}>식약</div>
            <div style={{ flex: 1, fontSize: 12, color: C.muted, lineHeight: '17px' }}>
              <b style={{ color: C.text, fontWeight: 700 }}>식품의약품안전처</b> · 의약품안전나라<br/>
              최종 업데이트 2025.10.18
            </div>
            <Icon name="external-link" size={16} stroke={1.8} />
          </div>
        </div>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 6. Schedule — month calendar + day detail
// ────────────────────────────────────────────────────────────────
function ScreenSchedule() {
  // build a fake November 2025 grid (starts Sat)
  const days = [];
  for (let i = 0; i < 35; i++) {
    const day = i - 5; // Nov 1 starts at index 6
    if (day < 1 || day > 30) days.push(null);
    else days.push(day);
  }
  // Each day's adherence: 'full', 'partial', 'miss', 'none' (future)
  const status = {
    1: 'full', 2: 'full', 3: 'full', 4: 'partial', 5: 'full', 6: 'full', 7: 'full',
    8: 'full', 9: 'partial', 10: 'full', 11: 'full', 12: 'full', 13: 'miss',
    14: 'partial', 15: 'full', 16: 'full', 17: 'full', 18: 'full', 19: 'partial',
    20: 'full', 21: 'full', 22: 'full', 23: 'miss', 24: 'today',
  };
  const dotColor = {
    full: C.positive, partial: C.cautionary, miss: C.negative, today: C.primary,
  };

  const todayMeds = [
    { time: '08:00', label: '아침', state: 'done', items: ['암로디핀 5mg', '메트포르민 500mg'] },
    { time: '12:30', label: '점심', state: 'now', items: ['메트포르민 500mg', '글리메피리드 2mg'] },
    { time: '19:00', label: '저녁', state: 'wait', items: ['아토르바스타틴 10mg'] },
    { time: '22:00', label: '취침 전', state: 'wait', items: ['오메가-3 1000mg'] },
  ];

  return (
    <div style={{ ...screenStyle, background: '#fff' }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="복약 스케줄"
        right={<Icon name="plus" size={24} stroke={2} />}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 80 }}>
        {/* month header */}
        <div style={{ ...padX, paddingTop: 4, paddingBottom: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.018em' }}>2025년 11월</div>
          <div style={{ display: 'flex', gap: 4 }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: C.fill, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="chevronL" size={18} stroke={2.2} />
            </div>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: C.fill, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="chevronR" size={18} stroke={2.2} />
            </div>
          </div>
        </div>

        {/* weekday header */}
        <div style={{ ...padX, display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', marginBottom: 4 }}>
          {['일','월','화','수','목','금','토'].map((d, i) => (
            <div key={d} style={{
              textAlign: 'center', fontSize: 11, fontWeight: 600,
              color: i === 0 ? C.negative : i === 6 ? C.primary : C.alt,
              padding: '6px 0',
            }}>{d}</div>
          ))}
        </div>

        {/* calendar grid */}
        <div style={{ ...padX, display: 'grid', gridTemplateColumns: 'repeat(7,1fr)', rowGap: 2 }}>
          {days.map((d, i) => {
            const s = d && status[d];
            const isToday = d === 24;
            const col = i % 7;
            return (
              <div key={i} style={{
                aspectRatio: '1 / 1.05', display: 'flex', flexDirection: 'column',
                alignItems: 'center', justifyContent: 'center', position: 'relative',
              }}>
                {d && (
                  <>
                    <div style={{
                      width: 32, height: 32, borderRadius: '50%',
                      background: isToday ? C.text : 'transparent',
                      color: isToday ? '#fff' : col === 0 ? C.negative : col === 6 ? C.primary : C.text,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 14, fontWeight: isToday ? 700 : 500,
                    }}>{d}</div>
                    {s && !isToday && (
                      <div style={{ width: 6, height: 6, borderRadius: '50%', background: dotColor[s], marginTop: 2 }} />
                    )}
                    {isToday && (
                      <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'transparent', marginTop: 2 }} />
                    )}
                  </>
                )}
              </div>
            );
          })}
        </div>

        {/* legend */}
        <div style={{ ...padX, display: 'flex', gap: 16, marginTop: 14, marginBottom: 12 }}>
          {[['전체 복용', C.positive], ['일부 미복용', C.cautionary], ['미복용', C.negative]].map(([t, c]) => (
            <div key={t} style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: C.muted }}>
              <div style={{ width: 6, height: 6, borderRadius: '50%', background: c }} />
              {t}
            </div>
          ))}
        </div>

        {/* selected day */}
        <div style={{ height: 8, background: C.bgAlt }} />
        <div style={{ ...padX, paddingTop: 20, paddingBottom: 12, background: C.bgAlt }}>
          <div style={{ fontSize: 12, color: C.alt, fontWeight: 600, letterSpacing: '0.04em' }}>오늘 · 11월 24일 월</div>
          <div style={{ fontSize: 18, fontWeight: 700, marginTop: 2, letterSpacing: '-0.012em' }}>복약 4 / 6 완료</div>
        </div>

        <div style={{ background: C.bgAlt, ...padX, paddingBottom: 24 }}>
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, overflow: 'hidden' }}>
            {todayMeds.map((m, i) => {
              const done = m.state === 'done';
              const now = m.state === 'now';
              return (
                <div key={i} style={{
                  display: 'flex', gap: 14, padding: '14px 16px',
                  borderTop: i === 0 ? 'none' : `1px solid ${C.line}`,
                  alignItems: 'center',
                }}>
                  <div style={{ minWidth: 48, textAlign: 'center' }}>
                    <div style={{ fontSize: 15, fontWeight: 700, color: done ? C.alt : C.text }}>{m.time}</div>
                    <div style={{ fontSize: 11, color: C.alt, marginTop: 1 }}>{m.label}</div>
                  </div>
                  <div style={{ width: 1, height: 36, background: C.line }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    {m.items.map((it, j) => (
                      <div key={j} style={{
                        fontSize: 14, color: done ? C.alt : C.text,
                        textDecoration: done ? 'line-through' : 'none',
                        fontWeight: 500, lineHeight: '20px',
                      }}>{it}</div>
                    ))}
                  </div>
                  <div style={{
                    width: 32, height: 32, borderRadius: '50%',
                    background: done ? C.positive : now ? C.primary : 'transparent',
                    border: done || now ? 'none' : `1.5px solid ${C.line}`,
                    color: done || now ? '#fff' : C.alt,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    {done && <Icon name="check" size={18} stroke={2.6} />}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <TabBar active="schedule" />
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 7. AI Chatbot — RAG with sources
// ────────────────────────────────────────────────────────────────
function ScreenChat() {
  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="복약 상담"
        sub={<span style={{ color: C.positive, fontWeight: 600 }}>● Gemini · RAG 검증</span>}
        right={<Icon name="more-horizontal" size={22} stroke={2} />}
      />

      <div style={{ flex: 1, overflow: 'auto', padding: '8px 16px 16px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {/* AI greeting */}
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <Avatar name="P" tint="#0066FF" size={32} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, marginBottom: 4 }}>PillMate AI</div>
            <div style={{
              background: '#fff', borderRadius: 14, padding: '12px 14px',
              border: `1px solid ${C.line}`, maxWidth: 280,
              fontSize: 14, color: C.text, lineHeight: '21px',
            }}>
              안녕하세요. 할머니가 복용 중인 약에 대해 궁금한 점이 있으시면 물어보세요.
            </div>
            {/* quick prompts */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
              {['감기약 같이 먹어도 돼?', '부작용은?', '음식 주의사항'].map(q => (
                <div key={q} style={{
                  padding: '7px 12px', borderRadius: 9999, background: '#fff',
                  border: `1px solid ${C.line}`,
                  fontSize: 12, color: C.text, fontWeight: 500,
                }}>{q}</div>
              ))}
            </div>
          </div>
        </div>

        {/* user message */}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <div style={{
            background: C.primary, color: '#fff', borderRadius: 14,
            padding: '12px 14px', maxWidth: 280,
            fontSize: 14, lineHeight: '21px',
          }}>
            엄마가 혈압약 먹는데 감기약 같이 드셔도 되나요?
          </div>
        </div>

        {/* AI response with sources */}
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <Avatar name="P" tint="#0066FF" size={32} />
          <div style={{ flex: 1, maxWidth: 290 }}>
            <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, marginBottom: 4 }}>PillMate AI · 1.8초</div>
            <div style={{
              background: '#fff', borderRadius: 14, padding: '14px 16px',
              border: `1px solid ${C.line}`,
              fontSize: 14, color: C.text, lineHeight: '22px',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--c-orange-40)', fontWeight: 700, fontSize: 13 }}>
                <Icon name="warn" size={15} stroke={2.2} />
                일부 감기약은 주의가 필요해요
              </div>
              <div style={{ marginTop: 8 }}>
                <b>암로디핀</b>은 일반 감기약과 대체로 함께 복용 가능하지만, <b>슈도에페드린</b> 성분이 포함된 감기약은 혈압을 올릴 수 있어 피해야 합니다.
              </div>
              {/* sources */}
              <div style={{ marginTop: 12, borderTop: `1px dashed ${C.line}`, paddingTop: 10 }}>
                <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, marginBottom: 6 }}>출처 · 2개</div>
                {[
                  ['식약처 의약품안전나라', '암로디핀정 병용주의'],
                  ['대한고혈압학회', '고혈압 환자의 감기약 복용 지침 2024'],
                ].map(([src, doc], i) => (
                  <div key={i} style={{ display: 'flex', gap: 8, padding: '6px 0', alignItems: 'flex-start' }}>
                    <div style={{
                      width: 18, height: 18, borderRadius: 4, flexShrink: 0,
                      background: 'var(--c-blue-95)', color: 'var(--c-blue-45)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 10, fontWeight: 700,
                    }}>{i + 1}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 12, color: C.text, fontWeight: 600 }}>{src}</div>
                      <div style={{ fontSize: 11, color: C.alt, marginTop: 1 }}>{doc}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
              <div style={{ padding: '6px 11px', borderRadius: 9999, background: '#fff', border: `1px solid ${C.line}`, fontSize: 12, color: C.text, fontWeight: 500 }}>안전한 감기약 추천</div>
              <div style={{ padding: '6px 11px', borderRadius: 9999, background: '#fff', border: `1px solid ${C.line}`, fontSize: 12, color: C.text, fontWeight: 500 }}>의사 상담</div>
            </div>
          </div>
        </div>
      </div>

      {/* input bar */}
      <div style={{
        padding: '10px 16px 28px', background: '#fff',
        borderTop: `1px solid ${C.line}`,
        display: 'flex', gap: 8, alignItems: 'center',
      }}>
        <div style={{ width: 38, height: 38, borderRadius: '50%', background: C.fill, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.muted }}>
          <Icon name="plus" size={22} stroke={2} />
        </div>
        <div style={{
          flex: 1, height: 42, borderRadius: 9999,
          background: C.bgAlt, border: `1px solid ${C.line}`,
          display: 'flex', alignItems: 'center', padding: '0 16px',
          fontSize: 14, color: C.assist,
        }}>약에 대해 물어보세요…</div>
        <div style={{ width: 42, height: 42, borderRadius: '50%', background: C.primary, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="send" size={20} stroke={2} fill="currentColor" />
        </div>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 8. Health Report — monthly adherence + AI insights
// ────────────────────────────────────────────────────────────────
function ScreenReport() {
  // mini bar chart data
  const bars = [88, 92, 95, 100, 100, 75, 60, 100, 100, 92, 88, 50, 75, 95, 100, 100, 100, 92, 75, 100, 100, 100, 25, 95];
  const max = 100;

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="건강 리포트"
        right={<Icon name="share" size={22} stroke={1.8} />}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 80 }}>
        {/* month selector */}
        <div style={{ ...padX, paddingTop: 4, paddingBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 12, color: C.alt, fontWeight: 600, letterSpacing: '0.04em' }}>할머니 · 박○○님</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 13, fontWeight: 600 }}>
            2025 · 11월 <Icon name="chevronD" size={16} stroke={2.2} />
          </div>
        </div>

        {/* score hero */}
        <div style={padX}>
          <div style={{
            background: '#fff', borderRadius: 20, padding: 20,
            border: `1px solid ${C.line}`,
            display: 'flex', gap: 20, alignItems: 'center',
          }}>
            {/* ring */}
            <div style={{ width: 104, height: 104, position: 'relative' }}>
              <svg width="104" height="104" viewBox="0 0 104 104">
                <circle cx="52" cy="52" r="44" fill="none" stroke="var(--fill-strong)" strokeWidth="10" />
                <circle cx="52" cy="52" r="44" fill="none" stroke="var(--primary-normal)" strokeWidth="10"
                  strokeLinecap="round" strokeDasharray={`${2 * Math.PI * 44 * 0.92} 999`}
                  transform="rotate(-90 52 52)" />
              </svg>
              <div style={{
                position: 'absolute', inset: 0, display: 'flex',
                flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
              }}>
                <div style={{ fontSize: 28, fontWeight: 700, letterSpacing: '-0.025em', color: C.text }}>92</div>
                <div style={{ fontSize: 10, color: C.alt, fontWeight: 600 }}>/ 100</div>
              </div>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--c-green-40)', letterSpacing: '0.04em' }}>훌륭해요</div>
              <div style={{ fontSize: 18, fontWeight: 700, marginTop: 4, letterSpacing: '-0.015em', lineHeight: '24px' }}>
                지난달보다 <span style={{ color: C.positive }}>+8점</span> 향상됐어요
              </div>
              <div style={{ fontSize: 12, color: C.muted, marginTop: 6, lineHeight: '17px' }}>
                평균 복약률 92% · 144회 중 132회 복용
              </div>
            </div>
          </div>
        </div>

        {/* daily adherence bar chart */}
        <div style={{ ...padX, paddingTop: 20 }}>
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, padding: 18 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: '-0.012em' }}>일별 복약률</div>
              <div style={{ fontSize: 11, color: C.alt }}>11.1 → 11.24</div>
            </div>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 3, height: 100, marginTop: 20, paddingBottom: 4 }}>
              {bars.map((v, i) => {
                const h = (v / max) * 88 + 4;
                const c = v >= 90 ? C.positive : v >= 70 ? C.cautionary : C.negative;
                return (
                  <div key={i} style={{ flex: 1, height: h, background: c, borderRadius: 2, opacity: i === bars.length - 1 ? 1 : 0.85 }} />
                );
              })}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: C.alt, marginTop: 6, fontWeight: 600 }}>
              <span>1일</span><span>8일</span><span>15일</span><span>22일</span>
            </div>
          </div>
        </div>

        {/* AI insight cards */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 8, fontSize: 16, fontWeight: 700, letterSpacing: '-0.012em' }}>
          AI 분석
        </div>
        <div style={{ ...padX, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {/* pattern */}
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--c-orange-95)', color: 'var(--c-orange-40)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="warn" size={16} stroke={2.2} />
              </div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--c-orange-40)', letterSpacing: '0.06em', textTransform: 'uppercase' }}>주의 패턴</div>
            </div>
            <div style={{ fontSize: 16, fontWeight: 700, marginTop: 10, letterSpacing: '-0.012em', lineHeight: '22px' }}>
              저녁약을 자주 빠뜨려요
            </div>
            <div style={{ fontSize: 13, color: C.muted, marginTop: 4, lineHeight: '20px' }}>
              지난 30일 중 7일(23%) 저녁 메트포르민 복용을 건너뛰셨어요. 식사 시간이 일정하지 않은 날에 집중돼 있어요.
            </div>
          </div>

          {/* recommendation */}
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, padding: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--c-violet-95)', color: 'var(--c-violet-45)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="sparkle" size={16} stroke={2} fill="currentColor" />
              </div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--c-violet-45)', letterSpacing: '0.06em', textTransform: 'uppercase' }}>맞춤 추천</div>
            </div>
            <div style={{ fontSize: 16, fontWeight: 700, marginTop: 10, letterSpacing: '-0.012em', lineHeight: '22px' }}>
              당뇨·고혈압 환자를 위한 저나트륨 식단
            </div>
            <div style={{ fontSize: 13, color: C.muted, marginTop: 4, lineHeight: '20px' }}>
              처방 약을 분석해 가장 적합한 식단을 골랐어요. 대한당뇨병학회 가이드라인 기반.
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, color: C.primary, fontWeight: 600, fontSize: 13 }}>
              식단 7개 보기 <Icon name="chevronR" size={16} stroke={2.2} />
            </div>
          </div>
        </div>
      </div>

      <TabBar active="" />
    </div>
  );
}

// ────────────────────────────────────────────────────────────────
// 9. Care Group — family management + invite
// ────────────────────────────────────────────────────────────────
function ScreenGroup() {
  const members = [
    { name: '박순자', sub: '환자 · 만 72세', role: '환자', tint: '#FF7B2E', online: true },
    { name: '김민지', sub: '딸 · 본인', role: '보호자', tint: '#0066FF', online: true, me: true },
    { name: '김지훈', sub: '아들', role: '보호자', tint: '#6541F2', online: false },
  ];

  return (
    <div style={{ ...screenStyle, background: C.bgAlt }}>
      <TopBar
        left={<Icon name="chevronL" size={26} stroke={2} />}
        title="케어 그룹"
        right={<Icon name="settings" size={22} stroke={1.8} />}
      />

      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 80 }}>
        {/* group hero */}
        <div style={padX}>
          <div style={{
            background: '#fff', borderRadius: 20, padding: 22,
            border: `1px solid ${C.line}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              {/* group icon — overlapped avatars */}
              <div style={{ position: 'relative', width: 64, height: 44 }}>
                <div style={{ position: 'absolute', left: 0, top: 0 }}><Avatar name="박" tint="#FF7B2E" size={44} /></div>
                <div style={{ position: 'absolute', left: 20, top: 0 }}><Avatar name="민" tint="#0066FF" size={44} /></div>
                <div style={{ position: 'absolute', left: 40, top: 0, width: 44, height: 44, borderRadius: '50%', background: '#fff', border: `1.5px solid ${C.line}`, color: C.alt, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 700 }}>+1</div>
              </div>
              <div style={{ flex: 1, marginLeft: 28 }}>
                <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.015em' }}>할머니 댁</div>
                <div style={{ fontSize: 13, color: C.muted, marginTop: 2 }}>3명 · 보호자 2 · 환자 1</div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
              <div style={{ flex: 1, height: 42, borderRadius: 10, background: C.text, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 14, fontWeight: 600 }}>
                <Icon name="plus" size={18} stroke={2.2} />
                초대하기
              </div>
              <div style={{ width: 42, height: 42, borderRadius: 10, background: C.fill, color: C.text, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="qr" size={20} stroke={2} />
              </div>
            </div>
          </div>
        </div>

        {/* members list */}
        <div style={{ ...padX, paddingTop: 24, paddingBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 11, color: C.alt, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase' }}>구성원 · 3</div>
        </div>
        <div style={padX}>
          <div style={{ background: '#fff', borderRadius: 16, border: `1px solid ${C.line}`, overflow: 'hidden' }}>
            {members.map((m, i) => (
              <div key={i} style={{
                display: 'flex', alignItems: 'center', gap: 12,
                padding: '14px 16px',
                borderTop: i === 0 ? 'none' : `1px solid ${C.line}`,
              }}>
                <div style={{ position: 'relative' }}>
                  <Avatar name={m.name[0]} tint={m.tint} size={44} />
                  {m.online && <div style={{ position: 'absolute', right: -1, bottom: -1, width: 12, height: 12, borderRadius: '50%', background: C.positive, border: '2px solid #fff' }} />}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <div style={{ fontSize: 15, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>{m.name}</div>
                    {m.me && <div style={{ fontSize: 10, color: C.alt, padding: '2px 6px', background: C.fill, borderRadius: 4, fontWeight: 600 }}>나</div>}
                  </div>
                  <div style={{ fontSize: 12, color: C.alt, marginTop: 1 }}>{m.sub}</div>
                </div>
                <div style={{
                  padding: '4px 10px', borderRadius: 6, fontSize: 11, fontWeight: 600,
                  background: m.role === '환자' ? 'var(--c-orange-95)' : 'var(--c-blue-95)',
                  color: m.role === '환자' ? 'var(--c-orange-40)' : 'var(--c-blue-45)',
                }}>{m.role}</div>
              </div>
            ))}
          </div>
        </div>

        {/* invite code */}
        <div style={{ ...padX, paddingTop: 24 }}>
          <div style={{
            background: '#fff', borderRadius: 16, padding: 18,
            border: `1px dashed ${C.line}`,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <div style={{ fontSize: 11, color: C.alt, fontWeight: 600, letterSpacing: '0.04em' }}>초대 코드</div>
                <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: '0.08em', marginTop: 4, fontFamily: 'var(--font-mono)' }}>3F9-K2P</div>
              </div>
              <div style={{ padding: '8px 14px', borderRadius: 9999, background: C.fill, fontSize: 13, color: C.text, fontWeight: 600 }}>복사</div>
            </div>
            <div style={{ fontSize: 12, color: C.alt, marginTop: 8, lineHeight: '17px' }}>
              유효 시간 23분 · 가족에게 코드 또는 QR을 전송하세요.
            </div>
          </div>
        </div>

        {/* ── Group activity feed (scroll-down area) ── */}
        <div style={{ ...padX, paddingTop: 28, paddingBottom: 4, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: C.text, letterSpacing: '-0.01em' }}>그룹 활동</div>
          <div style={{ fontSize: 12, color: C.primary, fontWeight: 600 }}>전체보기</div>
        </div>
        <div style={{ ...padX, paddingBottom: 12, fontSize: 11, color: C.alt }}>최근 일주일</div>

        <GroupActivityTimeline />
      </div>

      <TabBar active="group" />
    </div>
  );
}

Object.assign(window, { ScreenDetail, ScreenSchedule, ScreenChat, ScreenReport, ScreenGroup, GroupActivityTimeline });

// ────────────────────────────────────────────────────────────────
// GroupActivityTimeline — compact activity feed shown inside group detail.
// ────────────────────────────────────────────────────────────────
function GroupActivityTimeline() {
  const events = [
    {
      who: '박순자', whoLabel: '할머니', tint: '#FF7B2E', time: '오늘 12:34',
      kind: 'done', title: '점심약 2개를 복용했어요',
      detail: ['메트포르민 500mg', '글리메피리드 2mg'],
      pills: ['orange', 'white'],
    },
    {
      who: 'PillMate AI', whoLabel: 'AI', tint: '#6541F2', time: '오늘 09:10',
      kind: 'ai', title: '저녁약 미복용 패턴',
      detail: '지난 7일 중 3일 빠뜨리셨어요. 알림 시간을 조정해볼까요?',
      cta: '알림 조정',
    },
    {
      who: '김민지', whoLabel: '딸', tint: '#0066FF', time: '오늘 07:40',
      kind: 'rx', title: '새 처방전을 등록했어요',
      detail: '내과 진료 · 약 5개 추가',
    },
    {
      who: '박순자', whoLabel: '할머니', tint: '#FF7B2E', time: '어제 22:30',
      kind: 'miss', title: '취침 전 약을 놓치셨어요',
      detail: ['오메가-3 1000mg'],
      pills: ['yellow'],
    },
    {
      who: '김지훈', whoLabel: '아들', tint: '#6541F2', time: '어제 20:14',
      kind: 'note', title: '메모를 남겼어요',
      detail: '"엄마, 오늘 어지러우셨다고 하셨어요. 다음 진료에서 여쭤봐요."',
    },
    {
      who: '박순자', whoLabel: '할머니', tint: '#FF7B2E', time: '어제 19:08',
      kind: 'done', title: '저녁약 1개를 복용했어요',
      detail: ['아토르바스타틴 10mg'],
      pills: ['pink'],
    },
  ];

  const dotColor = {
    done: 'var(--status-positive)',
    miss: 'var(--status-negative)',
    ai:   'var(--c-violet-45)',
    rx:   'var(--primary-normal)',
    note: 'var(--c-cyan-50)',
  };

  return (
    <div style={{ ...padX }}>
      {events.map((e, i) => (
        <div key={i} style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
          {/* rail */}
          <div style={{ width: 14, position: 'relative', flexShrink: 0 }}>
            <div style={{
              position: 'absolute', left: '50%', top: 20, bottom: i === events.length - 1 ? '50%' : -2,
              width: 2, background: C.line, transform: 'translateX(-1px)',
            }} />
            <div style={{
              position: 'absolute', left: '50%', top: 16, width: 10, height: 10,
              borderRadius: '50%', background: dotColor[e.kind] || C.muted,
              transform: 'translate(-50%, 0)',
              boxShadow: `0 0 0 3px ${C.bgAlt}`,
            }} />
          </div>

          {/* card */}
          <div style={{ flex: 1, paddingBottom: i === events.length - 1 ? 0 : 10, minWidth: 0 }}>
            <div style={{
              background: '#fff', borderRadius: 12, padding: '12px 14px',
              border: `1px solid ${C.line}`,
            }}>
              {/* head: avatar + name + time */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar name={e.who[0]} tint={e.tint} size={24} />
                <div style={{ flex: 1, minWidth: 0, fontSize: 12, color: C.text }}>
                  <b style={{ fontWeight: 700 }}>{e.who}</b>
                  <span style={{ color: C.alt, marginLeft: 4 }}>· {e.whoLabel}</span>
                </div>
                <div style={{ fontSize: 11, color: C.alt, fontWeight: 500 }}>{e.time}</div>
              </div>

              {/* title */}
              <div style={{
                fontSize: 14, fontWeight: 700, color: C.text, marginTop: 8,
                letterSpacing: '-0.01em', lineHeight: '19px',
              }}>{e.title}</div>

              {/* detail */}
              {Array.isArray(e.detail) ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, padding: '8px 10px', borderRadius: 8, background: C.bgAlt }}>
                  {e.pills && (
                    <div style={{ display: 'flex' }}>
                      {e.pills.map((p, idx) => (
                        <div key={idx} style={{ marginLeft: idx === 0 ? 0 : -6 }}>
                          <PillVisual color={p} size={22} />
                        </div>
                      ))}
                    </div>
                  )}
                  <div style={{ flex: 1, fontSize: 12, color: C.muted, lineHeight: '17px' }}>
                    {e.detail.join(' · ')}
                  </div>
                </div>
              ) : (
                <div style={{ fontSize: 13, color: C.muted, marginTop: 4, lineHeight: '19px' }}>
                  {e.detail}
                </div>
              )}

              {/* cta */}
              {e.cta && (
                <div style={{ marginTop: 10 }}>
                  <div style={{
                    display: 'inline-flex', padding: '6px 12px', borderRadius: 8,
                    background: C.text, color: '#fff',
                    fontSize: 12, fontWeight: 600,
                  }}>{e.cta}</div>
                </div>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
