import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Text } from '@/components/ui/Text';
import { Heading } from '@/components/ui/Heading';

export default function ChatScreen() {
  return (
    <SafeAreaView edges={['top']} className="flex-1 bg-bg">
      <View className="p-4 gap-2">
        <Heading level={2}>AI 상담</Heading>
        <Text muted>약 정보와 복약 관련 궁금한 점을 물어보세요.</Text>
      </View>
    </SafeAreaView>
  );
}
