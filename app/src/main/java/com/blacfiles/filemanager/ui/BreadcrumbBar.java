package com.blacfiles.filemanager.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom breadcrumb navigation bar.
 *
 * Displays the current directory path as a row of clickable chips.
 * Tapping a chip navigates back to that ancestor directory.
 *
 * Usage:
 *   breadcrumbBar.setPath("/storage/emulated/0/Documents/Work", listener);
 */
public class BreadcrumbBar extends HorizontalScrollView {

    public interface OnCrumbClickListener {
        /** @param path the absolute path of the tapped breadcrumb segment */
        void onCrumbClick(String path);
    }

    private LinearLayout         innerLayout;
    private OnCrumbClickListener listener;

    public BreadcrumbBar(Context context) {
        super(context);
        init(context);
    }

    public BreadcrumbBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BreadcrumbBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        setHorizontalScrollBarEnabled(false);
        innerLayout = new LinearLayout(context);
        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setGravity(Gravity.CENTER_VERTICAL);
        addView(innerLayout);
    }

    public void setOnCrumbClickListener(OnCrumbClickListener l) {
        this.listener = l;
    }

    /**
     * Populates the breadcrumb chips for the given path.
     * @param path absolute path string, e.g. "/storage/emulated/0/Downloads"
     */
    public void setPath(String path) {
        innerLayout.removeAllViews();

        List<String[]> crumbs = buildCrumbs(path);
        for (int i = 0; i < crumbs.size(); i++) {
            final String[] crumb = crumbs.get(i);
            // crumb[0] = label, crumb[1] = full path up to this segment

            Button chip = new Button(getContext());
            chip.setText(crumb[0]);
            chip.setTextSize(13f);
            chip.setAllCaps(false);
            chip.setFocusable(true);

            final String chipPath = crumb[1];
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.onCrumbClick(chipPath);
                }
            });
            innerLayout.addView(chip);

            // Separator '/' between crumbs (not after the last one)
            if (i < crumbs.size() - 1) {
                android.widget.TextView sep = new android.widget.TextView(getContext());
                sep.setText("/");
                sep.setPadding(0, 0, 0, 0);
                sep.setTextColor(0xFF9E9E9E);
                sep.setFocusable(false);
                innerLayout.addView(sep);
            }
        }

        // Auto-scroll to end so the deepest segment is always visible
        post(new Runnable() {
            @Override
            public void run() {
                fullScroll(FOCUS_RIGHT);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Splits a path into [label, fullPath] pairs for each segment.
     * e.g. "/a/b/c" → [["/ (root)", "/"], ["a", "/a"], ["b", "/a/b"], ["c", "/a/b/c"]]
     */
    private static List<String[]> buildCrumbs(String path) {
        List<String[]> result = new ArrayList<String[]>();
        if (path == null || path.length() == 0) return result;

        // Root crumb
        result.add(new String[]{"storage", "/"});

        String[] parts = path.split("/");
        StringBuilder accumulated = new StringBuilder();
        for (String part : parts) {
            if (part.length() == 0) continue; // leading slash produces empty token
            accumulated.append("/").append(part);
            result.add(new String[]{part, accumulated.toString()});
        }
        return result;
    }
}
