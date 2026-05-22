package com.blacfiles.filemanager.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.blacfiles.filemanager.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the navigation drawer ListView.
 *
 * Items are divided into three sections:
 *   SECTION_HEADER — non-clickable divider label (Favorites / Local / Network)
 *   ENTRY          — clickable row with icon + label
 *
 * The adapter returns false for {@link #isEnabled(int)} on header rows to
 * prevent them from receiving click events or focus.
 */
public class DrawerAdapter extends BaseAdapter {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ENTRY  = 1;

    public static class DrawerItem {
        public final int    viewType;
        public final String label;
        public final int    iconRes;  // 0 for headers
        public final String payload;  // path or connection ID

        /** Header constructor */
        public DrawerItem(String label) {
            this.viewType = VIEW_TYPE_HEADER;
            this.label    = label;
            this.iconRes  = 0;
            this.payload  = null;
        }

        /** Entry constructor */
        public DrawerItem(String label, int iconRes, String payload) {
            this.viewType = VIEW_TYPE_ENTRY;
            this.label    = label;
            this.iconRes  = iconRes;
            this.payload  = payload;
        }
    }

    private final Context        context;
    private final LayoutInflater inflater;
    private List<DrawerItem>     items = new ArrayList<DrawerItem>();

    public DrawerAdapter(Context context) {
        this.context  = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<DrawerItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<DrawerItem>();
        notifyDataSetChanged();
    }

    // ── BaseAdapter ───────────────────────────────────────────────────────────

    @Override public int     getCount()             { return items.size(); }
    @Override public DrawerItem getItem(int pos)    { return items.get(pos); }
    @Override public long   getItemId(int pos)      { return pos; }
    @Override public int    getViewTypeCount()      { return 2; }
    @Override public int    getItemViewType(int pos){ return items.get(pos).viewType; }

    @Override
    public boolean isEnabled(int position) {
        return items.get(position).viewType == VIEW_TYPE_ENTRY;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DrawerItem item = items.get(position);

        if (item.viewType == VIEW_TYPE_HEADER) {
            if (convertView == null || convertView.getTag() == null) {
                convertView = inflater.inflate(R.layout.item_drawer_section_header, parent, false);
                convertView.setTag("header");
            }
            ((TextView) convertView).setText(item.label);
        } else {
            EntryHolder holder;
            if (convertView == null || !(convertView.getTag() instanceof EntryHolder)) {
                convertView = inflater.inflate(R.layout.item_drawer_entry, parent, false);
                holder = new EntryHolder();
                holder.icon  = (ImageView) convertView.findViewById(R.id.drawer_icon);
                holder.label = (TextView)  convertView.findViewById(R.id.drawer_label);
                convertView.setTag(holder);
            } else {
                holder = (EntryHolder) convertView.getTag();
            }
            holder.label.setText(item.label);
            if (item.iconRes != 0) {
                holder.icon.setImageResource(item.iconRes);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.INVISIBLE);
            }
        }
        return convertView;
    }

    private static class EntryHolder {
        ImageView icon;
        TextView  label;
    }
}
