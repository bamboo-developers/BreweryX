/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024 The Brewery Team
 *
 * This file is part of BreweryX.
 *
 * BreweryX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BreweryX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BreweryX. If not, see <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.dre.brewery.lore;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.Arrays;

public final class SeedInputStream extends InputStream {
    // From java.util.Random
    private static final long multiplier = 0x5DEECE66DL;
    private static final long addend = 0xBL;
    private static final long mask = (1L << 48) - 1;

    private long seed;
    private byte[] buf = new byte[4];
    private byte reader = 4;
    private long markSeed;
    private byte[] markbuf;

    public SeedInputStream(final long seed) {
        this.seed = (seed ^ multiplier) & mask;
    }

    private void calcSeed() {
        this.seed = (this.seed * multiplier + addend) & mask;
    }

    private void genNext() {
        this.calcSeed();
        final var next = (int) (this.seed >>> 16);
        this.buf[0] = (byte) (next >> 24);
        this.buf[1] = (byte) (next >> 16);
        this.buf[2] = (byte) (next >> 8);
        this.buf[3] = (byte) next;
        this.reader = 0;
    }

    @Override
    public final int read(@NotNull final byte[] b, final int off, final int len) {
        for (var i = off; i < len; i++) {
            if (this.reader >= 4) {
                this.genNext();
            }
            b[i] = this.buf[this.reader++];
        }
        return len;
    }

    @Override
    public final int read() {
        if (this.reader == 4) {
            this.genNext();
        }
        return this.buf[this.reader++];
    }

    @Override
    public final long skip(final long toSkip) {
        var n = toSkip;
        while (n > 0) {
            if (this.reader < 4) {
                this.reader++;
                n--;
            } else if (n >= 4) {
                this.calcSeed();
                n -= 4;
            } else {
                this.genNext();
            }
        }
        return toSkip;
    }

    @Override
    public final void close() {
        this.buf = null;
    }

    @Override
    public final boolean markSupported() {
        return true;
    }

    @Override
    public final synchronized void mark(final int readlimit) {
        this.markbuf = new byte[]{this.buf[0], this.buf[1], this.buf[2], this.buf[3], this.reader};
        this.markSeed = this.seed;
    }

    @Override
    public final synchronized void reset() {
        this.seed = this.markSeed;
        this.buf = Arrays.copyOfRange(this.markbuf, 0, 4);
        this.reader = this.markbuf[4];
    }
}
