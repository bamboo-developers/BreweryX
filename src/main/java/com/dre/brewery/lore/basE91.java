/*
 * basE91 encoding/decoding routines
 *
 * Copyright (c) 2000-2006 Joachim Henke
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  - Neither the name of Joachim Henke nor the names of his contributors may
 *    be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.dre.brewery.lore;


public final class basE91 {
    public static final byte[] enctab;
    private static final byte[] dectab;

    static {
        int i;
        final var ts = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&()*+,-./:;<=>?@[]^_`{|}~\""; // Added '-' removed '#'

        enctab = ts.getBytes();
        dectab = new byte[256];
        for (i = 0; i < 256; ++i)
            dectab[i] = -1;
        for (i = 0; i < 91; ++i)
            dectab[enctab[i]] = (byte) i;
    }

    private int ebq;
    private int en;
    private int dbq;
    private int dn;
    private int dv;
    private int[] marker = null;

    public basE91() {
        this.encReset();
        this.decReset();
    }

    public final int encode(final byte[] ib, final int n, final byte[] ob) {
        int i;
        int c = 0;

        for (i = 0; i < n; ++i) {
            this.ebq |= (ib[i] & 255) << this.en;
            this.en += 8;
            if (this.en > 13) {
                var ev = this.ebq & 8191;

                if (ev > 88) {
                    this.ebq >>= 13;
                    this.en -= 13;
                } else {
                    ev = this.ebq & 16383;
                    this.ebq >>= 14;
                    this.en -= 14;
                }
                ob[c++] = enctab[ev % 91];
                ob[c++] = enctab[ev / 91];
            }
        }
        return c;
    }

    public final int encEnd(final byte[] ob) {
        var c = 0;

        if (this.en > 0) {
            ob[c++] = enctab[this.ebq % 91];
            if (this.en > 7 || this.ebq > 90)
                ob[c++] = enctab[this.ebq / 91];
        }
        this.encReset();
        return c;
    }

    public final void encReset() {
        this.ebq = 0;
        this.en = 0;
    }

    public final int decode(final byte[] ib, final int n, final byte[] ob) {
        int i;
        int c = 0;

        for (i = 0; i < n; ++i) {
            if (dectab[ib[i]] == -1)
                continue;
            if (this.dv == -1)
                this.dv = dectab[ib[i]];
            else {
                this.dv += dectab[ib[i]] * 91;
                this.dbq |= this.dv << this.dn;
                this.dn += (this.dv & 8191) > 88 ? 13 : 14;
                do {
                    ob[c++] = (byte) this.dbq;
                    this.dbq >>= 8;
                    this.dn -= 8;
                } while (this.dn > 7);
                this.dv = -1;
            }
        }
        return c;
    }

    public final int decEnd(final byte[] ob) {
        var c = 0;

        if (this.dv != -1)
            ob[c++] = (byte) (this.dbq | this.dv << this.dn);
        this.decReset();
        return c;
    }

    public final void decReset() {
        this.dbq = 0;
        this.dn = 0;
        this.dv = -1;
    }

    public final void decMark() {
        this.marker = new int[]{this.dbq, this.dn, this.dv};
    }

    public final void decUnmark() {
        if (this.marker == null) return;
        this.dbq = this.marker[0];
        this.dn = this.marker[1];
        this.dv = this.marker[2];
    }
}
