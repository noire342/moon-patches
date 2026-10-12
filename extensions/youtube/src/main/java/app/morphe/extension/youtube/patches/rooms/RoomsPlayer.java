// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

import android.content.Intent;
import android.net.Uri;
import java.lang.ref.WeakReference;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.shared.ShortsPlayerState;
import app.nyanime.rooms.core.RoomController;

final class RoomsPlayer implements RoomController.Player {
    private static WeakReference<RoomPlayerBridge> nativePlayer = new WeakReference<>(null);
    static void attach(Object player) {
        if (player instanceof RoomPlayerBridge bridge) nativePlayer = new WeakReference<>(bridge);
    }
    private static boolean regularPlayerOpen() {
        if (ShortsPlayerState.isOpen() || VideoInformation.lastVideoIdIsShort()) return false;
        return switch (PlayerType.getCurrent()) {
            case WATCH_WHILE_MINIMIZED, WATCH_WHILE_MAXIMIZED, WATCH_WHILE_FULLSCREEN,
                 WATCH_WHILE_SLIDING_MAXIMIZED_FULLSCREEN, WATCH_WHILE_SLIDING_MINIMIZED_MAXIMIZED,
                 WATCH_WHILE_PICTURE_IN_PICTURE, VIRTUAL_REALITY_FULLSCREEN -> true;
            default -> false;
        };
    }
    @Override public RoomController.Sample sample() {
        RoomController.Sample sample = new RoomController.Sample();
        // Feed previews and a dismissed player are not a video selected for the room.
        // Do not expose their IDs or call their playWhenReady setter from a lobby.
        if (!regularPlayerOpen()) return sample;
        sample.mediaId = VideoInformation.getVideoId();
        sample.title = VideoInformation.getVideoTitle();
        sample.speed = VideoInformation.getPlaybackSpeed();
        RoomPlayerBridge bridge = nativePlayer.get();
        if (bridge == null) return sample;
        try {
            int state = bridge.patch_roomPlaybackState();
            sample.position = Math.max(0, bridge.patch_roomPosition());
            sample.duration = Math.max(0, bridge.patch_roomDuration());
            sample.paused = !bridge.patch_roomPlayWhenReady();
            sample.buffering = state == 2;
            sample.ended = state == 4;
            sample.ready = (state == 2 || state == 3 || state == 4) && sample.mediaId.matches("[A-Za-z0-9_-]{11}")
                    && sample.duration > 0 && sample.duration <= 86_400_000;
        } catch (RuntimeException ignored) { /* Native constructor may not have finished yet. */ }
        return sample;
    }
    @Override public boolean pause(boolean paused) {
        if (!regularPlayerOpen()) return false;
        RoomPlayerBridge bridge = nativePlayer.get();
        if (bridge == null) return false;
        try { bridge.patch_roomPlayWhenReady(!paused); return true; } catch (RuntimeException ignored) { return false; }
    }
    @Override public boolean seek(long millis) { return VideoInformation.seekTo(millis); }
    @Override public void speed(double speed) { VideoInformation.changePlaybackSpeed((float) speed); }
    @Override public void open(String provider, String mediaId) {
        if (!provider.equals("youtube") || !mediaId.matches("[A-Za-z0-9_-]{11}")) return;
        var context = Utils.getActivity() != null ? Utils.getActivity() : Utils.getContext();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + mediaId));
        intent.setPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); } catch (RuntimeException ignored) { Utils.showToastShort("Apri il video condiviso per sincronizzarti"); }
    }
}
