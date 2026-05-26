import { ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Heading } from "@/components/ui/Heading";
import { Text } from "@/components/ui/Text";
import { MFDS_SOURCE } from "@/lib/constants";

export default function HomeScreen() {
  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-bg">
      <ScrollView contentContainerClassName="p-4 gap-4">
        <View className="gap-1">
          <Heading level={1}>PillMate</Heading>
          <Text muted>오늘 복용하실 약을 확인해 주세요.</Text>
        </View>

        <Card>
          <View className="gap-2">
            <Heading level={3}>아침 · 08:00</Heading>
            <Text>혈압약 1정, 위장약 1정</Text>
            <Text muted>출처: {MFDS_SOURCE}</Text>
            <Button title="복용 완료" variant="primary" size="lg" />
          </View>
        </Card>

        <Card>
          <View className="gap-2">
            <Heading level={3}>저녁 · 19:00</Heading>
            <Text>혈압약 1정</Text>
            <Text muted>출처: {MFDS_SOURCE}</Text>
            <Button title="복용 완료" variant="secondary" size="lg" />
          </View>
        </Card>
      </ScrollView>
    </SafeAreaView>
  );
}
