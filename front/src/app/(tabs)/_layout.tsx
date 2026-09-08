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
            // 그룹 탭은 언제 눌러도 목록(index)으로 — 상세에 있었어도 목록 표시.
            // preventDefault 로 탭바의 기본 navigate('group') 를 막아야 한다. 그게 없으면
            // 여기서 index 로 보낸 직후 기본 동작이 마지막 스택 상태(상세)를 다시 복원해
            // 목록과 상세가 번갈아 나온다.
            tabPress: (e) => {
              e.preventDefault();
              navigation.navigate('group', { screen: 'index' });
            },
          })}
        />
        <Tabs.Screen name="prescriptions" options={{ title: '약봉투', headerShown: false, tabBarButton: () => null }} />
        <Tabs.Screen name="chat"          options={{ title: '상담',   headerShown: false, tabBarButton: () => null }} />
        <Tabs.Screen name="my"            options={{ title: '설정',   headerShown: false, tabBarButton: () => null }} />
      </Tabs>
    </View>
  );
}
