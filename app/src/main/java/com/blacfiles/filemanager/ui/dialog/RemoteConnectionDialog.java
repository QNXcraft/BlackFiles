package com.blacfiles.filemanager.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.blacfiles.filemanager.R;
import com.blacfiles.filemanager.model.RemoteConnection;
import com.blacfiles.filemanager.prefs.AppPreferences;

import java.util.UUID;

/**
 * DialogFragment for creating a new remote connection.
 *
 * On successful validation, stores the connection via {@link AppPreferences}
 * and notifies the host activity via {@link OnConnectionSavedListener}.
 */
public class RemoteConnectionDialog extends DialogFragment {

    public interface OnConnectionSavedListener {
        void onConnectionSaved(RemoteConnection connection);
    }

    private static final String[] PROTOCOL_LABELS =
            { "SFTP", "SCP", "FTP", "FTPS", "WebDAV", "SMB" };
    private static final RemoteConnection.Protocol[] PROTOCOL_VALUES = {
            RemoteConnection.Protocol.SFTP,
            RemoteConnection.Protocol.SCP,
            RemoteConnection.Protocol.FTP,
            RemoteConnection.Protocol.FTPS,
            RemoteConnection.Protocol.WEBDAV,
            RemoteConnection.Protocol.SMB
    };

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_remote_connection, null);

        final Spinner   spinnerProtocol = (Spinner)   view.findViewById(R.id.spinner_protocol);
        final EditText  etDisplayName   = (EditText)  view.findViewById(R.id.et_display_name);
        final EditText  etHost          = (EditText)  view.findViewById(R.id.et_host);
        final EditText  etPort          = (EditText)  view.findViewById(R.id.et_port);
        final EditText  etUsername      = (EditText)  view.findViewById(R.id.et_username);
        final EditText  etPassword      = (EditText)  view.findViewById(R.id.et_password);
        final EditText  etPrivateKey    = (EditText)  view.findViewById(R.id.et_private_key);
        final EditText  etInitialPath   = (EditText)  view.findViewById(R.id.et_initial_path);

        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<String>(
                getActivity(),
                android.R.layout.simple_spinner_item,
                PROTOCOL_LABELS
        );
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProtocol.setAdapter(protocolAdapter);

        // Auto-fill port when protocol changes
        spinnerProtocol.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int pos, long id) {
                RemoteConnection.Protocol proto = PROTOCOL_VALUES[pos];
                etPort.setText(String.valueOf(RemoteConnection.defaultPort(proto)));
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Add Remote Connection");
        builder.setView(view);
        builder.setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Validation
                String host = etHost.getText().toString().trim();
                String portStr = etPort.getText().toString().trim();
                if (host.isEmpty()) {
                    Toast.makeText(getActivity(), "Host is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getActivity(), "Invalid port", Toast.LENGTH_SHORT).show();
                    return;
                }

                RemoteConnection.Protocol proto =
                        PROTOCOL_VALUES[spinnerProtocol.getSelectedItemPosition()];
                String displayName = etDisplayName.getText().toString().trim();
                if (displayName.isEmpty()) displayName = host;

                String path = etInitialPath.getText().toString().trim();
                if (path.isEmpty()) path = "/";

                RemoteConnection conn = new RemoteConnection(
                        UUID.randomUUID().toString(),
                        displayName,
                        proto,
                        host,
                        port,
                        etUsername.getText().toString().trim(),
                        etPassword.getText().toString(),
                        path,
                        etPrivateKey.getText().toString().trim()
                );

                AppPreferences prefs = new AppPreferences(getActivity());
                prefs.addConnection(conn);

                if (getActivity() instanceof OnConnectionSavedListener) {
                    ((OnConnectionSavedListener) getActivity()).onConnectionSaved(conn);
                }
            }
        });
        builder.setNegativeButton(R.string.action_cancel, null);
        return builder.create();
    }
}
