// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.Dim;

/** Uses Morphe's native toolbar hook, retaining YouTube's original search action. */
public final class RoomToolbar {
    private static final String TAG = "app.nyanime.rooms.toolbar";
    private static final WeakHashMap<ImageView, Boolean> icons = new WeakHashMap<>();
    private static WeakReference<ImageView> search = new WeakReference<>(null);
    private static final WeakHashMap<ImageView, Boolean> searchButtons = new WeakHashMap<>();
    private RoomToolbar() { }

    public static void setSearchButtonView(String enumName, View parentView, ImageView imageView) {
        if (!"SEARCH".equals(enumName) && !"SEARCH_BOLD".equals(enumName) && !"SEARCH_CAIRO".equals(enumName)) return;
        search = new WeakReference<>(imageView);
        searchButtons.put(imageView, true);
        install(parentView, enumName);
        // YouTube can invoke the hook before attaching the menu item to its toolbar.
        parentView.post(() -> install(parentView, enumName));
    }
    private static void install(View source, String enumName) {
        try {
            View item = source;
            ViewGroup toolbar = null;
            int toolbarId = ResourceUtils.getIdIdentifier("toolbar");
            while (item.getParent() instanceof ViewGroup parent) {
                if (item.getId() != View.NO_ID) {
                    try {
                        String name = item.getResources().getResourceEntryName(item.getId());
                        if (name.startsWith("menu_item_")) { toolbar = parent; break; }
                    } catch (android.content.res.Resources.NotFoundException ignored) { /* Runtime-generated view ID. */ }
                }
                if (toolbarId != 0 && parent.getId() == toolbarId) { toolbar = parent; break; }
                item = parent;
            }
            if (toolbar == null || toolbar.findViewWithTag(TAG) != null) return;
            Drawable drawable = ResourceUtils.getDrawable("nya_yt_room" + ("SEARCH".equals(enumName) ? "" : "_bold"));
            if (drawable == null) return;
            FrameLayout button = new FrameLayout(item.getContext());
            button.setTag(TAG); button.setFocusable(true); button.setClickable(true);
            button.setMinimumWidth(Dim.dp48); button.setMinimumHeight(Dim.dp48);
            button.setTooltipText("Guarda insieme"); button.setOnClickListener(RoomsPatch::show);
            GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(Dim.dp24);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000 | (ThemeUtils.getAppForegroundColor() & 0xFFFFFF)), null, mask));
            ImageView icon = new ImageView(item.getContext()); icon.setImageDrawable(drawable.mutate());
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            button.addView(icon, new FrameLayout.LayoutParams(Dim.dp24, Dim.dp24, Gravity.CENTER));
            icons.put(icon, true);
            ViewGroup.LayoutParams params = copyParams(item.getLayoutParams()); params.width = Dim.dp48;
            if (params instanceof LinearLayout.LayoutParams linear) linear.weight = 0;
            toolbar.addView(button, Math.max(0, toolbar.indexOfChild(item)), params);
            refresh();
        } catch (Exception error) { Logger.printException(() -> "Room toolbar unavailable", error); }
    }
    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams original) {
        if (original instanceof LinearLayout.LayoutParams linear) return new LinearLayout.LayoutParams(linear);
        if (original instanceof FrameLayout.LayoutParams frame) return new FrameLayout.LayoutParams(frame);
        if (original instanceof ViewGroup.MarginLayoutParams margins) return new ViewGroup.MarginLayoutParams(margins);
        return original == null ? new ViewGroup.LayoutParams(Dim.dp48, Dim.dp48) : new ViewGroup.LayoutParams(original);
    }
    static void refresh() {
        boolean active = RoomsPatch.active();
        for (ImageView icon : icons.keySet()) {
            icon.setColorFilter(active ? 0xFF16834A : ThemeUtils.getAppForegroundColor());
            if (icon.getParent() instanceof View parent) parent.setContentDescription(active ? "Room attiva · Apri Guarda insieme" : "Guarda insieme");
        }
    }
    static boolean openSearch() {
        ImageView original = search.get();
        if (original == null || !original.isAttachedToWindow() || !original.isShown()) {
            original = null;
            for (ImageView candidate : searchButtons.keySet()) {
                if (candidate.isAttachedToWindow() && candidate.isShown()) { original = candidate; break; }
            }
        }
        if (original == null) return false;
        if (original.callOnClick()) return true;
        return original.getParent() instanceof View parent && parent.performClick();
    }
}
