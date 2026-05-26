package com.pillmate.drug.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.RegisterUserAliasCommand;
import com.pillmate.drug.application.dto.RegisterUserAliasResponse;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import com.pillmate.drug.domain.repository.DrugMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserAliasService implements RegisterUserAliasUseCase {

    private static final int USER_ALIAS_CONFIDENCE = 70;

    private static final char[] CHO = {
        'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    };
    private static final char[] JUNG = {
        'ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ','ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ'
    };
    private static final char[] JONG = {
        0,'ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    };

    private final DrugMasterRepository drugMasterRepository;
    private final DrugAliasRepository drugAliasRepository;

    @Override
    @Transactional
    public RegisterUserAliasResponse register(RegisterUserAliasCommand command) {
        requireItemSeqExists(command.itemSeq());

        String alias = normalizeAlias(command.nameRaw());
        String aliasJamo = splitToJamo(alias);

        return drugAliasRepository.findByAliasAndItemSeq(alias, command.itemSeq())
                .map(this::toResponse)
                .orElseGet(() -> saveNewAlias(alias, aliasJamo, command.itemSeq()));
    }

    private void requireItemSeqExists(String itemSeq) {
        drugMasterRepository.findByItemSeq(itemSeq)
                .orElseThrow(() -> new PillmateException(ErrorCode.ITEM_SEQ_NOT_FOUND));
    }

    private RegisterUserAliasResponse saveNewAlias(String alias, String aliasJamo, String itemSeq) {
        DrugAlias saved = drugAliasRepository.save(
                DrugAlias.create(alias, aliasJamo, itemSeq, AliasSource.USER, USER_ALIAS_CONFIDENCE));
        return toResponse(saved);
    }

    private RegisterUserAliasResponse toResponse(DrugAlias a) {
        return new RegisterUserAliasResponse(
                a.getId(),
                a.getAlias(),
                a.getItemSeq(),
                a.getSource().name().toLowerCase(),
                a.getConfidence(),
                a.isVerified());
    }

    private String normalizeAlias(String raw) {
        return raw.replaceAll("\\([^)]*\\)", "")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    private String splitToJamo(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int offset = c - 0xAC00;
                int choIdx = offset / (21 * 28);
                int jungIdx = (offset % (21 * 28)) / 28;
                int jongIdx = offset % 28;
                sb.append(CHO[choIdx]);
                sb.append(JUNG[jungIdx]);
                if (jongIdx != 0) sb.append(JONG[jongIdx]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
