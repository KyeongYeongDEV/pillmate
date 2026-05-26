import React from 'react';
import { View, Text } from 'react-native';

interface AvatarProps {
  name: string;
  tint: string;
  size?: number;
}

function Avatar({ name, tint, size = 40 }: AvatarProps) {
  const fontSize = Math.round(size * 0.38);
  return (
    <View style={{
      width: size, height: size, borderRadius: size / 2,
      backgroundColor: tint, alignItems: 'center', justifyContent: 'center',
    }}>
      <Text style={{ fontSize, color: '#fff', fontWeight: '700' }}>{name}</Text>
    </View>
  );
}

export default React.memo(Avatar);
