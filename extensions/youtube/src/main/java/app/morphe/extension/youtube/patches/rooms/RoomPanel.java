// SPDX-License-Identifier: GPL-3.0-only
// Copyright 2026 Moon Patches contributors.

package app.morphe.extension.youtube.patches.rooms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.nyanime.rooms.core.*;
import java.util.Locale;

/** Theme-aware native sheet. Playback updates never rebuild fields or move focus. */
final class RoomPanel {
    private final Activity activity;
    private final Dialog dialog;
    private final boolean dark = Utils.isDarkModeEnabled();
    private final int surface = ThemeUtils.getDialogBackgroundColor();
    private final int card = ThemeUtils.getCancelOrNeutralButtonBackgroundColor();
    private final int ink = ThemeUtils.getAppForegroundColor();
    private final int muted = dark ? 0xFFAAAAAA : 0xFF606060;
    private final int accent = dark ? 0xFF3EA6FF : 0xFF065FD4;
    private final int errorColor = dark ? 0xFFFF8A80 : 0xFFB3261E;
    private LinearLayout content, members, requests, playbackControls, options;
    private TextView status, title, clock, notice, play, seekBack, seekForward;
    private TextView videoHint, chooseVideo, controlHint, joinAction, codeError;
    private Switch shared, wait;
    private EditText name, code;
    private String mode = "", lastRequests = "", lastMembers = "";
    private long lastRefresh;
    private boolean showingHostInvite, closed, joinTab;

