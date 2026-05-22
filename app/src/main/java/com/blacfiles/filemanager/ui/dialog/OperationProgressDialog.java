package com.blacfiles.filemanager.ui.dialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.blacfiles.filemanager.R;
import com.blacfiles.filemanager.ops.FileOperation;

/**
 * Progress dialog that binds to a {@link FileOperation.ProgressCallback}.
 *
 * Shows:
 *  - Indeterminate spinner while total is 0
 *  - Determinate ProgressBar once total bytes are known
 *  - Item name being processed
 *  - Dismissed automatically on complete/error
 */
public class OperationProgressDialog extends DialogFragment
        implements FileOperation.ProgressCallback {

    private ProgressBar progressBar;
    private TextView    labelText;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        android.app.AlertDialog.Builder b =
                new android.app.AlertDialog.Builder(getActivity());

        View v = LayoutInflater.from(getActivity())
                .inflate(android.R.layout.activity_list_item, null);

        // Build a simple inline layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(getActivity());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getActivity().getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        labelText   = new TextView(getActivity());
        labelText.setText(R.string.loading);
        layout.addView(labelText);

        progressBar = new ProgressBar(getActivity(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        progressBar.setLayoutParams(lp);
        layout.addView(progressBar);

        b.setView(layout);
        b.setCancelable(false);
        Dialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        return d;
    }

    // ── ProgressCallback (called on main thread by FileOperationQueue) ────────

    @Override
    public void onProgress(long current, long total, String itemName) {
        if (labelText != null)   labelText.setText(itemName);
        if (progressBar != null) {
            if (total > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress((int) (current * 100 / total));
            } else {
                progressBar.setIndeterminate(true);
            }
        }
    }

    @Override
    public void onComplete() {
        dismissAllowingStateLoss();
    }

    @Override
    public void onError(String message) {
        dismissAllowingStateLoss();
        if (getActivity() != null) {
            android.widget.Toast.makeText(getActivity(), message,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
