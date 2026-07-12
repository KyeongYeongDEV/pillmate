import { useEffect, useState } from 'react';

// count 개 항목을 intervalMs 마다 순환하는 인덱스. 1개 이하면 타이머 없이 0 고정.
export function useRotatingIndex(count: number, intervalMs: number): number {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    setIndex(0);
    if (count <= 1) return;

    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % count);
    }, intervalMs);

    return () => clearInterval(timer);
  }, [count, intervalMs]);

  return count > 0 ? index % count : 0;
}