    RoomPanel(Activity activity) {
        this.activity = activity; dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> closed = true);
    }
    void show() { closed = false; refresh(); dialog.show(); configureWindow(); }
    void dismiss() { hideKeyboard(); dialog.dismiss(); }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return v;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(activity), new LinearLayout.LayoutParams(1, dp(height))); }
    private void gap(LinearLayout parent) { parent.addView(new View(activity), new LinearLayout.LayoutParams(dp(8), 1)); }
    private TextView action(String label, boolean primary, Runnable click) {
        TextView v = text(label, 14, primary ? (dark ? 0xFF0F0F0F : Color.WHITE) : ink, true);
        v.setGravity(Gravity.CENTER); v.setMinHeight(dp(48)); v.setPadding(dp(16), dp(12), dp(16), dp(12));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), shape(primary ? accent : card, 24), null));
        v.setOnClickListener(ignored -> click.run()); v.setFocusable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName(Button.class.getName());
            }
        }); return v;
    }
    private void fullAction(LinearLayout parent, String label, boolean primary, Runnable click) { parent.addView(action(label, primary, click), new LinearLayout.LayoutParams(-1, -2)); }
    private void enabled(TextView action, boolean value) { action.setEnabled(value); action.setAlpha(value ? 1 : 0.4f); }
    private void setText(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
    private void label(LinearLayout parent, String value) { parent.addView(text(value, 12, muted, true)); space(parent, 8); }
    private EditText field(String hint, String value, boolean digits) {
        EditText edit = new EditText(activity); edit.setSingleLine(); edit.setTextSize(digits ? 20 : 16); edit.setTextColor(ink); edit.setHintTextColor(muted);
        edit.setHint(hint); edit.setText(value); edit.setPadding(dp(16), dp(12), dp(16), dp(12)); edit.setMinHeight(dp(52));
        edit.setBackground(shape(card, 12)); edit.setContentDescription(hint);
        edit.setInputType(digits ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(digits ? 8 : 40)}); edit.setSelectAllOnFocus(true);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        if (digits) { edit.setTypeface(Typeface.MONOSPACE); edit.setLetterSpacing(0.08f); } return edit;
    }
    private void hideKeyboard() {
        Window window = dialog.getWindow(); if (window == null) return;
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(window.getDecorView().getWindowToken(), 0);
    }
    private void configureWindow() {
        Window window = dialog.getWindow(); if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent); window.setGravity(Gravity.BOTTOM);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(0.42f);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(600)), -2);
        window.setNavigationBarColor(surface); window.getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    void refresh() {
        if (closed) return;
        if (activity.isFinishing() || activity.isDestroyed()) { dismiss(); return; }
        String next = RoomsPatch.active() ? "active" : RoomsPatch.admission != null ? "joining" : "idle";
        boolean hasHostInvite = RoomsPatch.active() && RoomsPatch.controller.state().host && RoomsPatch.admission != null;
        if (!next.equals(mode) || hasHostInvite != showingHostInvite) { hideKeyboard(); mode = next; showingHostInvite = hasHostInvite; build(); }
        if (SystemClock.elapsedRealtime() - lastRefresh < 500) return; lastRefresh = SystemClock.elapsedRealtime();
        setText(notice, RoomsPatch.lastMessage); notice.setVisibility(RoomsPatch.lastMessage.isEmpty() ? View.GONE : View.VISIBLE);
        if (mode.equals("joining")) { setText(status, RoomsPatch.admission.status()); return; }
        if (!mode.equals("active")) return;
        RoomController.State state = RoomsPatch.controller.state(); setText(status, state.message);
        status.setTextColor(state.phase == RoomProtocol.Phase.RECONNECTING ? muted : accent);
        boolean hasVideo = !state.mediaId.isEmpty();
        setText(title, !hasVideo ? (state.host ? "Scegli cosa guardare" : "In attesa di un video") : state.title.isEmpty() ? "Video della room" : state.title);
        RoomController.Sample sample = RoomsPatch.PLAYER.sample(); setText(clock, time(sample.position) + " / " + time(sample.duration));
        clock.setVisibility(hasVideo ? View.VISIBLE : View.GONE); videoHint.setVisibility(hasVideo ? View.GONE : View.VISIBLE);
        chooseVideo.setVisibility(!hasVideo && state.host ? View.VISIBLE : View.GONE); playbackControls.setVisibility(hasVideo ? View.VISIBLE : View.GONE);
        setText(controlHint, (state.sharedControls ? "Tutti possono mettere in pausa e spostare il video." : "I comandi del video sono gestiti dall’host.") + "\nSponsorBlock è sospeso nella room.");
        boolean canControl = state.canControl() && hasVideo; setText(play, sample.paused ? "Riprendi per tutti" : "Pausa per tutti");
        enabled(play, canControl); enabled(seekBack, canControl); enabled(seekForward, canControl); refreshRequests();
        StringBuilder memberKey = new StringBuilder().append(hasVideo);
        for (RoomProtocol.Member m : state.members) memberKey.append(m.id).append(m.name).append(m.ready).append(m.buffering);
        if (!lastMembers.equals(memberKey.toString())) {
            lastMembers = memberKey.toString(); members.removeAllViews(); label(members, "Partecipanti · " + state.members.size() + "/8");
            for (int index = 0; index < state.members.size(); index++) {
                RoomProtocol.Member member = state.members.get(index);
                boolean self = RoomsPatch.identity != null && member.id.equals(RoomsPatch.identity.publicKey);
                String role = (index == 0 ? "Host" : "Ospite") + (self ? " · Tu" : "");
                LinearLayout line = row(); line.setPadding(dp(12), dp(10), dp(12), dp(10)); line.setBackground(shape(card, 12));
                String initial = member.name.isEmpty() ? "?" : member.name.substring(0, member.name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
                TextView avatar = text(initial, 16, accent, true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(shape(surface, 18));
                avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); line.addView(avatar, new LinearLayout.LayoutParams(dp(36), dp(36))); gap(line);
                LinearLayout person = column(); person.addView(text(member.name + " · " + role, 14, ink, true)); space(person, 3);
                person.addView(text(!hasVideo ? "Nella room" : member.buffering ? "Caricamento…" : member.ready ? "Pronto" : "In attesa…", 12, muted, false));
                line.addView(person, new LinearLayout.LayoutParams(0, -2, 1)); members.addView(line, new LinearLayout.LayoutParams(-1, -2)); space(members, 6);
            }
        }
    }
    private void refreshRequests() {
        if (requests == null || RoomsPatch.admission == null) return;
        StringBuilder keys = new StringBuilder(); for (RoomAdmission.Request r : RoomsPatch.admission.requests()) keys.append(r.id);
        if (lastRequests.equals(keys.toString())) return; lastRequests = keys.toString(); requests.removeAllViews();
        for (RoomAdmission.Request request : RoomsPatch.admission.requests()) {
            LinearLayout requestCard = column(); requestCard.setPadding(dp(12), dp(12), dp(12), dp(12)); requestCard.setBackground(shape(card, 12));
            requestCard.addView(text(request.name + " vuole entrare", 15, ink, true)); space(requestCard, 6);
            requestCard.addView(text("Verifica persona: " + request.fingerprint(), 13, muted, false)); space(requestCard, 10);
            LinearLayout decisions = row();
            decisions.addView(action("Accetta", true, () -> { if (!RoomsPatch.admission.approve(request.id)) Utils.showToastShort("La room è piena"); lastRequests = "!"; refresh(); }), new LinearLayout.LayoutParams(0, -2, 1)); gap(decisions);
            decisions.addView(action("Rifiuta", false, () -> { RoomsPatch.admission.reject(request.id); lastRequests = "!"; refresh(); }), new LinearLayout.LayoutParams(0, -2, 1));
            requestCard.addView(decisions); requests.addView(requestCard, new LinearLayout.LayoutParams(-1, -2)); space(requests, 12);
        }
        if (!keys.isEmpty()) requests.announceForAccessibility("Nuova richiesta di ingresso nella room");
    }
    private void build() {
        lastRefresh = 0; lastRequests = "!"; lastMembers = "!"; requests = null;
        LinearLayout root = column(); root.setPadding(dp(20), 0, dp(20), dp(16)); root.setFocusableInTouchMode(true);
        GradientDrawable background = shape(surface, 24); background.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0}); root.setBackground(background);
        FrameLayout drag = new FrameLayout(activity); View handle = new View(activity); handle.setBackground(shape(muted, 2));
        drag.addView(handle, new FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER)); root.addView(drag, new LinearLayout.LayoutParams(-1, dp(28)));
        drag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        drag.setOnTouchListener(new View.OnTouchListener() {
            float start;
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) { start = event.getRawY(); return true; }
                if (event.getAction() == MotionEvent.ACTION_UP && event.getRawY() - start > dp(56)) dismiss(); return true;
            }
        });
        LinearLayout heading = row(); TextView headingText = text("Guarda insieme", 20, ink, true); headingText.setAccessibilityHeading(true);
        heading.addView(headingText, new LinearLayout.LayoutParams(0, -2, 1)); TextView close = action("×", false, this::dismiss);
        close.setTextSize(28); close.setTypeface(Typeface.DEFAULT); close.setPadding(0, 0, 0, 0);
        close.setContentDescription("Chiudi il pannello, la room resta aperta"); close.setTooltipText("Chiudi il pannello");
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), null, shape(Color.WHITE, 24)));
        heading.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(heading); space(root, 12);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                Rect visible = new Rect(); dialog.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
                int screen = activity.getResources().getDisplayMetrics().heightPixels, available = visible.height() > 0 ? visible.height() : screen;
                int maximum = Math.max(dp(100), Math.min((int) (screen * 0.78), available) - dp(108));
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) maximum = Math.min(maximum, MeasureSpec.getSize(heightMeasureSpec));
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false); scroll.setClipToPadding(false); content = column(); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        notice = text(RoomsPatch.lastMessage, 13, accent, false); notice.setPadding(0, 0, 0, dp(12)); notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(notice);
        if (mode.equals("idle")) buildIdle(); else if (mode.equals("joining")) buildJoining(); else buildActive();
        dialog.setContentView(root); root.requestFocus(); configureWindow();
    }
    private void buildIdle() {
        content.addView(text("Un video, lo stesso momento.", 15, ink, true)); space(content, 6);
        content.addView(text("Invita chi vuoi. Il video puoi sceglierlo dopo.", 14, muted, false)); space(content, 20);
        label(content, "Il tuo nome"); name = field("Nome nella room", RoomsPatch.nickname(), false); content.addView(name); space(content, 16);
        LinearLayout tabs = row(); TextView createTab = action("Crea", false, () -> {}), enterTab = action("Entra", false, () -> {});
        tabs.addView(createTab, new LinearLayout.LayoutParams(0, -2, 1)); gap(tabs); tabs.addView(enterTab, new LinearLayout.LayoutParams(0, -2, 1)); content.addView(tabs); space(content, 16);
        LinearLayout createPane = column(), joinPane = column(); content.addView(createPane); content.addView(joinPane);
        fullAction(createPane, "Crea una room", true, this::create); space(createPane, 12);
        createPane.addView(text("Ricevi un codice da condividere. Tu approvi gli ingressi.", 12, muted, false));
        label(joinPane, "Codice dell’invito"); LinearLayout codeRow = row(); code = field("8 cifre", "", true);
        codeRow.addView(code, new LinearLayout.LayoutParams(0, -2, 1)); gap(codeRow); codeRow.addView(action("Incolla", false, this::paste)); joinPane.addView(codeRow); space(joinPane, 8);
        codeError = text("", 12, errorColor, false); codeError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); codeError.setVisibility(View.GONE); joinPane.addView(codeError);
        joinAction = action("Entra nella room", true, this::join); enabled(joinAction, false); joinPane.addView(joinAction, new LinearLayout.LayoutParams(-1, -2));
        space(joinPane, 12); joinPane.addView(text("L’host deve approvare la tua richiesta.", 12, muted, false));
        code.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { enabled(joinAction, !RoomInvite.shortCode(s.toString()).isEmpty()); codeError.setVisibility(View.GONE); }
            public void afterTextChanged(Editable s) {}
        });
        code.setOnEditorActionListener((view, id, event) -> { if (id == EditorInfo.IME_ACTION_DONE) { join(); return true; } return false; });
        name.setOnEditorActionListener((view, id, event) -> { if (id == EditorInfo.IME_ACTION_DONE) { if (joinTab) code.requestFocus(); else create(); return true; } return false; });
        Runnable selectTab = () -> {
            createPane.setVisibility(joinTab ? View.GONE : View.VISIBLE); joinPane.setVisibility(joinTab ? View.VISIBLE : View.GONE);
            createTab.setSelected(!joinTab); enterTab.setSelected(joinTab);
            createTab.setTextColor(joinTab ? muted : ink); enterTab.setTextColor(joinTab ? ink : muted);
            int ripple = dark ? 0x33FFFFFF : 0x22000000;
            createTab.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape(joinTab ? surface : card, 24), null));
            enterTab.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), shape(joinTab ? card : surface, 24), null));
        };
        createTab.setOnClickListener(v -> { hideKeyboard(); joinTab = false; selectTab.run(); }); enterTab.setOnClickListener(v -> { hideKeyboard(); joinTab = true; selectTab.run(); }); selectTab.run();
        space(content, 18); content.addView(text("SponsorBlock viene sospeso per tutta la sessione.", 12, muted, false));
    }
    private void create() { hideKeyboard(); RoomsPatch.create(name.getText().toString()); }
    private void join() {
        if (RoomInvite.shortCode(code.getText().toString()).isEmpty()) { setText(codeError, "Inserisci tutte le 8 cifre del codice."); codeError.setVisibility(View.VISIBLE); code.requestFocus(); return; }
        hideKeyboard(); RoomsPatch.join(code.getText().toString(), name.getText().toString());
    }
    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE); ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip(); String parsed = "";
        if (clip != null && clip.getItemCount() > 0) parsed = RoomInvite.shortCode(clip.getItemAt(0).coerceToText(activity).toString());
        if (!parsed.isEmpty()) { code.setText(parsed); code.setSelection(code.length()); }
        else { setText(codeError, "Nessun codice valido negli appunti."); codeError.setVisibility(View.VISIBLE); }
    }
    private void buildJoining() {
        LinearLayout connection = row(); ProgressBar progress = new ProgressBar(activity); progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        connection.addView(progress, new LinearLayout.LayoutParams(dp(24), dp(24))); gap(connection);
        status = text("Connessione…", 16, ink, true); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        connection.addView(status, new LinearLayout.LayoutParams(0, -2, 1)); content.addView(connection); space(content, 20);
        LinearLayout identity = column(); identity.setPadding(dp(16), dp(14), dp(16), dp(14)); identity.setBackground(shape(card, 16));
        identity.addView(text("La tua verifica persona", 12, muted, true)); space(identity, 8);
        TextView fingerprint = text(RoomsPatch.admission.ownFingerprint(), 22, ink, true); fingerprint.setTypeface(Typeface.MONOSPACE); identity.addView(fingerprint); space(identity, 8);
        identity.addView(text("Comunicala all’host per riconoscere la tua richiesta.", 13, muted, false)); content.addView(identity); space(content, 16);
        fullAction(content, "Annulla richiesta", false, RoomsPatch::leave); space(content, 14); content.addView(text("SponsorBlock è già sospeso durante la connessione.", 12, muted, false));
    }
    private void buildActive() {
        RoomController.State state = RoomsPatch.controller.state(); status = text("Connessione…", 13, accent, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(status); space(content, 12);
        if (state.host && RoomsPatch.admission != null) { requests = column(); content.addView(requests); }
        title = text(state.title, 18, ink, true); title.setMaxLines(3); content.addView(title); space(content, 8);
        clock = text("", 12, muted, false); clock.setTypeface(Typeface.MONOSPACE); content.addView(clock);
        videoHint = text(state.host ? "Invita i partecipanti e scegli un video. Si aprirà anche per loro." : "L’host sceglierà il video. Si aprirà qui automaticamente.", 14, muted, false);
        videoHint.setPadding(0, 0, 0, dp(14)); content.addView(videoHint);
        chooseVideo = action("Scegli un video", true, () -> { dismiss(); if (!RoomToolbar.openSearch()) Utils.showToastShort("Scegli un video su YouTube: la room resta aperta"); });
        content.addView(chooseVideo, new LinearLayout.LayoutParams(-1, -2)); playbackControls = column(); content.addView(playbackControls); space(playbackControls, 14);
        play = action("Riprendi per tutti", true, () -> RoomsPatch.controller.command(RoomsPatch.PLAYER.sample().paused ? "play" : "pause", 0));
        playbackControls.addView(play, new LinearLayout.LayoutParams(-1, -2)); space(playbackControls, 8); LinearLayout seek = row();
        seekBack = action("−10 secondi", false, () -> RoomsPatch.controller.command("seek", Math.max(0, RoomsPatch.PLAYER.sample().position - 10000)));
        seekForward = action("+10 secondi", false, () -> RoomsPatch.controller.command("seek", RoomsPatch.PLAYER.sample().position + 10000));
        seek.addView(seekBack, new LinearLayout.LayoutParams(0, -2, 1)); gap(seek); seek.addView(seekForward, new LinearLayout.LayoutParams(0, -2, 1)); playbackControls.addView(seek); space(content, 18);
        if (state.host && RoomsPatch.admission != null) buildInvite(); controlHint = text("", 12, muted, false); content.addView(controlHint); space(content, 18);
        members = column(); content.addView(members); space(content, 12);
        if (state.host) {
            TextView reveal = action("Opzioni della room · Mostra", false, () -> {}); content.addView(reveal); options = column(); options.setVisibility(View.GONE); content.addView(options);
            shared = option("Condividi i comandi", "Pausa e spostamenti disponibili a tutti", state.sharedControls);
            wait = option("Aspetta tutti", "Aspetta chi sta caricando prima di riprendere", state.waitForEveryone);
            shared.setOnCheckedChangeListener((button, value) -> RoomsPatch.controller.options(value, wait.isChecked())); wait.setOnCheckedChangeListener((button, value) -> RoomsPatch.controller.options(shared.isChecked(), value));
            reveal.setOnClickListener(v -> { boolean show = options.getVisibility() != View.VISIBLE; options.setVisibility(show ? View.VISIBLE : View.GONE); setText(reveal, "Opzioni della room · " + (show ? "Nascondi" : "Mostra")); }); space(content, 8);
        }
        fullAction(content, "Stato della connessione", false, this::connectionInfo); space(content, 8);
        TextView leave = action(state.host ? "Chiudi la room" : "Lascia la room", false, () -> new AlertDialog.Builder(activity)
                .setTitle(state.host ? "Chiudere la room?" : "Lasciare la room?").setMessage(state.host ? "La sessione terminerà per tutti i partecipanti." : "Il video continuerà fuori dalla room.")
                .setNegativeButton("Resta", null).setPositiveButton(state.host ? "Chiudi" : "Lascia", (d, which) -> RoomsPatch.leave()).show());
        leave.setTextColor(errorColor); content.addView(leave, new LinearLayout.LayoutParams(-1, -2));
    }
    private void buildInvite() {
        String shortCode = RoomsPatch.admission.code(); LinearLayout invite = column(); invite.setPadding(dp(16), dp(14), dp(16), dp(14)); invite.setBackground(shape(card, 16));
        invite.addView(text("Codice della room", 12, muted, true)); space(invite, 8);
        TextView digits = text(shortCode.substring(0, 4) + " " + shortCode.substring(4), 26, ink, true); digits.setTypeface(Typeface.MONOSPACE);
        digits.setTextIsSelectable(true); digits.setContentDescription("Codice della room: " + shortCode); invite.addView(digits); space(invite, 12);
        LinearLayout actions = row(); actions.addView(action("Copia codice", false, () -> Utils.setClipboard(shortCode)), new LinearLayout.LayoutParams(0, -2, 1)); gap(actions);
        actions.addView(action("Invita", true, () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Guarda insieme con Nya Rooms.\nCodice: " + shortCode + "\nApri YouTube → Guarda insieme → Entra nella room.");
            activity.startActivity(Intent.createChooser(share, "Invita nella room"));
        }), new LinearLayout.LayoutParams(0, -2, 1)); invite.addView(actions); content.addView(invite); space(content, 12);
    }
    private void connectionInfo() {
        RoomController.State s = RoomsPatch.controller.state(); String timing = s.host ? "\nQuesta room segue il tuo video." : "\nLatenza stimata: " + s.latency + " ms\nDifferenza dal video dell’host: " + s.drift + " ms";
        new AlertDialog.Builder(activity).setTitle("Connessione della room").setMessage("Connessioni disponibili: " + s.relays + "/3" + timing + "\n\nLa room usa relay Nostr pubblici. Nessun server da gestire; i messaggi sono cifrati e temporanei.").setPositiveButton("OK", null).show();
    }
    private Switch option(String label, String description, boolean checked) {
        LinearLayout line = row(); line.setPadding(0, dp(14), 0, dp(14)); LinearLayout labels = column();
        labels.addView(text(label, 14, ink, true)); space(labels, 4); labels.addView(text(description, 12, muted, false));
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); gap(line); Switch toggle = new Switch(activity); toggle.setChecked(checked); toggle.setContentDescription(label);
        line.addView(toggle); line.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked())); options.addView(line); return toggle;
    }
    private String time(long millis) { long s = Math.max(0, millis) / 1000; return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60); }
}
