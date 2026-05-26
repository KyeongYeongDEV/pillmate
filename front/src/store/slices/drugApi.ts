import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { Drug, DrugDetail, DrugSearchResult } from '@/types/prescription';

// Phase 1 mock data — swap queryFn for real fetch in Phase 2 once backend is ready
const MOCK_DRUGS: DrugSearchResult[] = [
  { id: 1,  kdCode: '670500700', name: '암로디핀정 5mg',          company: '한미약품',   imageUrl: null },
  { id: 2,  kdCode: '670500701', name: '암로디핀베실산염정 5mg',  company: '대웅제약',   imageUrl: null },
  { id: 3,  kdCode: '670500702', name: '암로핀정 10mg',           company: '종근당',     imageUrl: null },
  { id: 4,  kdCode: '670500703', name: '메트포르민정 500mg',      company: '동아제약',   imageUrl: null },
  { id: 5,  kdCode: '670500704', name: '글리메피리드정 2mg',      company: '한림제약',   imageUrl: null },
  { id: 6,  kdCode: '670500705', name: '아토르바스타틴정 10mg',   company: '한국화이자', imageUrl: null },
  { id: 7,  kdCode: '670500706', name: '오메가-3 지방산 1000mg',  company: '일동제약',   imageUrl: null },
  { id: 8,  kdCode: '670500707', name: '노바스크정 5mg',          company: '한국화이자', imageUrl: null },
  { id: 9,  kdCode: '670500708', name: '카나브정 30mg',           company: '보령제약',   imageUrl: null },
  { id: 10, kdCode: '670500709', name: '에소메프라졸캡슐 20mg',   company: '동아에스티', imageUrl: null },
];

const DRUG_EN_NAMES: Record<string, string> = {
  '670500700': 'Amlodipine Besylate',
  '670500701': 'Amlodipine Besylate',
  '670500702': 'Amlodipine Besylate',
  '670500703': 'Metformin HCl',
  '670500704': 'Glimepiride',
  '670500705': 'Atorvastatin Calcium',
  '670500706': 'Omega-3 Fatty Acids',
  '670500707': 'Amlodipine Besylate',
  '670500708': 'Fimasartan',
  '670500709': 'Esomeprazole Magnesium',
};

const DRUG_CATEGORIES: Record<string, string> = {
  '670500700': '혈압강하제 · CCB',
  '670500701': '혈압강하제 · CCB',
  '670500702': '혈압강하제 · CCB',
  '670500703': '당뇨 치료제 · 비구아니드',
  '670500704': '당뇨 치료제 · 설포닐우레아',
  '670500705': '고지혈증 치료제 · 스타틴',
  '670500706': '건강기능식품 · 오메가-3',
  '670500707': '혈압강하제 · CCB',
  '670500708': '혈압강하제 · ARB',
  '670500709': '소화성 궤양 치료제 · PPI',
};

const DRUG_EFFICACY: Record<string, string[]> = {
  '670500700': ['본태성 고혈압', '관상동맥질환에 의한 만성 안정형 협심증', '혈관경련성 협심증 (이형 협심증)'],
  '670500703': ['제2형 당뇨병 혈당 조절', '인슐린 저항성 개선', '체중 중립적 혈당 강하'],
  '670500705': ['고콜레스테롤혈증', '혼합형 이상지혈증', '죽상동맥경화증 예방'],
};

const DRUG_INTERACTIONS: Record<string, { kdCode: string; name: string; category: string; description: string }[]> = {
  '670500700': [
    {
      kdCode: 'DDI001',
      name: '이트라코나졸',
      category: '항진균제',
      description: '혈중 농도가 상승해 부작용 위험이 커집니다. 처방의와 반드시 상의하세요.',
    },
  ],
  '670500707': [
    {
      kdCode: 'DDI002',
      name: '와파린',
      category: '항응고제',
      description: '오메가-3는 항응고 작용을 강화할 수 있습니다. 출혈 위험에 주의하세요.',
    },
  ],
};

const DRUG_SIDE_EFFECTS: Record<string, { name: string; rate: number }[]> = {
  '670500700': [
    { name: '두통', rate: 0.12 }, { name: '안면홍조', rate: 0.08 },
    { name: '하지부종', rate: 0.07 }, { name: '어지러움', rate: 0.04 },
    { name: '심계항진', rate: 0.02 }, { name: '피로감', rate: 0.01 },
  ],
  '670500703': [
    { name: '소화불량', rate: 0.15 }, { name: '구역', rate: 0.10 },
    { name: '설사', rate: 0.08 }, { name: '복통', rate: 0.05 },
  ],
  '670500705': [
    { name: '근육통', rate: 0.06 }, { name: '두통', rate: 0.04 },
    { name: '소화불량', rate: 0.03 }, { name: '피로감', rate: 0.02 },
  ],
};

export const drugApiSlice = createApi({
  reducerPath: 'drugApi',
  baseQuery: fetchBaseQuery({ baseUrl: '' }),
  keepUnusedDataFor: 60,
  endpoints: (build) => ({
    searchDrugs: build.query<DrugSearchResult[], string>({
      queryFn: async (q) => {
        if (!q.trim()) return { data: [] };
        const lower = q.toLowerCase();
        const data = MOCK_DRUGS.filter(d =>
          d.name.toLowerCase().includes(lower) ||
          (d.company?.toLowerCase().includes(lower) ?? false),
        );
        return { data };
      },
    }),
    getDrugDetail: build.query<DrugDetail, string>({
      queryFn: async (kdCode) => {
        const found = MOCK_DRUGS.find(d => d.kdCode === kdCode);
        if (!found) return { error: { status: 404, data: 'Not found' } };
        const detail: DrugDetail = {
          id: found.id,
          kdCode: found.kdCode,
          name: found.name,
          englishName: DRUG_EN_NAMES[found.kdCode] ?? null,
          category: DRUG_CATEGORIES[found.kdCode] ?? null,
          ingredient: null,
          company: found.company,
          imageUrl: found.imageUrl,
          efficacy: DRUG_EFFICACY[found.kdCode] ?? ['식약처 데이터 준비 중입니다.'],
          dosage: ['의사·약사의 처방에 따라 복용하세요.'],
          warnings: ['임산부·수유부는 복용 전 의사와 상담하세요.', '신장·간 질환자는 용량 조절이 필요할 수 있습니다.'],
          interactions: DRUG_INTERACTIONS[found.kdCode] ?? [],
          sideEffects: DRUG_SIDE_EFFECTS[found.kdCode] ?? [],
          source: '식품의약품안전처 의약품안전나라',
          updatedAt: '2025.10.18',
        };
        return { data: detail };
      },
    }),
  }),
});

export const {
  useLazySearchDrugsQuery,
  useSearchDrugsQuery,
  useGetDrugDetailQuery,
} = drugApiSlice;
