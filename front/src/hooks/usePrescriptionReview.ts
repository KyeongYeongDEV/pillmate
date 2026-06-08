import { useCallback, useState } from 'react';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import { addFromSearch, removeItem, replaceItem } from '@/store/slices/prescriptionFlowSlice';
import type { DrugSearchResult } from '@/types/prescription';

export interface AliasLog {
  nameRaw: string;
  toKdCode: string;
}

export function usePrescriptionReview() {
  const dispatch = useAppDispatch();
  const items = useAppSelector(s => s.prescriptionFlow.items);
  const prescriptionId = useAppSelector(s => s.prescriptionFlow.prescriptionId);
  const warnings = useAppSelector(s => s.prescriptionFlow.warnings);

  const [aliasLogs, setAliasLogs] = useState<AliasLog[]>([]);
  const [replaceTargetId, setReplaceTargetId] = useState<string | null>(null);
  const [addModalVisible, setAddModalVisible] = useState(false);

  const openReplace = useCallback((id: string) => {
    setReplaceTargetId(id);
  }, []);

  const closeReplace = useCallback(() => {
    setReplaceTargetId(null);
  }, []);

  const confirmReplace = useCallback(
    (drug: DrugSearchResult) => {
      if (!replaceTargetId) return;
      const target = items.find(i => i.id === replaceTargetId);
      if (target && target.nameRaw !== drug.name) {
        setAliasLogs(prev => [...prev, { nameRaw: target.nameRaw, toKdCode: drug.kdCode }]);
      }
      dispatch(replaceItem({
        id: replaceTargetId,
        kdCode: drug.kdCode,
        matchedName: drug.name,
        imageUrl: drug.imageUrl,
      }));
      setReplaceTargetId(null);
    },
    [replaceTargetId, items, dispatch],
  );

  const handleRemove = useCallback(
    (id: string) => {
      dispatch(removeItem(id));
    },
    [dispatch],
  );

  const openAdd = useCallback(() => setAddModalVisible(true), []);
  const closeAdd = useCallback(() => setAddModalVisible(false), []);

  const confirmAdd = useCallback(
    (drug: DrugSearchResult) => {
      dispatch(addFromSearch({
        kdCode: drug.kdCode,
        nameRaw: drug.name,
        matchedName: drug.name,
        imageUrl: drug.imageUrl,
      }));
      setAddModalVisible(false);
    },
    [dispatch],
  );

  const hasLowConfidenceItems = items.some(
    i => i.kdCode === null || (i.confidence !== null && i.confidence < 0.5),
  );

  return {
    items,
    prescriptionId,
    warnings,
    aliasLogs,
    replaceTargetId,
    addModalVisible,
    hasLowConfidenceItems,
    openReplace,
    closeReplace,
    confirmReplace,
    handleRemove,
    openAdd,
    closeAdd,
    confirmAdd,
  };
}
