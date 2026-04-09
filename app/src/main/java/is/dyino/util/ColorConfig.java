package is.dyino.util;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

public class ColorConfig {
    private static final String TAG   = "ColorConfig";
    private static final String FILE  = "color.json";
    private static final String ASSET = "configs/color.json";

    private JSONObject root;
    private final Context ctx;

    public ColorConfig(Context ctx) { this.ctx = ctx; load(); }

    private void load() {
        try { root = new JSONObject(readRaw()); }
        catch (Exception e) { Log.e(TAG, "load", e); root = new JSONObject(); }
    }

    private int color(String section, String key, String fallback) {
        try {
            JSONObject sec = root.optJSONObject(section);
            String v = sec != null ? sec.optString(key, null) : null;
            return Color.parseColor(v != null && !v.isEmpty() ? v : fallback);
        } catch (Exception e) {
            try { return Color.parseColor(fallback); } catch (Exception ex) { return Color.WHITE; }
        }
    }

    // ── Global ───────────────────────────────────────────────────
    public int bgPrimary()        { return color("global","bg_primary",      "#0D0D14"); }
    public int bgCard()           { return color("global","bg_card",         "#16161F"); }
    public int bgCard2()          { return color("global","bg_card2",        "#1E1E2A"); }
    public int accent()           { return color("global","accent",          "#6C63FF"); }
    public int accentDim()        { return color("global","accent_dim",      "#3D3880"); }
    public int divider()          { return color("global","divider",         "#22223A"); }
    public int textPrimary()      { return color("global","text_primary",    "#FFFFFF"); }
    public int textSecondary()    { return color("global","text_secondary",  "#8888AA"); }
    public int textSectionTitle() { return color("global","text_section_title","#FFFFFF"); }
    public int iconNoteColor()    { return color("global","icon_note_color", "#6C63FF"); }
    /** Tint applied to ic_note_vec vector drawable everywhere */
    public int iconNoteVecTint()  { return color("global","icon_note_vec_tint","#6C63FF"); }

    // ── Nav ──────────────────────────────────────────────────────
    public int bgNav()             { return color("nav","bg",                "#0D0D14"); }
    public int navSelected()       { return color("nav","selected",          "#FFFFFF"); }
    public int navUnselected()     { return color("nav","unselected",        "#44445A"); }
    public int navLabelSelected()  { return color("nav","label_selected",    "#FFFFFF"); }
    public int navLabelUnselected(){ return color("nav","label_unselected",  "#44445A"); }

    // ── Home ─────────────────────────────────────────────────────
    public int homeSectionTitle()     { return color("home","section_title",          "#FFFFFF"); }
    public int homeCardBg()           { return color("home","card_bg",                "#16161F"); }
    public int homeChipPlayBg()       { return color("home","chip_playing_bg",        "#28265A"); }
    public int homeChipBorder()       { return color("home","chip_playing_border",    "#6C63FF"); }
    public int homeChipText()         { return color("home","chip_text",              "#FFFFFF"); }
    public int homeEmptyText()        { return color("home","empty_text",             "#44445A"); }
    public int homePageTitle()        { return color("home","page_title",             "#FFFFFF"); }
    public int nowPlayingAnimColor()  { return color("home","now_playing_anim",       "#6C63FF"); }
    public int nowPlayingCardBg()     { return color("home","now_playing_card_bg",    "#1E1A3A"); }
    public int nowPlayingCardBorder() { return color("home","now_playing_card_border","#6C63FF"); }
    public int nowPlayingIconBg()     { return color("home","now_playing_icon_bg",    "#28265A"); }
    public int nowPlayingIconTint()   { return color("home","now_playing_icon_tint",  "#6C63FF"); }

    // ── Radio ────────────────────────────────────────────────────
    public int stationBg()           { return color("radio","station_bg",              "#1E1E2A"); }
    public int stationActiveBg()     { return color("radio","station_bg_active",       "#2A1E4A"); }
    public int stationActiveBorder() { return color("radio","station_border_active",   "#6C63FF"); }
    public int stationClickGlow()    { return color("radio","station_click_glow",      "#6C63FF"); }
    public int stationText()         { return color("radio","station_text",            "#FFFFFF"); }
    public int stationTextActive()   { return color("radio","station_text_active",     "#6C63FF"); }
    public int eqBar()               { return color("radio","eq_bar",                  "#6C63FF"); }
    public int radioGroupNameText()  { return color("radio","group_name_text",         "#6C63FF"); }
    public int radioGroupCollapsed() { return color("radio","group_name_collapsed_text","#8888AA"); }
    public int radioSearchBg()       { return color("radio","search_bg",               "#1E1E2A"); }
    public int radioSearchText()     { return color("radio","search_text",             "#FFFFFF"); }
    public int radioSearchHint()     { return color("radio","search_hint",             "#8888AA"); }
    public int radioPageTitle()      { return color("radio","page_title",              "#FFFFFF"); }
    public int radioCheckboxColor()  { return color("radio","checkbox_color",          "#6C63FF"); }
    public int textStationActive()   { return stationTextActive(); }
    public int textAboutBrand()      { return accent(); }
    public int textLink()            { return textPrimary(); }

