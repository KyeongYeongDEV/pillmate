export const colors = {
  primary: "#208AEF",
  danger: "#DC2626",
  warning: "#F59E0B",
  text: "#0F172A",
  muted: "#64748B",
  bg: "#FFFFFF",
  card: "#F8FAFC",
  border: "#E2E8F0",
} as const;

export type ColorToken = keyof typeof colors;
