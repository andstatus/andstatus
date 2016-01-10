/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.widget;

import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.andstatus.app.R;

public abstract class MyBaseAdapter extends BaseAdapter {

    private boolean positionRestored = false;

    public Object getItem(View view) {
        return getItem(getPosition(view));
    }

    public int getPosition(View view) {
        TextView positionView = getPositionView(view);
        if (positionView == null) {
            return -1;
        }
        return Integer.parseInt(positionView.getText().toString());
    }

    private TextView getPositionView(View view) {
        return (TextView) view.findViewById(R.id.position);
    }

    protected void setPosition(View view, int position) {
        TextView positionView = getPositionView(view);
        if (positionView != null) {
            positionView.setText(Integer.toString(position));
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

    public void setPositionRestored(boolean positionRestored) {
        this.positionRestored = positionRestored;
    }

    public boolean isPositionRestored() {
        return positionRestored;
    }
}