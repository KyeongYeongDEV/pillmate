import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Heading } from "@/components/ui/Heading";
import { Text } from "@/components/ui/Text";

export default function PrescriptionsScreen() {
  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-bg">
      <View className="p-4 gap-2">
        <Heading level={2}>처방전</Heading>
        <Text muted>등록된 처방전이 아직 없습니다.</Text>
      </View>
    </SafeAreaView>
  );
}
