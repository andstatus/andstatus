package org.andstatus.app.view;

import android.content.Context;
import android.provider.BaseColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;

import org.andstatus.app.context.MyPreferences;

import java.util.List;
import java.util.Map;

public class MySimpleAdapter extends SimpleAdapter implements View.OnClickListener {
    final boolean hasActionOnClick;

    public MySimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
            String[] from, int[] to, boolean hasActionOnClick) {
        super(context, data, resource, from, to);
        this.hasActionOnClick = hasActionOnClick;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (!hasActionOnClick) {
            view.setOnClickListener(this);
        }
        return view;
    }

    @Override
    public long getItemId(int position) {
        @SuppressWarnings("unchecked")
        Map<String, ?> item = (Map<String, ?>) getItem(position);
        try {
            String id = (String) item.get(BaseColumns._ID);
            return id == null ? 0 : Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(e.getMessage() +  " caused by wrong item: " + item);
        }
    }

    public int getPositionById(long itemId) {
        if (itemId != 0) {
            for (int position = 0; position < getCount(); position++) {
                if (getItemId(position) == itemId) {
                    return position;
                }
            }
        }
        return -1;
    }

    @Override
    public void onClick(View v) {
        if (!MyPreferences.isLongPressToOpenContextMenu() && v.getParent() != null) {
            v.showContextMenu();
        }
    }

}
