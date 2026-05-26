import React, { useCallback } from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import type { BottomTabBarProps } from 'expo-router/build/react-navigation/bottom-tabs';
import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { colors, space, typography } from '@/styles/tokens';
import FabButton from './FabButton';

type TabIconProps = {
  name: string;
  focused: boolean;
  label: string;
};

const TAB_ICONS: Record<string, { icon: string; label: string }> = {
  home: { icon: 'home', label: '홈' },
  drugs: { icon: 'search', label: '약' },
  groups: { icon: 'users', label: '그룹' },
  my: { icon: 'user', label: 'MY' },
};

function TabIcon({ name, focused, label }: TabIconProps) {
  const color = focused ? colors.primaryNormal : colors.labelAlternative;
  return (
    <View style={styles.tabItem}>
      <Feather name={name as any} size={22} color={color} />
      <Text style={[styles.tabLabel, focused && styles.tabLabelActive]}>
        {label}
      </Text>
    </View>
  );
}

function CustomTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  const handleTabPress = useCallback(async (routeName: string, isFocused: boolean) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (!isFocused) {
      navigation.navigate(routeName);
    }
  }, [navigation]);

  const handleFabPress = useCallback(() => {
    router.push('/prescription' as any);
  }, []);

  const visibleRoutes = state.routes.filter(r => r.name !== 'register-fab');

  const leftRoutes = visibleRoutes.slice(0, 2);
  const rightRoutes = visibleRoutes.slice(2);

  const isFocused = (routeName: string) =>
    state.routes[state.index]?.name === routeName;

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      <View style={styles.bar}>
        {/* Left 2 tabs */}
        {leftRoutes.map((route) => {
          const tabInfo = TAB_ICONS[route.name];
          if (!tabInfo) return null;
          const focused = isFocused(route.name);
          return (
            <Pressable
              key={route.key}
              style={styles.tabButton}
              onPress={() => handleTabPress(route.name, focused)}
              accessibilityLabel={tabInfo.label}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
            >
              <TabIcon name={tabInfo.icon} focused={focused} label={tabInfo.label} />
            </Pressable>
          );
        })}

        {/* Center FAB */}
        <View style={styles.fabSlot}>
          <FabButton onPress={handleFabPress} />
        </View>

        {/* Right 2 tabs */}
        {rightRoutes.map((route) => {
          const tabInfo = TAB_ICONS[route.name];
          if (!tabInfo) return null;
          const focused = isFocused(route.name);
          return (
            <Pressable
              key={route.key}
              style={styles.tabButton}
              onPress={() => handleTabPress(route.name, focused)}
              accessibilityLabel={tabInfo.label}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
            >
              <TabIcon name={tabInfo.icon} focused={focused} label={tabInfo.label} />
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export default React.memo(CustomTabBar);

const styles = StyleSheet.create({
  container: {
    backgroundColor: colors.bgNormal,
    borderTopWidth: 1,
    borderTopColor: colors.line,
    // Shadow for iOS
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 8,
  },
  bar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    height: 56,
    paddingHorizontal: space.s8,
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    height: 56,
  },
  tabItem: {
    alignItems: 'center',
    gap: 2,
  },
  tabLabel: {
    fontSize: 10,
    color: colors.labelAlternative,
    fontWeight: '500',
  },
  tabLabelActive: {
    color: colors.primaryNormal,
    fontWeight: '700',
  },
  fabSlot: {
    width: 80,
    alignItems: 'center',
    justifyContent: 'flex-end',
    height: 56,
  },
});
