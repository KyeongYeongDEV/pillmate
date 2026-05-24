import { Pressable, Text, type PressableProps } from "react-native";

export type ButtonVariant = "primary" | "secondary" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export type ButtonProps = Omit<PressableProps, "children"> & {
  title: string;
  variant?: ButtonVariant;
  size?: ButtonSize;
};

const CONTAINER_VARIANT: Record<ButtonVariant, string> = {
  primary: "bg-primary",
  secondary: "bg-card border border-border",
  danger: "bg-danger",
};

const TEXT_VARIANT: Record<ButtonVariant, string> = {
  primary: "text-white",
  secondary: "text-text",
  danger: "text-white",
};

const CONTAINER_SIZE: Record<ButtonSize, string> = {
  sm: "px-3 py-2 rounded-lg",
  md: "px-4 py-3 rounded-xl",
  lg: "px-5 py-4 rounded-2xl",
};

const TEXT_SIZE: Record<ButtonSize, string> = {
  sm: "text-base",
  md: "text-lg",
  // 노인 사용자 주요 액션은 lg 권장.
  lg: "text-xl",
};

export function Button({
  title,
  variant = "primary",
  size = "md",
  disabled,
  accessibilityLabel,
  ...rest
}: ButtonProps) {
  const disabledStyle = disabled ? "opacity-50" : "";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityState={{ disabled: !!disabled }}
      disabled={disabled}
      {...rest}
      className={`items-center justify-center ${CONTAINER_VARIANT[variant]} ${CONTAINER_SIZE[size]} ${disabledStyle}`}
    >
      <Text className={`font-semibold ${TEXT_VARIANT[variant]} ${TEXT_SIZE[size]}`}>
        {title}
      </Text>
    </Pressable>
  );
}
