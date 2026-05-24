import { fireEvent, render, screen } from "@testing-library/react-native";

import { Button } from "@/components/ui/Button";

describe("Button", () => {
  it("renders title and fires onPress", () => {
    const onPress = jest.fn();
    render(<Button title="복용 완료" onPress={onPress} />);

    const button = screen.getByRole("button", { name: "복용 완료" });
    fireEvent.press(button);

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it("does not fire onPress when disabled", () => {
    const onPress = jest.fn();
    render(<Button title="확인" onPress={onPress} disabled />);

    fireEvent.press(screen.getByRole("button", { name: "확인" }));

    expect(onPress).not.toHaveBeenCalled();
  });
});
