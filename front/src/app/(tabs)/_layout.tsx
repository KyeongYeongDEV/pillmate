import { Tabs } from 'expo-router';
import CustomTabBar from '@/components/navigation/CustomTabBar';

export default function TabsLayout() {
  return (
    <Tabs tabBar={(props) => <CustomTabBar {...props} />}>
      <Tabs.Screen name="home" options={{ title: '홈', headerShown: false }} />
      <Tabs.Screen name="drugs" options={{ title: '약', headerShown: false }} />
      <Tabs.Screen
        name="register-fab"
        options={{ title: '', headerShown: false, tabBarButton: () => null }}
      />
      <Tabs.Screen name="groups" options={{ title: '그룹', headerShown: false }} />
      <Tabs.Screen name="my" options={{ title: 'MY', headerShown: false }} />
    </Tabs>
  );
}
