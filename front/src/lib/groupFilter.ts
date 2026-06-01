import type { MyGroupSummary } from '@/types/caregroup';
import type { GroupFilter } from '@/components/group/FilterChips';

const GUARDIAN_ROLES = new Set(['ADMIN', 'GUARDIAN']);
const PRIVATE_MEMBER_COUNT = 1;

export function applyGroupFilter(groups: MyGroupSummary[], filter: GroupFilter): MyGroupSummary[] {
  switch (filter) {
    case '전체': return groups;
    case '내가 환자': return groups.filter(g => g.role === 'PATIENT');
    case '내가 보호자': return groups.filter(g => GUARDIAN_ROLES.has(g.role));
    case '비공개': return groups.filter(g => g.memberCount === PRIVATE_MEMBER_COUNT);
    default: return groups;
  }
}
