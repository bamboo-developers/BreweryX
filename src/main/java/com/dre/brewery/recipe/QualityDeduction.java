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

package com.dre.brewery.recipe;

import com.dre.brewery.BrewDefect;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents a deduction of quality caused by a {@link BrewDefect}.
 * Sort order is <strong>only</strong> determined by quality. Two QualityDeductions with the same quality but
 * different defects will be considered the same for sorting.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class QualityDeduction implements Comparable<QualityDeduction> {

    /**
     * The reason for this deduction
     */
    private final BrewDefect defect;
    /**
     * The amount to deduct by, will be negative infinity if fatal
     */
    private final float qualityDeduction;

    /**
     * Creates a quality deduction with the given amount.
     *
     * @param defect           the defect that is the reason for this deduction
     * @param qualityDeduction the amount to deduct
     * @return the quality deduction
     * @throws IllegalArgumentException if qualityDeduction is not finite
     */
    public static QualityDeduction deduction(final BrewDefect defect, final float qualityDeduction) {
        if (!Float.isFinite(qualityDeduction)) {
            throw new IllegalArgumentException("qualityDeduction must be finite");
        }
        return new QualityDeduction(defect, qualityDeduction);
    }

    /**
     * Creates a fatal quality deduction, which prevents the brew from being used.
     *
     * @param defect the defect that is the reason for this deduction
     * @return the quality deduction
     */
    public static QualityDeduction fatal(final BrewDefect defect) {
        return new QualityDeduction(defect, Float.NEGATIVE_INFINITY);
    }

    /**
     * @return whether this defect is fatal, and should prevent the brew from being used
     */
    public final boolean isFatal() {
        return this.qualityDeduction == Float.NEGATIVE_INFINITY;
    }

    /**
     * Scales the deduction amount by the given factor.
     *
     * @param f the factor to scale by
     * @return the scaled quality deduction
     * @throws IllegalArgumentException if f is negative
     */
    public final QualityDeduction scale(final float f) {
        if (f < 0) {
            throw new IllegalArgumentException("f must be positive");
        }
        // ok since -inf * anything non-negative = -inf
        return new QualityDeduction(this.defect, this.qualityDeduction * f);
    }

    @Override
    public final int compareTo(final QualityDeduction other) {
        return Float.compare(this.qualityDeduction, other.qualityDeduction);
    }

    @Override
    public final String toString() {
        if (this.isFatal()) {
            return "FATAL " + this.defect;
        }
        return String.format("-%.3f %s", this.qualityDeduction, this.defect);
    }

}
