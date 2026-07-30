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
package com.dre.brewery.utility;

import org.jetbrains.annotations.Contract;

/**
 * @param a The first value in the tuple
 * @param b The second value in the tuple
 */
public record Tuple<A, B>(A a, B b) {

    /**
     * Gets the first value in the tuple
     */
    @Contract(pure = true)
    public A first() {
        return this.a;
    }

    /**
     * Gets the second value in the tuple
     */
    @Contract(pure = true)
    public B second() {
        return this.b;
    }

    /**
     * Gets the first value in the tuple, Synonym for first()
     */
    @Override
    @Contract(pure = true)
    public A a() {
        return this.a;
    }

    /**
     * Gets the second value in the tuple, Synonym for second()
     */
    @Override
    @Contract(pure = true)
    public B b() {
        return this.b;
    }

    @Override
    public boolean equals(final Object object) {
        if (!(object instanceof Tuple<?, ?>(Object a1, Object b1))) {
            return false;
        }

        return a1.equals(this.a) && b1.equals(this.b);
    }

    @Override
    public int hashCode() {
        return this.a.hashCode() ^ this.b.hashCode();
    }

    @Override
    public String toString() {
        return "Tuple{" +
                '{' + this.a + '}' +
                '{' + this.b + "}}";
    }
}
