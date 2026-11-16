package com.shoppulse.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    @DisplayName("email columns are masked, other columns are untouched")
    void masksEmailColumnsOnly() {
        List<String> columns = List.of("display_name", "email", "lifetime_revenue");
        List<List<Object>> rows = List.of(
                Arrays.asList("Jane Doe", "jane.doe@example.com", 1250.00),
                Arrays.asList("Sam Lee", "sam@shop.test", 310.50));

        List<List<Object>> masked = PiiMasker.maskRows(columns, rows);

        assertThat(masked.get(0)).containsExactly("Jane Doe", "j***@example.com", 1250.00);
        assertThat(masked.get(1)).containsExactly("Sam Lee", "s***@shop.test", 310.50);
    }

    @Test
    @DisplayName("rows without a sensitive column are returned unchanged")
    void leavesNonSensitiveRowsAlone() {
        List<String> columns = List.of("category_name", "revenue");
        List<List<Object>> rows = List.of(Arrays.asList("Audio", 900.00));

        assertThat(PiiMasker.maskRows(columns, rows)).isSameAs(rows);
    }

    @Test
    @DisplayName("nulls and malformed addresses do not leak and do not throw")
    void handlesNullsAndJunk() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("not-an-email")).isEqualTo("***");
        assertThat(PiiMasker.mask("@leading")).isEqualTo("***");
        assertThat(PiiMasker.maskRows(null, null)).isNull();
    }

    @Test
    @DisplayName("password-like columns are treated as sensitive too")
    void flagsPasswordColumns() {
        assertThat(PiiMasker.isSensitive("password_hash")).isTrue();
        assertThat(PiiMasker.isSensitive("EMAIL")).isTrue();
        assertThat(PiiMasker.isSensitive("revenue")).isFalse();
        assertThat(PiiMasker.isSensitive(null)).isFalse();
    }
}
