import { router } from 'expo-router';

const DEFAULT_FALLBACK = '/(tabs)/home';

export function safeBack(fallback: string = DEFAULT_FALLBACK): void {
  if (router.canGoBack()) {
    router.back();
    return;
  }
  router.replace(fallback as any);
}
