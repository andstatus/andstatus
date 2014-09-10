package org.andstatus.app.account;

import android.content.Context;
import android.provider.BaseColumns;
import android.widget.SimpleAdapter;

import java.util.List;
import java.util.Map;

public class MySimpleAdapter extends SimpleAdapter {

    public MySimpleAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
            String[] from, int[] to) {
        super(context, data, resource, from, to);
    }

    @Override
    public long getItemId(int position) {
        Map<String, ?> item = (Map<String, ?>) getItem(position);
        return Long.parseLong( (String) item.get(BaseColumns._ID));
    }

}
