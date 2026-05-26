import { View, type ViewProps } from "react-native";

export type CardProps = ViewProps;

export function Card({ className, ...rest }: CardProps) {
  return (
    <View
      {...rest}
      className={`bg-card border border-border rounded-2xl p-4 shadow-sm ${className ?? ""}`}
    />
  );
}
