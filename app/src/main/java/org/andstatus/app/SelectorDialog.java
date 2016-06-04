/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.andstatus.app.widget.MySimpleAdapter;
import org.andstatus.app.context.MyTheme;

/**
 * @author yvolk@yurivolkov.com
 */
public class SelectorDialog extends DialogFragment {
    public final static String dialogTag = SelectorDialog.class.getSimpleName();
    Toolbar toolbar = null;
    ListView listView = null;
    private int mLayoutId = R.layout.my_list_dialog;

    public Bundle setRequestCode(ActivityRequestCode requestCode) {
        Bundle args = new Bundle();
        args.putInt(IntentExtra.REQUEST_CODE.key, requestCode.id);
        setArguments(args);
        return args;
    }

    public static String getDialogTag() {
        return dialogTag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), MyTheme.getThemeId(getActivity(), MyTheme.getThemeName()));
        MyTheme.applyStyles(dialog.getContext(), true);
        return dialog;
    }

    public ListView getListView() {
        return listView;
    }

    protected void setListAdapter(MySimpleAdapter adapter) {
        if (listView != null) {
            listView.setAdapter(adapter);
        }
    }

    public MySimpleAdapter getListAdapter() {
        if (listView != null) {
             return (MySimpleAdapter) listView.getAdapter();
        }
        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(mLayoutId, container, false);
        toolbar = (Toolbar) view.findViewById(R.id.my_action_bar);
        listView = (ListView) view.findViewById(android.R.id.list);
        return view;
    }

    public void setTitle(@StringRes int resId) {
        if (toolbar != null) {
            toolbar.setTitle(resId);
        }
    }

    public void setTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    protected void returnSelected(Intent selectedData) {
        dismiss();
        ((MyActivity) getActivity()).onActivityResult(
                getArguments().getInt(IntentExtra.REQUEST_CODE.key),
                Activity.RESULT_OK,
                selectedData
        );
    }

    public void show(FragmentActivity fragmentActivity) {
        FragmentTransaction ft = fragmentActivity.getSupportFragmentManager().beginTransaction();
        Fragment prev = fragmentActivity.getSupportFragmentManager().findFragmentByTag(dialogTag);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        show(ft, dialogTag);
    }
}
