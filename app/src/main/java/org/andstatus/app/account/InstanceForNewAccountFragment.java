/* 
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.NonUiThreadExecutor;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

import io.vavr.control.Try;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class InstanceForNewAccountFragment extends Fragment {
    private OriginType originType = OriginType.UNKNOWN;
    private Origin origin = Origin.EMPTY;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.instance_for_new_account, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        final AccountSettingsActivity activity = (AccountSettingsActivity) getActivity();
        if (activity != null) {
            origin = activity.getState().getAccount().getOrigin();
            originType = origin.nonEmpty()
                    ? origin.getOriginType()
                    : OriginType.fromCode(getArguments().getString(IntentExtra.ORIGIN_TYPE.key));
            prepareScreen(activity);
            activity.updateScreen();
            if (origin.isPersistent()) {
                activity.verifyCredentials(true);
            }
        }
        super.onActivityCreated(savedInstanceState);
    }

    private void prepareScreen(AccountSettingsActivity activity) {
        TextView instanceTextView = (TextView) activity.findFragmentViewById(R.id.originInstance);
        if (instanceTextView != null) {
            String hint = getText(R.string.hint_which_instance).toString() +  "\n\n"
                + getText(originType == OriginType.MASTODON
                    ? R.string.host_hint_mastodon
                    : R.string.host_hint);
            instanceTextView.setHint(hint);
            if (origin.nonEmpty()) {
                instanceTextView.setText(origin.getHost());
            }
        }

        String buttonText = String.format(getText(R.string.log_in_to_social_network).toString(),
            originType.title(activity));
        TextView buttonTextView = activity.showTextView(R.id.log_in_to_social_network, buttonText, true);
        if (buttonTextView != null) {
            buttonTextView.setOnClickListener(this::onLogInClick);
        }
    }

    private void onLogInClick(View view) {
        final AccountSettingsActivity activity = (AccountSettingsActivity) getActivity();
        if (activity == null) return;;

        activity.clearError();
        getNewOrExistingOrigin(activity)
        .onFailure(e -> {
            activity.appendError(e.getMessage());
            activity.showErrors();
        })
        .onSuccess(originNew -> onNewOrigin(activity, originNew));
    }

    private Try<Origin> getNewOrExistingOrigin(AccountSettingsActivity activity) {
        TextView instanceTextView = (TextView) activity.findFragmentViewById(R.id.originInstance);
        if (instanceTextView == null) return TryUtils.failure("No text view ???");

        String hostOrUrl = instanceTextView.getText().toString();
        URL url1 = UrlUtils.buildUrl(hostOrUrl, true);
        if (url1 == null) {
            return TryUtils.failure(getText(R.string.error_invalid_value) + ": '" + hostOrUrl + "'");
        }
        String host = url1.getHost();
        for (Origin existing: myContextHolder.getBlocking().origins().originsOfType(originType)) {
            if (host.equals(existing.getHost())) {
                return Try.success(existing);
            }
        }
        Origin origin = new Origin.Builder(myContextHolder.getBlocking(), originType)
                .setHostOrUrl(hostOrUrl)
                .setName(host)
                .save()
                .build();
        myContextHolder.setExpired(false);
        return origin.isPersistent()
                ? Try.success(origin)
                : TryUtils.failure(getText(R.string.error_invalid_value) + ": " + origin);
    }

    private void onNewOrigin(AccountSettingsActivity activity, Origin originNew) {
        if (originNew.equals(origin) || myContextHolder.getNow().isReady()) {
            if (!activity.getState().getAccount().getOrigin().equals(originNew)) {
                activity.getState().builder.setOrigin(originNew);
            }
            activity.verifyCredentials(true);
        } else {
            myContextHolder.initialize(activity)
            .whenSuccessAsync(future -> AccountSettingsActivity
                            .startAddNewAccount(future.context(), originNew.getName(), true),
                    NonUiThreadExecutor.INSTANCE);
        }
    }

}
