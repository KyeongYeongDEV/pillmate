import { Text as RNText, type TextProps as RNTextProps } from "react-native";

export type HeadingLevel = 1 | 2 | 3;

export type HeadingProps = RNTextProps & {
  level?: HeadingLevel;
};

// 노인 친화: h1=28sp/h2=22sp/h3=18sp.
const LEVEL_STYLE: Record<HeadingLevel, string> = {
  1: "text-2xl",
  2: "text-xl",
  3: "text-lg",
};

export function Heading({ level = 1, className, ...rest }: HeadingProps) {
  return (
    <RNText
      accessibilityRole="header"
      {...rest}
      className={`${LEVEL_STYLE[level]} font-bold text-text ${className ?? ""}`}
    />
  );
}
