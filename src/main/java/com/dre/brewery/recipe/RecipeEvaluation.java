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
import com.dre.brewery.utility.BUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Helper class that keeps track of {@link BrewDefect BrewDefects} and their quality deductions.
 * Quality starts at 10, and is reduced by each defect.
 */
public final class RecipeEvaluation {

    private final List<QualityDeduction> deductions = new ArrayList<>();

    /**
     * Combines multiple RecipeEvaluations into one.
     * If there are {@code n} evaluations, each evaluation contributes {@code 1/n} of the quality.
     *
     * @param evals the evaluations
     * @return the combined evaluation
     */
    public static RecipeEvaluation combine(final RecipeEvaluation... evals) {
        final var combined = new RecipeEvaluation();
        for (final var eval : evals) {
            final var scaledDown = eval.deductions.stream()
                    .map(d -> d.scale(1.0f / evals.length))
                    .toList();
            combined.deductions.addAll(scaledDown);
        }
        return combined;
    }

    /**
     * Deducts quality by the specified amount.
     *
     * @param defect           the defect that caused the quality deduction
     * @param qualityDeduction the amount to deduct by
     * @throws IllegalArgumentException if qualityDeduction is negative
     */
    public final void deduct(final BrewDefect defect, final float qualityDeduction) {
        if (qualityDeduction < 0) {
            throw new IllegalArgumentException("qualityDeduction cannot be negative");
        }
        this.deductions.add(QualityDeduction.deduction(defect, qualityDeduction));
    }

    /**
     * Adds a fatal defect that prevents the recipe from being used.
     *
     * @param defect the defect
     */
    public final void fatal(final BrewDefect defect) {
        this.deductions.add(QualityDeduction.fatal(defect));
    }

    /**
     * @return whether {@link #getQuality()} is -1
     */
    public final boolean isFatal() {
        return this.getTrueQuality() < 0;
    }

    /**
     * @return whether there are any fatal defects
     */
    private boolean hasFatalDefect() {
        return this.deductions.stream().anyMatch(QualityDeduction::isFatal);
    }

    /**
     * Gets the quality of the recipe. Will be between 0 and 10 inclusive and rounded.
     * If there are fatal defects, or if the quality is deducted to less than 0,the quality will be -1.
     *
     * @return the quality
     */
    public final float getQuality() {
        final var quality = this.getTrueQuality();
        if (quality < 0) {
            return -1;
        }
        return quality;
    }

    /**
     * Gets the true quality of the recipe, without rounding or bounds.
     * Can be any number 10 or below. Will be negative infinity if there are fatal defects.
     *
     * @return the true quality
     */
    public final float getTrueQuality() {
        if (this.hasFatalDefect()) {
            return Float.NEGATIVE_INFINITY;
        }
        return this.deductions.stream()
                .map(QualityDeduction::getQualityDeduction)
                .reduce(10f, (q1, q2) -> q1 - q2);
    }

    /**
     * @return all quality deductions, in arbitrary order
     */
    public List<QualityDeduction> getDeductions() {
        return Collections.unmodifiableList(this.deductions);
    }

    /**
     * Gets the defect that deducts the most quality from the recipe.
     * If there is a tie, multiple defects are returned.
     *
     * @return list of defects, possibly empty
     */
    public final List<BrewDefect> getWorstDefects() {
        if (this.hasFatalDefect()) {
            return this.deductions.stream()
                    .filter(QualityDeduction::isFatal)
                    .map(QualityDeduction::getDefect)
                    .toList();
        } else {
            return BUtil.multiMin(this.deductions).stream()
                    .map(QualityDeduction::getDefect)
                    .toList();
        }
    }

    /**
     * Compares two RecipeEvaluations, in order of most complexity to least complexity.
     * Recipe evaluations are sorted by, in order:
     * <ul>
     *     <li>Number of total defects, most to fewest</li>
     *     <li>Number of fatal defects, most to fewest</li>
     *     <li>{@link #getTrueQuality()}, lowest to highest</li>
     * </ul>
     *
     * @param other the other evaluation
     * @return -1, 0, or 1 if <, =, or >
     * @throws NullPointerException if other is null
     */
    public final int compareMostToLeastComplexity(final RecipeEvaluation other) {
        if (other == null) {
            throw new NullPointerException("other cannot be null");
        }
        final var numDefectsCompare = -Integer.compare(this.deductions.size(), other.deductions.size());
        if (numDefectsCompare != 0) {
            return numDefectsCompare;
        }
        final var thisFatalCount = this.fatalCount();
        final var thisFatal = thisFatalCount > 0;
        final var otherFatalCount = other.fatalCount();
        final var otherFatal = otherFatalCount > 0;
        if (!thisFatal && !otherFatal) {
            return Float.compare(this.getTrueQuality(), other.getTrueQuality());
        }
        if (thisFatal && otherFatal) {
            return -Integer.compare(thisFatalCount, otherFatalCount);
        }
        return -Boolean.compare(thisFatal, otherFatal);
    }

    private int fatalCount() {
        return (int) this.deductions.stream()
                .filter(QualityDeduction::isFatal)
                .count();
    }

    @Override
    public final String toString() {
        final var quality = this.getTrueQuality();
        final var qualityStr = quality == Float.NEGATIVE_INFINITY ? "fatal" : String.format("%.3f", quality);

        final var deductionsStr = this.deductions.stream()
                .map(QualityDeduction::toString)
                .collect(Collectors.joining(", ", "[", "]"));

        return new StringJoiner(", ", "{", "}")
                .add("quality=" + qualityStr)
                .add("deductions=" + deductionsStr)
                .toString();
    }

}
