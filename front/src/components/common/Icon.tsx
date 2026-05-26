import React from 'react';
import { Feather } from '@expo/vector-icons';
import { Ionicons } from '@expo/vector-icons';

export type IconName =
  | 'home'
  | 'calendar'
  | 'add'
  | 'chat'
  | 'people'
  | 'bell'
  | 'chevronDown'
  | 'sparkles'
  | 'check';

interface IconProps {
  name: IconName;
  size?: number;
  color?: string;
}

type Lib = 'feather' | 'ionicons';

const ICON_MAP: Record<IconName, { lib: Lib; icon: string }> = {
  home:        { lib: 'feather',  icon: 'home' },
  calendar:    { lib: 'feather',  icon: 'calendar' },
  add:         { lib: 'ionicons', icon: 'add' },
  chat:        { lib: 'feather',  icon: 'message-circle' },
  people:      { lib: 'feather',  icon: 'users' },
  bell:        { lib: 'feather',  icon: 'bell' },
  chevronDown: { lib: 'feather',  icon: 'chevron-down' },
  sparkles:    { lib: 'ionicons', icon: 'sparkles' },
  check:       { lib: 'ionicons', icon: 'checkmark' },
};

function Icon({ name, size = 24, color = '#000' }: IconProps) {
  const { lib, icon } = ICON_MAP[name];
  if (lib === 'ionicons') {
    return <Ionicons name={icon as any} size={size} color={color} />;
  }
  return <Feather name={icon as any} size={size} color={color} />;
}

export default React.memo(Icon);
