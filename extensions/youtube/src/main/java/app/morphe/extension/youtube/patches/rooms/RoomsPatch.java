// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.sponsorblock.SegmentPlaybackController;
import app.nyanime.rooms.core.*;

/** Android owns lifecycle/UI; protocol, authority and clock correction live in room-core. */
public final class RoomsPatch {
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final RoomsPlayer PLAYER = new RoomsPlayer();
    static final RoomController.Clock CLOCK = new RoomController.Clock() {
        public long now() { return System.currentTimeMillis(); }
        public long monotonic() { return SystemClock.elapsedRealtime(); }
    };
    static RoomController controller;
    static RoomAdmission admission;
    static RoomIdentity identity;
    static String lastMessage = "";
    private static WeakReference<RoomPanel> panel = new WeakReference<>(null);
    private static boolean ticking;
    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            try {
                if (controller != null && controller.state().active) controller.tick();
                if (admission != null) admission.tick();
            } catch (RuntimeException error) { leave(); lastMessage = "La room si è interrotta. Puoi riprovare."; }
            refresh();
            if (busy()) MAIN.postDelayed(this, 250); else ticking = false;
        }
    };
    private RoomsPatch() { }
    public static void attachPlayer(Object player) { RoomsPlayer.attach(player); }
    static boolean active() { return controller != null && controller.state().active; }
    static boolean busy() { return active() || admission != null; }
    public static void show(View source) {
        Activity activity = Utils.getActivity();
        android.content.Context context = source == null ? null : source.getContext();
        while (context instanceof android.content.ContextWrapper wrapper) {
            if (context instanceof Activity found) { activity = found; break; }
            context = wrapper.getBaseContext();
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        RoomPanel previous = panel.get();
        if (previous != null) previous.dismiss();
        RoomPanel next = new RoomPanel(activity); panel = new WeakReference<>(next); next.show();
    }
    static String nickname() { return Utils.getContext().getSharedPreferences("nya_rooms", 0).getString("name", "Ospite"); }
    private static String saveName(String name) {
        String cleaned = RoomProtocol.cleanName(name);
        Utils.getContext().getSharedPreferences("nya_rooms", 0).edit().putString("name", cleaned).apply();
        return cleaned;
    }
    static void create(String name) {
        if (busy()) return;
        name = saveName(name); identity = new RoomIdentity();
        RoomInvite invite = RoomInvite.create(identity.publicKey, CLOCK.now());
        suspend(true); startController(invite, name, false);
        admission = admission(RoomAdmission.newCode(), name, invite);
        controller.start(); admission.start(); runTicks(); refresh();
    }
    static void join(String input, String name) {
        if (busy()) return;
        String code = RoomInvite.shortCode(input);
        if (code.isEmpty()) { lastMessage = "Inserisci il codice di 8 cifre"; refresh(); return; }
        name = saveName(name); identity = new RoomIdentity(); suspend(true); PLAYER.pause(true);
        admission = admission(code, name, null); admission.start(); runTicks(); refresh();
    }
    private static RoomAdmission admission(String code, String name, RoomInvite hostInvite) {
        return new RoomAdmission(identity, code, name, hostInvite, hostInvite == null ? null : controller, CLOCK,
                (id, shortCode) -> new NostrTransport(new NostrCodec(id, RoomAdmission.discoveryKey(shortCode),
                        RoomAdmission.discoveryTopic(shortCode), RoomProtocol.DISCOVERY_KIND), RoomInvite.RELAYS, MAIN::post),
                new RoomAdmission.Listener() {
                    @Override public void changed() { refresh(); }
                    @Override public void granted(RoomInvite invite) { admission = null; startController(invite, nickname(), true); refresh(); }
                    @Override public void failed(String message) { leave(); lastMessage = message; refresh(); }
                });
    }
    private static void startController(RoomInvite invite, String name, boolean startImmediately) {
        RoomTransport transport = new NostrTransport(new NostrCodec(identity, RoomIdentity.bytes(invite.secret, 32),
                invite.topic(), RoomProtocol.KIND), invite.relays, MAIN::post);
        controller = new RoomController(invite, identity.publicKey, name, PLAYER, CLOCK, transport, state -> {
            if (!state.active) {
                lastMessage = state.message;
                if (admission != null) { admission.close(); admission = null; }
                if (identity != null) { identity.close(); identity = null; }
                suspend(false);
            }
            refresh();
        });
        lastMessage = "";
        if (startImmediately) { controller.start(); runTicks(); }
    }
    private static void suspend(boolean value) { SegmentPlaybackController.setRoomSuspended(value, app.morphe.extension.youtube.patches.VideoInformation.getVideoId()); }
    private static void runTicks() { if (!ticking) { ticking = true; MAIN.post(TICK); } }
    static void leave() {
        if (admission != null) { admission.close(); admission = null; }
        if (active()) controller.close();
        controller = null;
        if (identity != null) { identity.close(); identity = null; }
        suspend(false); refresh();
    }
    static void refresh() {
        RoomPanel current = panel.get(); if (current != null) current.refresh();
        RoomButton.refresh(); RoomToolbar.refresh();
    }
}
