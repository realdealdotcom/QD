/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.RangeFilter;
import com.devexperts.qd.kit.RangeUtil;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class BenchmarkRangeFilterTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    // Self-test to test alternative implementations for correctness.
    // This test is ignored by default, because it requires to have "securities.ipf.zip"
    @Ignore
    @Test
    public void testUniverse() throws Exception {
        List<InstrumentProfile> profiles = new InstrumentProfileReader().readFromFile("securities.ipf.zip");

        //String spec = "range_EME220715_EME22071Z_";
        String spec = "range_MSFT_NBSE_";
        QDFilter f1 = RangeFilter.valueOf(SCHEME, spec);
        QDFilter f2 = LongCodePrefixRangeFilter.valueOf(SCHEME, spec);

        SymbolCodec codec = SCHEME.getCodec();
        for (InstrumentProfile profile : profiles) {
            String symbol = profile.getSymbol();
            int cipher = codec.encode(symbol);

            boolean r1 = f1.accept(null, null, cipher, cipher != 0 ? null : symbol);
            boolean r2 = f1.accept(null, null, 0, symbol);

            boolean r3 = f2.accept(null, null, cipher, cipher != 0 ? null : symbol);
            boolean r4 = f2.accept(null, null, 0, symbol);

            assertEquals(symbol, r1, r2);
            assertEquals(symbol, r3, r4);
            assertEquals(symbol, r1, r3);
        }
    }

    @Test
    public void testInvalidRangeFilter() {
        assertInvalidFilter("range");
        assertInvalidFilter("range_");
        assertInvalidFilter("rangeABC");
        assertInvalidFilter("rangexAxB");
        assertInvalidFilter("rangexAxBxx");
        assertInvalidFilter("rangexAxAx");
        assertInvalidFilter("rangexBxAx");
    }


    @Test
    public void testSimpleRangeFilter() {
        QDFilter f = filter("range_A_B_");
        assertFilter(f, "0", false);

        assertFilter(f, "A", true);
        assertFilter(f, "ABCD", true);
        assertFilter(f, ".A", true);
        assertFilter(f, "./A", true);

        assertFilter(f, "B", false);
        assertFilter(f, "C", false);
    }

    @Test
    public void testSpreadSymbol() {
        QDFilter f = filter("range_A_B_");
        assertFilter(f, "=A", true);
        assertFilter(f, "=-A", true);
        assertFilter(f, "=-2*A", true);
        assertFilter(f, "=-2.2*A", true);
        assertFilter(f, "=-2.2*.A", true);
        assertFilter(f, "=-2.2*./A", true);
        assertFilter(f, "=-2.2*.AC-100.0", true);
        assertFilter(f, "=-2.2*.AP-100.0", true);
    }

    @Test
    public void testLongSymbol() {
        QDFilter f = filter("range_12345678A_12345678B_");
        assertFilter(f, "1234567", false);
        assertFilter(f, "12345678", false);

        assertFilter(f, "12345678A", true);
        assertFilter(f, "12345678AA", true);
        assertFilter(f, ".12345678A", true);
        assertFilter(f, "/12345678A", true);
        assertFilter(f, "=-1.23*12345678A", true);
        assertFilter(f, "12345678AB", true);
        assertFilter(f, "12345678AAA", true);
        assertFilter(f, "12345678AAB", true);

        assertFilter(f, "12345678B", false);
        assertFilter(f, "12345678BA", false);
    }

    @Test
    public void testLeftRangeSymbol() {
        QDFilter f = filter("range__AAA_");
        assertFilter(f, "", true);
        assertFilter(f, "0", true);
        assertFilter(f, "000000000000", true);
        assertFilter(f, "A", true);
        assertFilter(f, "AA", true);
        assertFilter(f, "AA0", true);
        assertFilter(f, "AA0000000000", true);
        assertFilter(f, "AA1", true);
    }

    @Test
    public void testRightRangeSymbol() {
        QDFilter f = filter("range_AAA__");
        assertFilter(f, "AAA", true);
        assertFilter(f, ".AAA", true);
        assertFilter(f, "/AAA", true);
        assertFilter(f, "=AAA", true);
        assertFilter(f, "AAAA", true);
        assertFilter(f, "AAA000000000", true);
        assertFilter(f, ".AAA00000000", true);
        assertFilter(f, "/AAA00000000", true);
        assertFilter(f, "=-2.*AAA00000000", true);
        assertFilter(f, "ZZZZZZZZZZZZ", true);
        assertFilter(f, "____________", true);
        assertFilter(f, "aaa", true);
        assertFilter(f, "zzz", true);
    }

    @Test
    public void testAllRangeSymbol() {
        QDFilter f = filter("range___");
        assertFilter(f, "AAA", true);
        assertFilter(f, "@", true);
        assertFilter(f, "{}", true);
        assertFilter(f, "\uFFFF", true);
    }

    @Test
    public void testEncodingSymbol() {
        // Symbols with code larger than 255 should not be "truncated"
        String pseudoAA = "A" + (char) ('A' + 256);

        assertNotEquals(RangeUtil.encodeSymbol("AA"), RangeUtil.encodeSymbol(pseudoAA));

        QDFilter f = filter("range_AA_AB_");
        assertFilter(f, "AA", true);
        assertFilter(f, pseudoAA, false);
    }

    @Test
    public void testOutOfRangeSymbol() {
        QDFilter f = filter("range__A_");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "\u007F", true);
        assertFilter(f, "\u00FF", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeCipher() {
        QDFilter f = filter("range__A_");
        assertFilter(f, "\u007E", true);
        assertFilter(f, "{|}", true);
    }

    @Test
    public void testOutOfRangeInvalidSymbol() {
        QDFilter f = filter("range_A_a_");
        // Use ASCII-code between A and a
        assertFilter(f, "[^]", false);
    }

    @Test
    public void testSimpleCode() {
        // Symbols with code larger than 255 should not be "truncated"
        QDFilter f = filter("range_A_B_");
        assertFilter(f, "0", false);

        assertFilter(f, "A", true);
        assertFilter(f, "ABCD", true);
        assertFilter(f, ".A", true);
        assertFilter(f, "./A", true);
        assertFilter(f, "=A-B", true);

        assertFilter(f, "B", false);
        assertFilter(f, "C", false);
    }

    @Test
    public void testLongSymbolCode() {
        QDFilter f = filter("range_AAAA00000ZZZ_AABB0000ZZZ_");
        assertFilter(f, "AAAA", false);

        assertFilter(f, "AABB", true);

        assertFilter(f, "AABBB", false);
    }

    // Utility methods

    private static QDFilter filter(String spec) {
        return LongCodePrefixRangeFilter.valueOf(SCHEME, spec);
    }

    private static void assertFilter(QDFilter filter, String s, boolean condition) {
        int cipher = SCHEME.getCodec().encode(s);
        if (cipher != 0) {
            assertEquals("Symbol " + s + " with filter " + filter, condition, filter.accept(null, null, cipher, null));
        }
        assertEquals("Symbol " + s + " with filter " + filter, condition, filter.accept(null, null, 0, s));
    }

    private static void assertInvalidFilter(String spec) {
        assertThrows(IllegalArgumentException.class, () -> filter(spec));
    }
}
