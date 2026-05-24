/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        primary: "#208AEF",
        danger: "#DC2626",
        warning: "#F59E0B",
        text: "#0F172A",
        muted: "#64748B",
        bg: "#FFFFFF",
        card: "#F8FAFC",
      },
      fontSize: {
        // 노인 친화 폰트 사이즈 (최소 16, h1=28)
        base: ["16px", { lineHeight: "24px" }],
        lg: ["18px", { lineHeight: "27px" }],
        xl: ["22px", { lineHeight: "30px" }],
        "2xl": ["28px", { lineHeight: "36px" }],
      },
    },
  },
  plugins: [],
};
