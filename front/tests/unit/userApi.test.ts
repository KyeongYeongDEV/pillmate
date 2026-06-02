import { userApiSlice } from '@/store/slices/userApi';

describe('userApi', () => {
  it('reducerPath 등록', () => {
    expect(userApiSlice.reducerPath).toBe('userApi');
  });

  it('registerDeviceToken mutation 존재', () => {
    expect(userApiSlice.endpoints).toHaveProperty('registerDeviceToken');
  });

  it('registerDeviceToken — initiate(body) 호출 가능', () => {
    const action = (userApiSlice.endpoints.registerDeviceToken as any).initiate({
      token: 'ExponentPushToken[xxx]',
      provider: 'EXPO',
    });
    expect(typeof action).toBe('function');
  });
});