    // ── Sounds ───────────────────────────────────────────────────
    public int soundBtnBg()           { return color("sounds","btn_bg",          "#1A1A26"); }
    public int soundBtnActiveBg()     { return color("sounds","btn_active_bg",   "#28265A"); }
    public int soundBtnActiveBorder() { return color("sounds","btn_border_active","#6C63FF"); }
    public int soundBtnClickGlow()    { return color("sounds","btn_click_glow",  "#6C63FF"); }
    public int soundBtnText()         { return color("sounds","btn_text",        "#FFFFFF"); }
    public int soundWaveColor()       { return color("sounds","wave_color",      "#6C63FF"); }
    public int soundVolBarTrack()     { return color("sounds","vol_bar_track",   "#44FFFFFF"); }
    public int soundVolBarFill()      { return color("sounds","vol_bar_fill",    "#FFFFFF"); }
    public int stopAllBg()            { return color("sounds","stop_all_bg",     "#1E1E2A"); }
    public int stopAllBorder()        { return color("sounds","stop_all_border", "#6C63FF"); }
    public int stopAllText()          { return color("sounds","stop_all_text",   "#FFFFFF"); }
    public int soundsPageTitle()      { return color("sounds","page_title",      "#FFFFFF"); }

    // ── Controls ─────────────────────────────────────────────────
    public int btnStopBg()     { return color("controls","stop_bg",    "#1E1E2A"); }
    public int btnStopBorder() { return color("controls","stop_border","#6C63FF"); }
    public int btnStopText()   { return color("controls","stop_text",  "#FFFFFF"); }

    // ── Settings ─────────────────────────────────────────────────
    public int bgSettingsCard()       { return color("settings","card_bg",        "#16161F"); }
    public int settingsCardBorder()   { return color("settings","card_border",    "#22223A"); }
    public int textSettingsLabel()    { return color("settings","label_text",     "#FFFFFF"); }
    public int textSettingsHeader()   { return color("settings","header_text",    "#8888AA"); }
    public int textSettingsHint()     { return color("settings","hint_text",      "#8888AA"); }
    public int textSettingsVersion()  { return color("settings","version_text",   "#44445A"); }
    public int settingsInputBg()      { return color("settings","input_bg",       "#1E1E2A"); }
    public int settingsInputBorder()  { return color("settings","input_border",   "#22223A"); }
    public int settingsInputText()    { return color("settings","input_text",     "#FFFFFF"); }
    public int settingsBtnBg()        { return color("settings","btn_bg",         "#1E1E2A"); }
    public int settingsBtnBorder()    { return color("settings","btn_border",     "#6C63FF"); }
    public int settingsBtnText()      { return color("settings","btn_text",       "#FFFFFF"); }
    public int settingsAboutText()    { return color("settings","about_text",     "#6C63FF"); }
    public int settingsDivider()      { return color("settings","divider",        "#22223A"); }
    public int settingsPageTitle()    { return color("settings","page_title",     "#FFFFFF"); }
    public int settingsSwitchThumbOn()  { return color("settings","switch_thumb_on",  "#6C63FF"); }
    public int settingsSwitchTrackOn()  { return color("settings","switch_track_on",  "#3D3880"); }
    public int settingsSwitchThumbOff() { return color("settings","switch_thumb_off", "#8888AA"); }
    public int settingsSwitchTrackOff() { return color("settings","switch_track_off", "#2A2A3A"); }
    public int settingsSwitchThumb()    { return settingsSwitchThumbOn(); }
    public int settingsSwitchTrack()    { return settingsSwitchTrackOn(); }
    public int settingsMadeByText()     { return color("settings","made_by_text",  "#44445A"); }
    public int settingsMadeByBrand()    { return color("settings","made_by_brand", "#6C63FF"); }
    public int tuneIconColor()          { return color("settings","tune_icon_color","#6C63FF"); }

    // ── About ─────────────────────────────────────────────────────
    public int aboutBg()     { return color("about","bg",     "#0D0D14"); }
    public int aboutTitle()  { return color("about","title",  "#FFFFFF"); }
    public int aboutText()   { return color("about","text",   "#CCCCCC"); }
    public int aboutAccent() { return color("about","accent", "#6C63FF"); }

    // ── Notification ─────────────────────────────────────────────
    public int notifBg()     { return color("notification","bg",      "#1A1A26"); }
    public int notifIconBg() { return color("notification","icon_bg", "#6C63FF"); }

    // ── Persist ──────────────────────────────────────────────────
    public void saveRaw(String raw) {
        try {
            new JSONObject(raw);
            FileWriter fw = new FileWriter(new File(ctx.getFilesDir(), FILE));
            fw.write(raw); fw.close();
        } catch (Exception e) { Log.e(TAG, "saveRaw", e); }
    }

    public String readRaw() {
        File f = new File(ctx.getFilesDir(), FILE);
        try {
            BufferedReader br = f.exists()
                ? new BufferedReader(new FileReader(f))
                : new BufferedReader(new InputStreamReader(ctx.getAssets().open(ASSET)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) { Log.e(TAG, "readRaw", e); return "{}"; }
    }
}
