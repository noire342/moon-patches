// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

/** Implemented on the native Media3 player during patching. */
public interface RoomPlayerBridge {
    void patch_roomPlayWhenReady(boolean play);
    boolean patch_roomPlayWhenReady();
    int patch_roomPlaybackState();
    long patch_roomPosition();
    long patch_roomDuration();
}
