package com.blacfiles.filemanager.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.blacfiles.filemanager.R;
import com.blacfiles.filemanager.model.FileItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter for the main file list.
 *
 * Multi-selection state is managed here.  The containing Fragment / Activity
 * reads {@link #getSelectedItems()} after the user triggers an operation.
 *
 * Selection mode is activated externally via {@link #setSelectionMode(boolean)}.
 * In selection mode the checkbox column becomes visible.
 */
public class FileListAdapter extends BaseAdapter {

    private final Context        context;
    private final LayoutInflater inflater;
    private List<FileItem>       items     = new ArrayList<FileItem>();
    private Set<Integer>         selected  = new HashSet<Integer>();
    private boolean              selectionMode = false;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());

    public FileListAdapter(Context context) {
        this.context  = context;
        this.inflater = LayoutInflater.from(context);
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    public void setItems(List<FileItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<FileItem>();
        selected.clear();
        notifyDataSetChanged();
    }

    @Override public int  getCount()             { return items.size(); }
    @Override public FileItem getItem(int pos)   { return items.get(pos); }
    @Override public long getItemId(int pos)     { return pos; }

    // ── Selection ─────────────────────────────────────────────────────────────

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        if (!on) selected.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void toggleSelection(int position) {
        if (selected.contains(position)) {
            selected.remove(position);
        } else {
            selected.add(position);
        }
        notifyDataSetChanged();
    }

    /** Selects all items in the current list. */
    public void selectAll() {
        selected.clear();
        for (int i = 0; i < items.size(); i++) selected.add(i);
        notifyDataSetChanged();
    }

    /** Deselects all items. */
    public void deselectAll() {
        selected.clear();
        notifyDataSetChanged();
    }

    public boolean isSelected(int position) {
        return selected.contains(position);
    }

    public int getSelectedCount() { return selected.size(); }

    public List<FileItem> getSelectedItems() {
        List<FileItem> result = new ArrayList<FileItem>();
        for (int idx : selected) {
            if (idx < items.size()) result.add(items.get(idx));
        }
        return result;
    }

    // ── View recycling ────────────────────────────────────────────────────────

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_file, parent, false);
            holder = new ViewHolder();
            holder.icon     = (ImageView) convertView.findViewById(R.id.file_icon);
            holder.name     = (TextView)  convertView.findViewById(R.id.file_name);
            holder.meta     = (TextView)  convertView.findViewById(R.id.file_meta);
            holder.checkbox = (CheckBox)  convertView.findViewById(R.id.file_checkbox);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        FileItem item = items.get(position);

        holder.name.setText(item.getName());
        holder.meta.setText(buildMeta(item));

        // Icon: directory vs file
        holder.icon.setImageResource(item.isDirectory()
                ? R.drawable.ic_folder
                : iconForExtension(item.getExtension()));

        // Dim hidden files
        float alpha = item.isHidden() ? 0.5f : 1.0f;
        holder.name.setAlpha(alpha);
        holder.meta.setAlpha(alpha);
        holder.icon.setAlpha(alpha);

        // Multi-selection
        if (selectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            holder.checkbox.setChecked(selected.contains(position));
        } else {
            holder.checkbox.setVisibility(View.GONE);
        }

        // Keyboard focus — explicit state so the selector drawable fires
        convertView.setFocusable(true);
        convertView.setFocusableInTouchMode(false);

        return convertView;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildMeta(FileItem item) {
        if (item.isDirectory()) {
            return "Folder · " + DATE_FMT.format(new Date(item.getLastModified()));
        }
        return formatSize(item.getSize()) + " · "
                + DATE_FMT.format(new Date(item.getLastModified()));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1048576)    return (bytes / 1024) + " KB";
        if (bytes < 1073741824) return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.getDefault(), "%.2f GB", bytes / 1073741824.0);
    }

    private static int iconForExtension(String ext) {
        if ("pdf".equals(ext))                               return R.drawable.ic_file_pdf;
        if ("jpg".equals(ext) || "jpeg".equals(ext)
                || "png".equals(ext) || "gif".equals(ext))  return R.drawable.ic_file_image;
        if ("mp4".equals(ext) || "mkv".equals(ext)
                || "avi".equals(ext) || "mov".equals(ext))  return R.drawable.ic_file_video;
        if ("mp3".equals(ext) || "ogg".equals(ext)
                || "flac".equals(ext) || "wav".equals(ext)) return R.drawable.ic_file_audio;
        if ("zip".equals(ext) || "tar".equals(ext)
                || "gz".equals(ext) || "rar".equals(ext))   return R.drawable.ic_file_archive;
        if ("txt".equals(ext) || "md".equals(ext)
                || "log".equals(ext))                       return R.drawable.ic_file_text;
        return R.drawable.ic_file;
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    private static class ViewHolder {
        ImageView icon;
        TextView  name;
        TextView  meta;
        CheckBox  checkbox;
    }
}
