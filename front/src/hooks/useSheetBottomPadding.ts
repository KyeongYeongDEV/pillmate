import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { space } from '@/styles/tokens';

// 바텀시트 하단 패딩을 시스템 네비바(safe-area) 위로 확보. 인셋 없으면 base 유지.
export function useSheetBottomPadding(base: number = space.s24, margin: number = space.s16): number {
  const insets = useSafeAreaInsets();
  return Math.max(base, insets.bottom + margin);
}
