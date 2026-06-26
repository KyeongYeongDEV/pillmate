import { View } from 'react-native';
import { Tabs } from 'expo-router';
import CustomTabBar from '@/components/navigation/CustomTabBar';
import PrescriptionFab from '@/components/navigation/PrescriptionFab';

export default function TabsLayout() {
  return (
    <View style={{ flex: 1 }}>
      <Tabs tabBar={(props) => <CustomTabBar {...props} />}>
        <Tabs.Screen name="home"         options={{ title: '홈',   headerShown: false }} />
        <Tabs.Screen name="prescriptions" options={{ title: '약봉투', headerShown: false }} />
        <Tabs.Screen name="schedule"     options={{ title: '복약', headerShown: false }} />
        <Tabs.Screen
          name="register-fab"
          options={{ title: '', headerShown: false, tabBarButton: () => null }}
        />
        <Tabs.Screen name="chat"         options={{ title: '상담', headerShown: false }} />
        <Tabs.Screen name="group"        options={{ title: '그룹', headerShown: false }} />
        <Tabs.Screen name="my"           options={{ title: '설정', headerShown: false }} />
      </Tabs>

      {/* FAB rendered outside the Tabs navigator so it's never clipped by the bar container */}
      <PrescriptionFab />
    </View>
  );
}
