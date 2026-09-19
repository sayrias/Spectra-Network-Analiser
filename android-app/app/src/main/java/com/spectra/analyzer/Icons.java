package com.spectra.analyzer;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

final class Icons {
    static final String HOME="\uf015", SPECTRUM="\uf83e", LINK="\uf0c1", RADAR="\uf140",
            SETTINGS="\uf1de", WIFI="\uf1eb", RADIO="\uf519", CLOSE="\uf00d",
            PLAY="\uf04b", SOUND="\uf028", UPLOAD="\uf093", DOWNLOAD="\uf019",
            SHIELD="\uf3ed", SIGNAL="\uf012", REFRESH="\uf2f1", PAUSE="\uf04c",
            INFO="\uf05a", EXPAND="\uf065", COMPRESS="\uf066";
    private static Typeface font;
    static void apply(TextView view, String glyph, int size) {
        if (font == null) font = Typeface.createFromAsset(view.getContext().getAssets(), "fonts/fa-solid-900.ttf");
        view.setTypeface(font); view.setText(glyph); view.setTextSize(size);
    }
}
