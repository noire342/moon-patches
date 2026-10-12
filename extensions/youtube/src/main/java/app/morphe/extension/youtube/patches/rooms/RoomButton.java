// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

import android.view.View;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.patches.LegacyPlayerControlsPatch;
import app.morphe.extension.youtube.videoplayer.PlayerOverlayButton;
import app.morphe.extension.youtube.videoplayer.PlayerIcons;
import app.morphe.extension.youtube.videoplayer.LegacyPlayerControlButton;

public final class RoomButton {
    private static WeakReference<ImageView> button = new WeakReference<>(null);
    public static void initializeButton(View source) {
        if (LegacyPlayerControlsPatch.RESTORE_OLD_PLAYER_BUTTONS) return;
        try {
            ImageView view = PlayerOverlayButton.addButton(source, PlayerIcons.name("nya_yt_room"), RoomsPatch::show, v -> { RoomsPatch.show(v); return true; });
            if (view != null) { button = new WeakReference<>(view); view.setContentDescription("Guarda insieme"); refresh(); }
        } catch (Exception error) { Logger.printException(() -> "Room button unavailable", error); }
    }
    public static void initializeLegacyButton(View source) {
        if (!LegacyPlayerControlsPatch.RESTORE_OLD_PLAYER_BUTTONS) return;
        new LegacyPlayerControlButton(source, "nya_room_button", null, "nya_yt_room",
                () -> LegacyPlayerControlButton.ButtonVisibility.ENABLED, RoomsPatch::show, v -> { RoomsPatch.show(v); return true; });
    }
    static void refresh() {
        ImageView view = button.get();
        if (view != null) {
            view.setColorFilter(RoomsPatch.active() ? 0xFF70DFA5 : 0xFFFFFFFF);
            view.setContentDescription(RoomsPatch.active() ? "Room attiva · Apri Guarda insieme" : "Guarda insieme");
        }
    }
}
