import { View } from 'react-native';
import { Tabs } from 'expo-router';
import CustomTabBar from '@/components/navigation/CustomTabBar';

export default function TabsLayout() {
  return (
    <View style={{ flex: 1 }}>
      <Tabs tabBar={(props) => <CustomTabBar {...props} />}>
        <Tabs.Screen name="home"          options={{ title: '홈',     headerShown: false }} />
        <Tabs.Screen name="schedule"      options={{ title: '복약',   headerShown: false }} />
        <Tabs.Screen name="register-fab"  options={{ title: '약봉투', headerShown: false }} />
        <Tabs.Screen
          name="group"
          options={{ title: '그룹', headerShown: false }}
          listeners={({ navigation }) => ({
            // 그룹 탭 누를 때마다 목록(index)으로 reset — 상세에 있었어도 목록 표시
            tabPress: () => navigation.navigate('group', { screen: 'index' }),
          })}
        />
        <Tabs.Screen name="prescriptions" options={{ title: '약봉투', headerShown: false, tabBarButton: () => null }} />
        <Tabs.Screen name="chat"          options={{ title: '상담',   headerShown: false, tabBarButton: () => null }} />
        <Tabs.Screen name="my"            options={{ title: '설정',   headerShown: false, tabBarButton: () => null }} />
      </Tabs>
    </View>
  );
}
