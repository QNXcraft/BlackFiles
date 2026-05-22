package com.blacfiles.filemanager.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.blacfiles.filemanager.R;

/**
 * Simple rename dialog — one EditText pre-filled with the current name.
 */
public class RenameDialog extends DialogFragment {

    public interface OnRenameListener {
        void onRenamed(String newName);
    }

    private static final String ARG_CURRENT_NAME = "current_name";

    public static RenameDialog newInstance(String currentName) {
        RenameDialog d = new RenameDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT_NAME, currentName);
        d.setArguments(args);
        return d;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String currentName = getArguments().getString(ARG_CURRENT_NAME, "");

        final EditText input = new EditText(getActivity());
        input.setText(currentName);
        input.selectAll();
        input.setSingleLine(true);
        input.setFocusable(true);
        int pad = (int) (16 * getActivity().getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_rename_title);
        builder.setView(input);
        builder.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (newName.length() > 0 && getActivity() instanceof OnRenameListener) {
                    ((OnRenameListener) getActivity()).onRenamed(newName);
                }
            }
        });
        builder.setNegativeButton(R.string.action_cancel, null);
        return builder.create();
    }
}
