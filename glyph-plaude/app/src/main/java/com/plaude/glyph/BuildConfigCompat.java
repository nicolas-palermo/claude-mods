package com.plaude.glyph;

import android.content.Context;

/** La versión instalada, sin depender de BuildConfig. */
final class BuildConfigCompat {

    private BuildConfigCompat() {
    }

    static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
