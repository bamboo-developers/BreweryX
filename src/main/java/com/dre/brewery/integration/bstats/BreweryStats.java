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

package com.dre.brewery.integration.bstats;

import com.dre.brewery.*;
import com.dre.brewery.configuration.ConfigManager;
import com.dre.brewery.configuration.files.Config;
import com.dre.brewery.integration.bstats.Metrics.AdvancedPie;
import com.dre.brewery.integration.bstats.Metrics.DrilldownPie;
import com.dre.brewery.integration.bstats.Metrics.SimplePie;
import com.dre.brewery.integration.bstats.Metrics.SingleLineChart;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.utility.Logging;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

/**
 * General stats written by the original author of Brewery.
 */
public final class BreweryStats {

    private static final int BSTATS_ID = 3494;

    private final Config config = ConfigManager.getConfig(Config.class);
    public int brewsCreated;
    public int brewsCreatedCmd; // Created by command
    public int exc;
    public int good;
    public int norm;
    public int bad;
    public int terr; // Brews drunken with quality

    public final void metricsForCreate(final boolean byCmd) {
        if (this.brewsCreated == Integer.MAX_VALUE) return;
        this.brewsCreated++;
        if (byCmd) {
            if (this.brewsCreatedCmd == Integer.MAX_VALUE) return;
            this.brewsCreatedCmd++;
        }
    }

    public final void forDrink(final Brew brew) {
        if (brew.getQuality() >= 9) {
            this.exc++;
        } else if (brew.getQuality() >= 7) {
            this.good++;
        } else if (brew.getQuality() >= 5) {
            this.norm++;
        } else if (brew.getQuality() >= 3) {
            this.bad++;
        } else {
            this.terr++;
        }
    }

    public final void setupBStats() {
        try {
            final var metrics = new Metrics(BreweryPlugin.getInstance(), BSTATS_ID);
            metrics.addCustomChart(new SingleLineChart("drunk_players", BPlayer::numDrunkPlayers));
            metrics.addCustomChart(new SingleLineChart("brews_in_existence", () -> this.brewsCreated));
            metrics.addCustomChart(new SingleLineChart("barrels_built", Barrel.getAllBarrels()::size));
            metrics.addCustomChart(new SingleLineChart("cauldrons_boiling", BCauldron.bcauldrons::size));
            metrics.addCustomChart(new AdvancedPie("brew_quality", () -> {
                final Map<String, Integer> map = new HashMap<>(8);
                map.put("excellent", this.exc);
                map.put("good", this.good);
                map.put("normal", this.norm);
                map.put("bad", this.bad);
                map.put("terrible", this.terr);
                return map;
            }));
            metrics.addCustomChart(new AdvancedPie("brews_created", () -> {
                final Map<String, Integer> map = new HashMap<>(4);
                map.put("by command", this.brewsCreatedCmd);
                map.put("brewing", this.brewsCreated - this.brewsCreatedCmd);
                return map;
            }));

            metrics.addCustomChart(new SimplePie("number_of_recipes", () -> {
                final var recipes = BRecipe.getAllRecipes().size();
                if (recipes < 7) {
                    return "Less than 7";
                } else if (recipes < 11) {
                    return "7-10";
                } else if (recipes == 11) {
                    // There were 11 default recipes, so show this as its own slice
                    return "11";
                } else if (recipes == 20) {
                    // There are 20 default recipes, so show this as its own slice
                    return "20";
                } else if (recipes <= 29) {
                    if (recipes % 2 == 0) {
                        return recipes + "-" + (recipes + 1);
                    } else {
                        return (recipes - 1) + "-" + recipes;
                    }
                } else if (recipes < 35) {
                    return "30-34";
                } else if (recipes < 40) {
                    return "35-39";
                } else if (recipes < 45) {
                    return "40-44";
                } else if (recipes <= 50) {
                    return "45-50";
                } else {
                    return "More than 50";
                }

            }));
            metrics.addCustomChart(new SimplePie("cauldron_particles", () -> {
                if (!this.config.isEnableCauldronParticles()) {
                    return "disabled";
                }
                if (this.config.isMinimalParticles()) {
                    return "minimal";
                }
                return "enabled";
            }));
            metrics.addCustomChart(new SimplePie("wakeups", () -> {
                if (!this.config.isEnableWake()) {
                    return "disabled";
                }
                final var wakeups = Wakeup.wakeups.size();
                if (wakeups == 0) {
                    return "0";
                } else if (wakeups <= 5) {
                    return "1-5";
                } else if (wakeups <= 10) {
                    return "6-10";
                } else if (wakeups <= 20) {
                    return "11-20";
                } else {
                    return "More than 20";
                }
            }));
            metrics.addCustomChart(new SimplePie("v2_mc_version", () -> {
                var mcv = Bukkit.getBukkitVersion();
                mcv = mcv.substring(0, mcv.indexOf('.', 2));
                final var index = mcv.indexOf('-');
                if (index > -1) {
                    mcv = mcv.substring(0, index);
                }
                if (mcv.matches("^\\d\\.\\d{1,2}$")) {
                    // Start, digit, dot, 1-2 digits, end
                    return mcv;
                } else {
                    return "undef";
                }
            }));
            metrics.addCustomChart(new DrilldownPie("plugin_mc_version", () -> {
                final Map<String, Map<String, Integer>> map = new HashMap<>(3);
                var mcv = Bukkit.getBukkitVersion();
                mcv = mcv.substring(0, mcv.indexOf('.', 2));
                final var index = mcv.indexOf('-');
                if (index > -1) {
                    mcv = mcv.substring(0, index);
                }
                if (mcv.matches("^\\d\\.\\d{1,2}$")) {
                    // Start, digit, dot, 1-2 digits, end
                    mcv = "MC " + mcv;
                } else {
                    mcv = "undef";
                }
                final Map<String, Integer> innerMap = new HashMap<>(3);
                innerMap.put(mcv, 1);
                map.put(BreweryPlugin.getInstance().getDescription().getVersion(), innerMap);
                return map;
            }));
            metrics.addCustomChart(new SimplePie("language", this.config::getLanguage));
            metrics.addCustomChart(new SimplePie("config_scramble", () -> this.config.isEnableEncode() ? "enabled" : "disabled"));
            metrics.addCustomChart(new SimplePie("config_lore_color", () -> {
                if (this.config.isColorInBarrels()) {
                    if (this.config.isColorInBrewer()) {
                        return "both";
                    } else {
                        return "in barrels";
                    }
                } else {
                    if (this.config.isColorInBrewer()) {
                        return "in distiller";
                    } else {
                        return "none";
                    }
                }
            }));
            metrics.addCustomChart(new SimplePie("config_always_show", () -> {
                if (this.config.isAlwaysShowQuality()) {
                    if (this.config.isAlwaysShowAlc()) {
                        return "both";
                    } else {
                        return "quality stars";
                    }
                } else {
                    if (this.config.isAlwaysShowAlc()) {
                        return "alc content";
                    } else {
                        return "none";
                    }
                }
            }));
        } catch (final Exception | LinkageError e) {
            Logging.errorLog("Failed to submit stats data to bStats.org", e);
        }
    }

}
