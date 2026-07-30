/*
 * BreweryX Bukkit-Plugin for an alternate brewing process
 * Copyright (C) 2024-2025 The Brewery Team
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

package com.dre.brewery.utility;

public final class OptionalFloat {
    private static final OptionalFloat EMPTY = new OptionalFloat();
    private final float value;
    private final boolean isPresent;

    private OptionalFloat(final float value) {
        this.value = value;
        this.isPresent = true;
    }

    private OptionalFloat() {
        this.value = 0.0f;
        this.isPresent = false;
    }

    public static OptionalFloat of(final float value) {
        return new OptionalFloat(value);
    }

    public static OptionalFloat empty() {
        return EMPTY;
    }

    public boolean isPresent() {
        return this.isPresent;
    }

    public boolean isEmpty() {
        return !this.isPresent;
    }

    public final float orElse(final float other) {
        return this.isPresent ? this.value : other;
    }

    public final String toString() {
        return this.isPresent ? "OptionalFloat[" + this.value + "]" : "OptionalFloat.empty";
    }

}
