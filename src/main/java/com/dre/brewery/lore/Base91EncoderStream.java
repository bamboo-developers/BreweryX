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

import java.io.ByteArrayInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class Base91EncoderStream extends FilterOutputStream {

    private final basE91 encoder = new basE91();
    private byte[] buf = new byte[32];
    private byte[] encBuf = new byte[48];
    private int writer = 0;
    private int encoded = 0;

    public Base91EncoderStream(final OutputStream out) {
        super(out);
    }

    private void encFlush() throws IOException {
        this.encoded = this.encoder.encode(this.buf, this.writer, this.encBuf);
        this.out.write(this.encBuf, 0, this.encoded);
        this.writer = 0;
    }

    @Override
    public final void write(final int b) throws IOException {
        this.buf[this.writer++] = (byte) b;
        if (this.writer >= this.buf.length) {
            this.encFlush();
        }
    }

    @Override
    public final void write(final byte[] b, final int off, final int len) throws IOException {
        if (len == 0) return;
        if (b == null) throw new NullPointerException();
        if (len < 0 || off < 0 || (off + len) > b.length || off > b.length || (off + len) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (this.buf.length - this.writer >= len) {
            // Enough space in the buffer, copy it in
            System.arraycopy(b, off, this.buf, this.writer, len);
            this.writer += len;
            if (this.writer >= this.buf.length) {
                this.encFlush();
            }
            return;
        }

        if (off == 0 && this.buf.length >= len) {
            // Buffer is too full but it would fit, so flush and encode data directly
            this.encFlush();
            this.encoded = this.encoder.encode(b, len, this.encBuf);
            this.out.write(this.encBuf, 0, this.encoded);
            return;
        }

        // More data than space in the Buffer
        final var in = new ByteArrayInputStream(b, off, len);
        while (true) {
            this.writer += in.read(this.buf, this.writer, this.buf.length - this.writer);
            if (this.writer >= this.buf.length) {
                this.encFlush();
            } else {
                break;
            }
        }
    }

    @Override
    public final void flush() throws IOException {
        if (this.writer > 0) {
            this.encFlush();
        }

        this.encoded = this.encoder.encEnd(this.encBuf);
        if (this.encoded > 0) {
            this.out.write(this.encBuf, 0, this.encoded);
        }
        super.flush();
    }

    @Override
    public final void close() throws IOException {
        super.close();
        this.encoder.encReset();
        this.buf = null;
        this.encBuf = null;
    }
}
