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
package com.devexperts.qd.kit;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.SymbolStriper;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy that splits symbol universe into stripes by hash groups.
 * @see HashFilter
 */
public class HashStriper implements SymbolStriper {
    
    public static final String HASH_STRIPER_PREFIX = "byhash";

    private static final Pattern STRIPER_PATTERN = Pattern.compile(HASH_STRIPER_PREFIX + "([0-9]+)");

    private static final int MAGIC = 0xB46394CD;

    private final String name;
    private final DataScheme scheme;
    private final SymbolCodec codec;
    private final int stripeCount;
    private final int shift;

    // Cached filters
    private final HashFilter[] filters;

    public static HashStriper valueOf(DataScheme scheme, String spec) {
        Matcher m = STRIPER_PATTERN.matcher(Objects.requireNonNull(spec, "spec"));
        if (!m.matches())
            throw new IllegalArgumentException("Invalid hash striper definition: " + spec);
        try {
            return new HashStriper(scheme, Integer.parseInt(m.group(1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in hash striper definition: " + spec);
        }
    }

    public static SymbolStriper valueOf(DataScheme scheme, int stripeCount) {
        if (stripeCount < 1)
            throw new IllegalArgumentException("Invalid stripe count: " + stripeCount);
        if (stripeCount == 1)
            return MonoStriper.INSTANCE;
        return new HashStriper(scheme, stripeCount);
    }

    protected HashStriper(DataScheme scheme, int stripeCount) {
        Objects.requireNonNull(scheme, "scheme");
        if ((stripeCount < 2) || ((stripeCount & (stripeCount - 1)) != 0))
            throw new IllegalArgumentException("Stripe count should a power of 2 and at least 2");

        this.name = HASH_STRIPER_PREFIX + stripeCount;
        this.scheme = scheme;
        this.codec = scheme.getCodec();
        this.stripeCount = stripeCount;
        this.shift = 32 - Integer.numberOfTrailingZeros(stripeCount);
        this.filters = new HashFilter[stripeCount];
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public int getStripeCount() {
        return stripeCount;
    }

    @Override
    public int getStripeIndex(int cipher, String symbol) {
        return index(cipher != 0 ? codec.hashCode(cipher) : symbol.hashCode());
    }

    @Override
    public int getStripeIndex(String symbol) {
        return index(symbol.hashCode());
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (filters[stripeIndex] == null) {
            filters[stripeIndex] = new HashFilter(this, stripeIndex);
        }
        return filters[stripeIndex];
    }

    @Override
    public String toString() {
        return getName();
    }

    private int index(int hash) {
        return hash * MAGIC >>> shift;
    }
}
