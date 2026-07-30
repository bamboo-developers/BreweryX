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

package com.dre.brewery.recipe;

import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.BukkitConstants;
import com.dre.brewery.utility.Logging;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class BEffect implements Cloneable {

    private final PotionEffectType type;
    private short minlvl;
    private short maxlvl;
    private short minduration;
    private short maxduration;
    private boolean hidden = false;


    public BEffect(final PotionEffectType type, final short minlvl, final short maxlvl, final short minduration, final short maxduration, final boolean hidden) {
        this.type = type;
        this.minlvl = minlvl;
        this.maxlvl = maxlvl;
        this.minduration = minduration;
        this.maxduration = maxduration;
        this.hidden = hidden;
    }

    public BEffect(final String effectString) {
        final var effectSplit = effectString.split("/");
        var effect = effectSplit[0];
        if (effect.equalsIgnoreCase("WEAKNESS") ||
                effect.equalsIgnoreCase("INCREASE_DAMAGE") ||
                effect.equalsIgnoreCase("SLOW") ||
                effect.equalsIgnoreCase("SPEED") ||
                effect.equalsIgnoreCase("REGENERATION")) {
            // hide these effects as they put crap into lore
            // Dont write Regeneration into Lore, its already there storing data!
            this.hidden = true;
        } else if (effect.endsWith("X")) {
            this.hidden = true;
            effect = effect.substring(0, effect.length() - 1);
        }
        this.type = BukkitConstants.nullablePotionEffectType(effect.toLowerCase());
        if (this.type == null) {
            Logging.errorLog("Effect: " + effect + " does not exist!");
            return;
        }

        if (effectSplit.length == 3) {
            var range = effectSplit[1].split("-");
            if (this.type.isInstant()) {
                this.setLvl(range);
            } else {
                this.setLvl(range);
                range = effectSplit[2].split("-");
                this.setDuration(range);
            }
        } else if (effectSplit.length == 2) {
            final var range = effectSplit[1].split("-");
            if (this.type.isInstant()) {
                this.setLvl(range);
            } else {
                this.setDuration(range);
                this.maxlvl = 3;
                this.minlvl = 1;
            }
        } else {
            this.maxduration = 20;
            this.minduration = 10;
            this.maxlvl = 3;
            this.minlvl = 1;
        }
    }

    private void setLvl(final String[] range) {
        if (range.length == 1) {
            this.maxlvl = (short) BUtil.getRandomIntInRange(range[0]);
            this.minlvl = 1;
        } else {
            this.maxlvl = (short) BUtil.getRandomIntInRange(range[1]);
            this.minlvl = (short) BUtil.getRandomIntInRange(range[0]);
        }
    }

    private void setDuration(final String[] range) {
        if (range.length == 1) {
            this.maxduration = (short) BUtil.getRandomIntInRange(range[0]);
            this.minduration = (short) (this.maxduration / 8);
        } else {
            this.maxduration = (short) BUtil.getRandomIntInRange(range[1]);
            this.minduration = (short) BUtil.getRandomIntInRange(range[0]);
        }
    }

    public final PotionEffect generateEffect(final int quality) {
        var duration = this.calcDuration(quality);
        final var lvl = this.calcLvl(quality);

        if (lvl < 1 || (duration < 1 && !this.type.isInstant())) {
            return null;
        }

        duration *= 20;
        return this.type.createEffect(duration, lvl - 1);
    }

    public final void apply(final int quality, final Player player) {
        final var effect = this.generateEffect(quality);
        if (effect != null) {
            BUtil.reapplyPotionEffect(player, effect, true);
        }
    }

    public final int calcDuration(final float quality) {
        return (int) Math.round(this.minduration + ((this.maxduration - this.minduration) * (quality / 10.0)));
    }

    public final int calcLvl(final float quality) {
        return (int) Math.round(this.minlvl + ((this.maxlvl - this.minlvl) * (quality / 10.0)));
    }

    public final void writeInto(final PotionMeta meta, final int quality) {
        if ((this.calcDuration(quality) > 0 || this.type.isInstant()) && this.calcLvl(quality) > 0) {
            meta.addCustomEffect(this.type.createEffect(0, 0), true);
        } else {
            meta.removeCustomEffect(this.type);
        }
    }

    public final boolean isValid() {
        return this.type != null && this.minlvl >= 0 && this.maxlvl >= 0 && this.minduration >= 0 && this.maxduration >= 0;
    }

    public final boolean isHidden() {
        return this.hidden;
    }

    public PotionEffectType getType() {
        return this.type;
    }

    @Override
    public final String toString() {
        return this.type.getName() + "/" + this.minlvl + "-" + this.maxlvl + "/" + this.minduration + "-" + this.maxduration;
    }

    @Override
    public final BEffect clone() {
        try {
            final var clone = (BEffect) super.clone();
            clone.minlvl = this.minlvl;
            clone.maxlvl = this.maxlvl;
            clone.minduration = this.minduration;
            clone.maxduration = this.maxduration;
            clone.hidden = this.hidden;
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
