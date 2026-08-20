import { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import { router } from 'expo-router';
import * as SecureStore from 'expo-secure-store';
import { getToken, ONBOARDING_SEEN_KEY } from '@/lib/auth/storage';
import { refreshSessionIfNeeded } from '@/lib/auth/refreshSession';
import { colors } from '@/styles/tokens';

export default function Index() {
  useEffect(() => {
    boot();
  }, []);

  return <View style={styles.splash} />;
}

async function boot() {
  const token = await getToken();
  if (token) {
    router.replace('/(tabs)/home');
    void refreshSessionIfNeeded(); // 화면전환은 그대로, 갱신은 백그라운드로
    return;
  }
  const seen = await SecureStore.getItemAsync(ONBOARDING_SEEN_KEY);
  if (seen === 'true') {
    router.replace('/(auth)/login');
  } else {
    router.replace('/(auth)/onboarding');
  }
}

const styles = StyleSheet.create({
  splash: { flex: 1, backgroundColor: colors.bgNormal },
});
