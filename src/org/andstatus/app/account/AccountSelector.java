/**
 * 
 */
package org.andstatus.app.account;

import android.app.ListActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.data.MyPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The purpose of this activity is to select Current account only, see {@link MyAccount#setCurrentMyAccount()}
 * @author yvolk
 *
 */
public class AccountSelector extends ListActivity {
    private static final String TAG = AccountSelector.class.getSimpleName();

    private final String KEY_NAME = "name";
    private final String KEY_TYPE = "type";
    
    private final String TYPE_ACCOUNT = "account";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyPreferences.loadTheme(TAG, this);
        
        setContentView(R.layout.accountlist);
        
        // Fill the list of accounts
        ArrayList<Map<String, String>> data = new ArrayList<Map<String, String>>();
        for (int ind = 0; ind < MyAccount.list().length; ind++) {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put(KEY_NAME, MyAccount.list()[ind].getAccountGuid());
            map.put(KEY_TYPE, TYPE_ACCOUNT);
            data.add(map);
        }
        
        ListAdapter adapter = new SimpleAdapter(this, 
                data, 
                R.layout.accountlist_item, 
                new String[] {KEY_NAME, KEY_TYPE}, 
                new int[] {R.id.name, R.id.type});
        
        // Bind to our new adapter.
        setListAdapter(adapter);

        getListView().setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String  accountName = ((TextView)view.findViewById(R.id.name)).getText().toString();
                MyAccount ma = MyAccount.getMyAccount(accountName);
                if (ma.isPersistent()) {
                    ma.setCurrentMyAccount();
                    AccountSelector.this.setResult(RESULT_OK);
                    finish();
                }
            }
        });

        
    }

}
