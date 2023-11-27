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

package org.geysermc.geyser.translator.protocol.bedrock.entity.player;

import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.event.bedrock.ClientEmoteEvent;
import org.geysermc.geyser.configuration.EmoteOffhandWorkaroundOption;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.SettingsUtils;

import com.github.steveice10.mc.protocol.data.game.ClientCommand;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

@Translator(packet = EmotePacket.class)
public class BedrockEmoteTranslator extends PacketTranslator<EmotePacket> {

    @Override
    public void translate(GeyserSession session, EmotePacket packet) {
        if (session.getGeyser().getConfig().getEmoteOffhandWorkaround() == EmoteOffhandWorkaroundOption.MENU) {
            
            SimpleForm.Builder emoteMenu = SimpleForm.builder()
                .title("Emote Menu")
                .button("Send Emote", FormImage.Type.PATH, "textures/ui/sprint.png")
                .button("Swap Offhand", FormImage.Type.PATH, "textures/ui/refresh.png")
                .button("Toggle Advanced Tooltips", FormImage.Type.PATH, "textures/ui/icon_recipe_equipment.png")
                .button("Advancements", FormImage.Type.PATH, "textures/ui/village_hero_effect.png")
                .button("Statistics", FormImage.Type.PATH, "textures/ui/icon_iron_pickaxe.png")
                .button("Execute Command", FormImage.Type.PATH, "textures/ui/ImpulseSquare.png")
                .button("Geyser Settings", FormImage.Type.PATH, "textures/ui/settings_glyph_color_2x.png");

            emoteMenu.closedResultHandler(() -> {
                return;
            }).validResultHandler((response) -> {
                switch (response.clickedButtonId()) {
                    case 1:
                        session.requestOffhandSwap();
                        break;

                    case 2:
                        String onOrOff = session.isAdvancedTooltips() ? "off" : "on";
                        session.setAdvancedTooltips(!session.isAdvancedTooltips());
                        session.sendMessage("§l§e" + MinecraftLocale.getLocaleString("debug.prefix", session.locale()) + " §r" + MinecraftLocale.getLocaleString("debug.advanced_tooltips." + onOrOff, session.locale()));
                        session.getInventoryTranslator().updateInventory(session, session.getPlayerInventory());
                        break;

                    case 3:
                        session.getAdvancementsCache().buildAndShowMenuForm();
                        break;

                    case 4:
                        session.setWaitingForStatistics(true);
                        ServerboundClientCommandPacket ServerboundClientCommandPacket = new ServerboundClientCommandPacket(ClientCommand.STATS);
                        session.sendDownstreamGamePacket(ServerboundClientCommandPacket);
                        break;

                    case 5:
                        session.sendCommand("give @p diamond");
                        break;

                    case 6:
                        session.sendForm(SettingsUtils.buildForm(session));
                        break;
                
                    default:
                        processEmote(session, packet);
                        break;
                }
    
                return;
            });
            
            session.sendForm(emoteMenu);

            return;
        }

        if (session.getGeyser().getConfig().getEmoteOffhandWorkaround() != EmoteOffhandWorkaroundOption.DISABLED) {
            // Activate the workaround - we should trigger the offhand now
            session.requestOffhandSwap();

            if (session.getGeyser().getConfig().getEmoteOffhandWorkaround() == EmoteOffhandWorkaroundOption.NO_EMOTES) {
                return;
            }
        }

        processEmote(session, packet);
    }

    /**
     * Process an EmotePacket to be played
     *
     * @param session the session of the emoter
     * @param packet the packet with the emote data
     */
    private void processEmote(GeyserSession session, EmotePacket packet) {
        // For the future: could have a method that exposes which players will see the emote
        ClientEmoteEvent event = new ClientEmoteEvent(session, packet.getEmoteId());
        session.getGeyser().eventBus().fire(event);
        if (event.isCancelled()) {
            return;
        }

        int javaId = session.getPlayerEntity().getEntityId();
        String xuid = session.getAuthData().xuid();
        String emote = packet.getEmoteId();
        for (GeyserSession otherSession : session.getGeyser().getSessionManager().getSessions().values()) {
            if (otherSession != session) {
                if (otherSession.isClosed()) continue;
                if (otherSession.getEventLoop().inEventLoop()) {
                    playEmote(otherSession, javaId, xuid, emote);
                } else {
                    otherSession.executeInEventLoop(() -> playEmote(otherSession, javaId, xuid, emote));
                }
            }
        }
    }

    /**
     * Play an emote by an emoter to the given session.
     * This method must be called within the session's event loop.
     *
     * @param session the session to show the emote to
     * @param emoterJavaId the java id of the emoter
     * @param emoterXuid the xuid of the emoter
     * @param emoteId the emote to play
     */
    private static void playEmote(GeyserSession session, int emoterJavaId, String emoterXuid, String emoteId) {
        Entity emoter = session.getEntityCache().getEntityByJavaId(emoterJavaId); // Must be ran on same thread
        if (emoter instanceof PlayerEntity) {
            EmotePacket packet = new EmotePacket();
            packet.setRuntimeEntityId(emoter.getGeyserId());
            packet.setXuid(emoterXuid);
            packet.setPlatformId(""); // BDS sends empty
            packet.setEmoteId(emoteId);
            session.sendUpstreamPacket(packet);
        }
    }
}
