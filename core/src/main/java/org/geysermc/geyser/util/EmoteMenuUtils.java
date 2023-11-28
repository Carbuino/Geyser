/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.util;

import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.BedrockEmoteTranslator;

import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

public class EmoteMenuUtils {
    public static SimpleForm buildEmoteForm(GeyserSession session, EmotePacket packet) {
        SimpleForm.Builder builder = SimpleForm.builder()
            .title("Emote Menu")
            .button("Send Emote", FormImage.Type.PATH, "textures/ui/sprint.png")
            .button("Swap Offhand", FormImage.Type.PATH, "textures/ui/refresh.png")
            .button("Toggle Advanced Tooltips", FormImage.Type.PATH, "textures/ui/icon_recipe_equipment.png")
            .button("Advancements", FormImage.Type.PATH, "textures/ui/village_hero_effect.png")
            .button("Statistics", FormImage.Type.PATH, "textures/items/iron_pickaxe.png")
            .button("Execute Command", FormImage.Type.PATH, "textures/blocks/command_block.png")
            .button("Geyser Settings", FormImage.Type.PATH, "textures/ui/settings_glyph_color_2x.png");

        builder.closedResultHandler(() -> {
            return;
        }).validResultHandler((response) -> {
            switch (response.clickedButtonId()) {
                // Swap offhand
                case 1:
                    if (checkSessionPermission(session, "geyser.command.offhand") == true) {
                        session.requestOffhandSwap();
                    }
                    break;
                // Toggle Advanced Tooltips
                case 2:
                    if (checkSessionPermission(session, "geyser.command.tooltips") == true) {
                        String onOrOff = session.isAdvancedTooltips() ? "off" : "on";
                        session.setAdvancedTooltips(!session.isAdvancedTooltips());
                        session.sendMessage("§l§e" + MinecraftLocale.getLocaleString("debug.prefix", session.locale()) + " §r" + MinecraftLocale.getLocaleString("debug.advanced_tooltips." + onOrOff, session.locale()));
                        session.getInventoryTranslator().updateInventory(session, session.getPlayerInventory());
                    }
                    break;
                // View Advancements
                case 3:
                    if (checkSessionPermission(session, "geyser.command.advancements") == true) {
                        session.getAdvancementsCache().buildAndShowMenuForm();
                    }
                    break;
                // View Statistics
                case 4:
                    if (checkSessionPermission(session, "geyser.command.statistics") == true) {
                        session.setWaitingForStatistics(true);
                        ServerboundClientCommandPacket ServerboundClientCommandPacket = new ServerboundClientCommandPacket(ClientCommand.STATS);
                        session.sendDownstreamGamePacket(ServerboundClientCommandPacket);
                    }
                    break;
                // Execute Command
                case 5:
                    session.sendForm(EmoteMenuUtils.buildCommandForm(session, packet));
                    break;
                // View Geyser Settings
                case 6:
                    if (checkSessionPermission(session, "geyser.command.settings") == true) {
                        session.sendForm(SettingsUtils.buildForm(session));
                    }
                    break;
                // Send Emote
                default:
                    BedrockEmoteTranslator.processEmote(session, packet);
                    break;
            }
        });

        return builder.build();
    }

    private static boolean checkSessionPermission(GeyserSession session, String permission) {
        if (session.getGeyser().getPlatformType() == PlatformType.STANDALONE || session.hasPermission(permission)) {
            return true;
        }
        return false;
    }

    /**
     * Build a settings form for the given session and store it for later
     *
     * @param session The session to build the form for
     */
    public static CustomForm buildCommandForm(GeyserSession session, EmotePacket packet) {
        CustomForm.Builder builder = CustomForm.builder()
            .title("Emote Menu - Execute Command")
            .input("Command", "Enter a command");

        builder.closedResultHandler(() -> {
            session.sendForm(EmoteMenuUtils.buildEmoteForm(session, packet));
        }).validResultHandler((response) -> {
            session.sendCommand(response.asInput(0));
        });

        return builder.build();
    }
}
