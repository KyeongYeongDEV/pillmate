import { AppState } from 'react-native';
import { appStateFocusListener } from '@/store/appStateListener';

const onFocus = jest.fn(() => ({ type: 'focus' }));
const onFocusLost = jest.fn(() => ({ type: 'focusLost' }));
const onOnline = jest.fn(() => ({ type: 'online' }));
const onOffline = jest.fn(() => ({ type: 'offline' }));
const actions = { onFocus, onFocusLost, onOnline, onOffline } as any;

function subscribe() {
  const dispatch = jest.fn();
  const remove = jest.fn();
  let handler: (state: string) => void = () => {};
  const addEventListener = jest
    .spyOn(AppState, 'addEventListener')
    .mockImplementation(((_event: string, cb: (state: string) => void) => {
      handler = cb;
      return { remove } as any;
    }) as any);
  const unsubscribe = appStateFocusListener(dispatch, actions) as () => void;
  return { dispatch, remove, unsubscribe, addEventListener, emit: (state: string) => handler(state) };
}

describe('appStateFocusListener (RN AppState ↔ RTK Query)', () => {
  afterEach(() => jest.restoreAllMocks());
  beforeEach(() => jest.clearAllMocks());

  it("AppState 'change' 구독", () => {
    const { addEventListener } = subscribe();
    expect(addEventListener).toHaveBeenCalledWith('change', expect.any(Function));
  });

  it("'active' 전환 시 onFocus dispatch", () => {
    const { dispatch, emit } = subscribe();
    emit('active');
    expect(onFocus).toHaveBeenCalled();
    expect(dispatch).toHaveBeenCalledWith({ type: 'focus' });
  });

  it("'background' 전환 시 onFocusLost dispatch", () => {
    const { dispatch, emit } = subscribe();
    emit('background');
    expect(onFocusLost).toHaveBeenCalled();
    expect(dispatch).toHaveBeenCalledWith({ type: 'focusLost' });
  });

  it('반환된 cleanup 이 구독을 해제', () => {
    const { remove, unsubscribe } = subscribe();
    unsubscribe();
    expect(remove).toHaveBeenCalledTimes(1);
  });
});
