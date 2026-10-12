// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.nyanime.rooms.core;

/** All callbacks must be delivered on the controller's dispatcher. */
public interface RoomTransport extends AutoCloseable {
    interface Listener {
        void message(String sender, String plaintext);
        void connection(int writableRelays);
    }
    void start(Listener listener);
    void send(String plaintext);
    @Override void close();
}
