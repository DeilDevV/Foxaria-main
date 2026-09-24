package com.foxaria.hubguard;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Текст префикса из YAML иногда оказывается в виде «РђРґРјРёРЅ» вместо «Админ»:
 * UTF-8 байты были интерпретированы как Windows-1251. Обратное перекодирование восстанавливает строку.
 */
public final class HubEncodingUtil {

    private static final Charset CP1251 = Charset.forName("Windows-1251");

    private HubEncodingUtil() {
    }

    /**
     * Если строка — результат UTF-8, ошибочно прочитанного как CP-1251, возвращает исправленный текст.
     * Уже корректный UTF-8 обычно даёт невалидную UTF-8 последовательность при обратном ходе — тогда возвращаем исходное.
     */
    public static String fixUtf8MisreadAsCp1251(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        ByteBuffer encoded = CP1251.encode(input);
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return input;
        }
    }
}
