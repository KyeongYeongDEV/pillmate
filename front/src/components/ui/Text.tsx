import { Text as RNText, type TextProps as RNTextProps } from "react-native";

export type TextProps = RNTextProps & {
  // 한글 가독성 위해 line-height 1.5 기본.
  muted?: boolean;
};

export function Text({ muted, className, ...rest }: TextProps) {
  const tone = muted ? "text-muted" : "text-text";
  return (
    <RNText
      {...rest}
      className={`text-base leading-6 ${tone} ${className ?? ""}`}
    />
  );
}
