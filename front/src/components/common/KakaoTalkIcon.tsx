import React from 'react';
import Svg, { Path } from 'react-native-svg';

interface Props {
  size?: number;
  color?: string;
}

// 카카오톡 공식 말풍선 심볼 (SSO 버튼용). 노란 버튼 위 검은 말풍선.
function KakaoTalkIcon({ size = 24, color = '#191600' }: Props) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityLabel="카카오톡">
      <Path
        fill={color}
        d="M12 3.5C6.753 3.5 2.5 6.79 2.5 10.85c0 2.63 1.79 4.94 4.48 6.25-.2.72-.72 2.62-.82 3.03-.13.51.19.5.39.37.16-.11 2.5-1.7 3.52-2.39.79.12 1.6.18 2.43.18 5.247 0 9.5-3.29 9.5-7.35S17.247 3.5 12 3.5z"
      />
    </Svg>
  );
}

export default React.memo(KakaoTalkIcon);
