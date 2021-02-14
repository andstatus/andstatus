package org.andstatus.app.context;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

public class MultilineListPreference extends ListPreference {

    public MultilineListPreference(Context context) {
        super(context);
    }

    public MultilineListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder viewHolder) {
        super.onBindViewHolder(viewHolder);
        TextView textView = (TextView) viewHolder.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setSingleLine(false);
        }
    }

}
