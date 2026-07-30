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

public final class ClassUtil {

    private ClassUtil() {
        throw new UnsupportedOperationException("Utility class");
    }


    public static boolean methodExists(final String className, final String methodName, final Class<?>... methodParams) {
        try {
            final var clazz = Class.forName(className);
            clazz.getMethod(methodName, methodParams);
            return true;
        } catch (final ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }

    public static boolean fieldExists(final String className, final String fieldName) {
        try {
            final var clazz = Class.forName(className);
            clazz.getField(fieldName);
            return true;
        } catch (final ClassNotFoundException | NoSuchFieldException e) {
            return false;
        }
    }

    public static boolean exists(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }
}
