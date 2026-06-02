import React, { useCallback } from 'react';
import { View, Pressable, Text, StyleSheet } from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, space } from '@/styles/tokens';

const TAB_ICONS: Record<string, { icon: string; label: string }> = {
  home:     { icon: 'home',           label: '홈' },
  schedule: { icon: 'calendar',       label: '복약' },
  chat:     { icon: 'message-circle', label: '상담' },
  group:    { icon: 'users',          label: '그룹' },
};

interface TabIconProps { name: string; focused: boolean; label: string }

function TabIcon({ name, focused, label }: TabIconProps) {
  const color = focused ? colors.primaryNormal : colors.tabInactive;
  return (
    <View style={styles.tabItem}>
      <Feather name={name as any} size={22} color={color} />
      <Text style={[styles.tabLabel, focused && styles.tabLabelActive]}>{label}</Text>
    </View>
  );
}

function CustomTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  const handleTabPress = useCallback(async (routeName: string, isFocused: boolean) => {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (!isFocused) navigation.navigate(routeName);
  }, [navigation]);

  const isFocused = (routeName: string) => state.routes[state.index]?.name === routeName;
  const visibleRoutes = state.routes.filter(r => r.name !== 'register-fab');
  const leftRoutes = visibleRoutes.slice(0, 2);
  const rightRoutes = visibleRoutes.slice(2);

  return (
    <View style={[styles.container, { paddingBottom: insets.bottom }]}>
      <View style={styles.bar}>
        {leftRoutes.map((route) => {
          const tabDef = TAB_ICONS[route.name];
          if (!tabDef) return null;
          const focused = isFocused(route.name);
          return (
            <Pressable
              key={route.key}
              style={styles.tabButton}
              onPress={() => handleTabPress(route.name, focused)}
              accessibilityLabel={tabDef.label}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
            >
              <TabIcon name={tabDef.icon} focused={focused} label={tabDef.label} />
            </Pressable>
          );
        })}

        {/* Empty center slot — FAB is rendered as overlay in TabsLayout */}
        <View style={styles.fabSlot} />

        {rightRoutes.map((route) => {
          const tabDef = TAB_ICONS[route.name];
          if (!tabDef) return null;
          const focused = isFocused(route.name);
          return (
            <Pressable
              key={route.key}
              style={styles.tabButton}
              onPress={() => handleTabPress(route.name, focused)}
              accessibilityLabel={tabDef.label}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
            >
              <TabIcon name={tabDef.icon} focused={focused} label={tabDef.label} />
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
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 8,
  },
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingHorizontal: space.s8,
  },
  tabButton: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    height: 56,
  },
  fabSlot: {
    width: 80,
  },
  tabItem: {
    alignItems: 'center',
    gap: 2,
  },
  tabLabel: {
    fontSize: 10,
    color: colors.tabInactive,
    fontWeight: '500',
  },
  tabLabelActive: {
    color: colors.primaryNormal,
    fontWeight: '700',
  },
});
