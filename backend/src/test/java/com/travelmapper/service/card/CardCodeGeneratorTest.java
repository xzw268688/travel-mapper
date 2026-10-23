package com.travelmapper.service.card;

import com.travelmapper.common.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardCodeGeneratorTest {

    private final CardCodeGenerator generator = new CardCodeGenerator();

    @Test
    void generatesGroupedCodeWithSafeAlphabet() {
        String code = generator.generate("TM");
        assertThat(code).matches("TM-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(code).doesNotContain("O", "I", "L", "0", "1");
        assertThat(code).hasSize(22);
    }

    @Test
    void defaultPrefixAndCustomPrefix() {
        assertThat(generator.generate(null)).startsWith("TM-");
        assertThat(generator.generate("ABC")).startsWith("ABC-");
        assertThat(generator.normalizePrefix("abc")).isEqualTo("ABC");
    }

    @Test
    void rejectsInvalidPrefix() {
        assertThatThrownBy(() -> generator.normalizePrefix("A"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> generator.normalizePrefix("ABCDEFG"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> generator.normalizePrefix("A1"))
                .isInstanceOf(BusinessException.class);
    }
}
