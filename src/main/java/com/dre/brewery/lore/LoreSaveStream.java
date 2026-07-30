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

import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class LoreSaveStream extends ByteArrayOutputStream {

    public static final String IDENTIFIER = "§%";

    private ItemMeta meta;
    private int line;
    private boolean flushed = false;

    public LoreSaveStream(final ItemMeta meta) {
        this(meta, -1);
    }

    public LoreSaveStream(final ItemMeta meta, final int line) {
        super(128);
        this.meta = meta;
        this.line = line;
    }

    // Writes to the Lore
    // Without calling this, the ItemMeta remains unchanged
    @Override
    public final void flush() throws IOException {
        super.flush();
        if (this.size() <= 0) return;
        if (this.flushed || this.meta == null) {
            // Dont write twice
            return;
        }
        this.flushed = true;
        final var s = this.toString();

        final var loreLineBuilder = new StringBuilder((s.length() * 2) + 6);
        loreLineBuilder.append(IDENTIFIER);
        for (final var c : s.toCharArray()) {
            loreLineBuilder.append('§').append(c);
        }
        final List<String> lore;
        if (this.meta.hasLore()) {
            lore = this.meta.getLore();
        } else {
            lore = new ArrayList<>();
        }
        var prev = 0;
        for (final var iterator = lore.iterator(); iterator.hasNext(); ) {
            if (iterator.next().startsWith(IDENTIFIER)) {
                iterator.remove();
                break;
            }
            prev++;
        }
        if (this.line < 0) {
            if (prev >= 0) {
                this.line = prev;
            } else {
                this.line = lore.size();
            }
        }
        while (lore.size() < this.line) {
            lore.add("");
        }
        lore.add(this.line, loreLineBuilder.toString());
        this.meta.setLore(lore);
    }

    @Override
    public final void close() throws IOException {
        super.close();
        this.meta = null;
    }
}
