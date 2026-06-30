import { Stack } from 'expo-router';

// 그룹 탭 내부 Stack — 목록(index) → 상세([id]) → 활동([id]/activity) 이동 시
// (tabs) 탭바가 유지된다 (상세 화면에서도 네비바 노출).
export default function GroupStackLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
