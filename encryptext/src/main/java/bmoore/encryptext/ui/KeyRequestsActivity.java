package bmoore.encryptext.ui;

import android.app.ListActivity;
import android.os.Bundle;

import java.util.ArrayList;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.model.KeyRequest;
import bmoore.encryptext.model.KeyRequestAdapter;
import bmoore.encryptext.utils.DBUtils;


public class KeyRequestsActivity extends ListActivity {

    private KeyRequestAdapter adapter;
    private DBUtils dbUtils;
    private EncrypText app;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.key_exhange_requests);

        app = ((EncrypText)getApplication());
        dbUtils = app.getDbUtils();

        adapter = new KeyRequestAdapter(this, R.layout.key_exchange_request_listitem,
                new ArrayList<KeyRequest>());

        adapter.addAll(dbUtils.loadKeyRequests());

        if(adapter.getCount() == 0)
            adapter.add(new KeyRequest("No keys exchanged. Start a conversation from the home screen", null, null, null));

        setListAdapter(adapter);
    }

}
