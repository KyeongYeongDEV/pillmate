import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '@/components/ui/Text';
import { Heading } from '@/components/ui/Heading';

export default function ScheduleScreen() {
  return (
    <SafeAreaView edges={['top']} className="flex-1 bg-bg">
      <View className="p-4 gap-2">
        <Heading level={2}>복약 일정</Heading>
        <Text muted>복약 스케줄을 확인하고 관리하세요.</Text>
      </View>
    </SafeAreaView>
  );
}
