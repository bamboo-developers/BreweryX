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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Base91DecoderStream extends FilterInputStream {

    private final basE91 decoder = new basE91();
    private byte[] decbuf = new byte[32];
    private byte[] buf = new byte[32];
    private int reader = 0;
    private int count = 0;
    private byte[] markBuf = null;

    public Base91DecoderStream(final InputStream in) {
        super(in);
    }

    private void decode() throws IOException {
        this.reader = 0;
        this.count = this.in.read(this.decbuf);
        if (this.count < 1) {
            this.count = this.decoder.decEnd(this.buf);
            if (this.count < 1) {
                this.count = -1;
            }
            return;
        }
        this.count = this.decoder.decode(this.decbuf, this.count, this.buf);
    }

    @Override
    public final int read() throws IOException {
        if (this.count == -1) return -1;
        if (this.count == 0 || this.reader == this.count) {
            this.decode();
            return this.read();
        }
        return this.buf[this.reader++] & 0xFF;
    }

    @Override
    public final int read(final byte[] b, final int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;

        if (this.count == -1) return -1;
        if (this.count == 0 || this.reader == this.count) {
            this.decode();
            if (this.count == -1) return -1;
        }

        if (this.count > 0 && this.count - this.reader >= len) {
            // enough data in buffer, copy it out directly
            System.arraycopy(this.buf, this.reader, b, off, len);
            this.reader += len;
            return len;
        }

        var out = 0;
        int writeSize;
        while (this.count > 0) {
            // Not enough data in buffer, write all out, decode and repeat
            writeSize = Math.min(len, this.count - this.reader);
            System.arraycopy(this.buf, this.reader, b, off + out, writeSize);
            out += writeSize;
            len -= writeSize;
            if (len > 0) {
                this.decode();
            } else {
                this.reader += writeSize;
                break;
            }
        }
        return out;
    }

    @Override
    public final long skip(final long n) throws IOException {
        if (this.count == -1) return 0;
        if (this.count > 0 && this.count - this.reader >= n) {
            this.reader += n;
            return n;
        }
        long skipped = this.count - this.reader;
        this.decode();

        while (this.count > 0) {
            if (this.count > n - skipped) {
                this.reader = (int) (n - skipped);
                return n;
            }
            skipped += this.count;
            this.decode();
        }
        return skipped;
    }

    @Override
    public final int available() throws IOException {
        if (this.count == -1) return 0;
        return (int) (this.in.available() * 0.813F) + this.count - this.reader; // Ratio encoded to decoded with random data
    }

    @Override
    public final void close() throws IOException {
        this.in.close();
        this.count = -1;
        this.decoder.decReset();
        this.buf = null;
        this.decbuf = null;
    }

    @Override
    public final synchronized void mark(final int readlimit) {
        if (!this.markSupported()) return;
        if (this.count == -1) return;
        this.in.mark(readlimit);
        this.decoder.decMark();
        if (this.count > 0 && this.reader < this.count) {
            this.markBuf = new byte[this.count - this.reader];
            System.arraycopy(this.buf, this.reader, this.markBuf, 0, this.markBuf.length);
        } else {
            this.markBuf = null;
        }
    }

    @Override
    public final synchronized void reset() throws IOException {
        if (!this.markSupported()) throw new IOException("mark and reset not supported by underlying Stream");
        this.in.reset();
        this.decoder.decUnmark();
        this.reader = 0;
        this.count = 0;
        if (this.markBuf != null) {
            System.arraycopy(this.markBuf, 0, this.buf, 0, this.markBuf.length);
            this.count = this.markBuf.length;
        }
    }

    @Override
    public final boolean markSupported() {
        return this.in.markSupported();
    }
}
