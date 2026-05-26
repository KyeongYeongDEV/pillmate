import React from 'react';
import { Text } from 'react-native';
import { colors } from '@/styles/tokens';

interface HighlightProps {
  text: string;
  term: string;
  style?: object;
}

export default function Highlight({ text, term, style }: HighlightProps) {
  if (!term.trim()) return <Text style={style}>{text}</Text>;
  const lowerText = text.toLowerCase();
  const lowerTerm = term.toLowerCase();
  const idx = lowerText.indexOf(lowerTerm);
  if (idx < 0) return <Text style={style}>{text}</Text>;
  return (
    <Text style={style}>
      {text.slice(0, idx)}
      <Text style={{ color: colors.primaryBase }}>{text.slice(idx, idx + term.length)}</Text>
      {text.slice(idx + term.length)}
    </Text>
  );
}
