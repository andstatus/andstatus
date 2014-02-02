package org.andstatus.app;

import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MyActionBar {
    private MyActionBarContainer container;

    public MyActionBar(MyActionBarContainer container) {
        this.container = container;
        if (android.os.Build.VERSION.SDK_INT >= 16 ) {
            container.getActivity().requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
    }

    public void attach() {
        LayoutInflater inflater = LayoutInflater.from(container.getActivity());
        ViewGroup actionsView = (ViewGroup) inflater.inflate(R.layout.empty_actions, null);
        ViewGroup viewGroup = findChildViewGroup(LinearLayout.class, (ViewGroup) container.getActivity().findViewById(android.R.id.content), 2);
        if (LinearLayout.class.isAssignableFrom(viewGroup.getClass())) {
            viewGroup.addView(actionsView, 0);
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 16 ) {
                attachToListView(actionsView);
            }
        }
        attachUpNavigationListener();
    }

    /**
     * @return Tries to return a child of the viewClassToFind but may return any ViewGroup if not found
     */
    private ViewGroup findChildViewGroup(Class<? extends ViewGroup> viewClassToFind, ViewGroup parentViewGroup, int minLevelsToCheck) {
        ViewGroup viewFound = parentViewGroup;
        for (int index = 0; index < parentViewGroup.getChildCount(); index++) {
            View view = parentViewGroup.getChildAt(index);
            if (viewClassToFind.isAssignableFrom(view.getClass())) {
                viewFound = (ViewGroup) view;
                break;
            }
        }
        if ((minLevelsToCheck > 0) || !(viewClassToFind.isAssignableFrom(viewFound.getClass()))) {
            for (int index = 0; index < parentViewGroup.getChildCount(); index++) {
                View view = parentViewGroup.getChildAt(index);
                if (ViewGroup.class.isAssignableFrom(view.getClass())) {
                    ViewGroup viewGroup = findChildViewGroup(viewClassToFind, (ViewGroup) view, minLevelsToCheck-1);
                    if (viewClassToFind.isAssignableFrom(viewGroup.getClass())) {
                        viewFound = viewGroup;
                        break;
                    }
                }
            }
        }
        return viewFound;
    }

    private void attachToListView(ViewGroup actionsView) {
        ViewGroup viewGroup = findChildViewGroup(ListView.class, (ViewGroup) container.getActivity().findViewById(android.R.id.content), 2);
        if (ListView.class.isAssignableFrom(viewGroup.getClass())) {
            if (PreferenceActivity.class.isAssignableFrom(container.getActivity().getClass())) {
                // Ugly but better than nothing
                ((ListView) viewGroup).addFooterView(actionsView);
            } else {
                ((ListView) viewGroup).addHeaderView(actionsView);
            }
        } else {
            viewGroup.addView(actionsView, 0);
        }
    }

    private void attachUpNavigationListener() {
        OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                container.closeAndGoBack();
            }
        };
        View view = container.getActivity().findViewById(R.id.upNavigation);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }
    
    public void setTitle(int resId) {
        setTitle(container.getActivity().getText(resId).toString());
    }

    public void setTitle(String titleText) {
        TextView titleView = (TextView) container.getActivity().findViewById(R.id.titleText);
        if (titleView != null) {
            titleView.setText(titleText);
        } else {
            String appName = container.getActivity().getText(R.string.app_name).toString();
            container.getActivity().setTitle(appName + ": " + titleText);
        }
    }
}
