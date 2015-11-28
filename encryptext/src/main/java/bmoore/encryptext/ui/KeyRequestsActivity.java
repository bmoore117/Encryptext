package bmoore.encryptext.ui;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.R;
import bmoore.encryptext.model.KeyRequest;
import bmoore.encryptext.model.KeyRequestAdapter;
import bmoore.encryptext.services.SenderSvc;
import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.utils.InvalidKeyTypeException;


public class KeyRequestsActivity extends ListActivity {

    private DBUtils dbUtils;
    private EncrypText app;
    private KeyRequestAdapter adapter;
    private SenderSvc senderSvc;

    private static final String TAG = "KeyRequestsActivity";

    private ServiceConnection senderConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service)
        {
            senderSvc = ((SenderSvc.SenderBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className)
        {
            senderSvc = null;
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.key_exhange_requests);

        app = ((EncrypText) getApplication());
        dbUtils = app.getDbUtils();

        adapter = new KeyRequestAdapter(this, R.layout.key_exchange_request_listitem,
                new ArrayList<KeyRequest>());

        new LoadKeyRequestsTask().execute();

        if(adapter.getCount() == 0)
            adapter.add(new KeyRequest("No keys exchanged. Start a conversation from the home screen", null, null, null));

        ListView list = (ListView) findViewById(android.R.id.list); //how you reference that pesky bitch

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, View view, final int position, long id) {
                final KeyRequest item = (KeyRequest) parent.getAdapter().getItem(position);

                AlertDialog.Builder builder = new AlertDialog.Builder(KeyRequestsActivity.this);
                builder.setTitle("Accept " + item.getName() + "'s request?");
                builder.setMessage("Select Yes to generate a secret key from their public key and reply with your public key. Select No to delete their request");

                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                        try {
                            Cryptor cryptor = app.getCryptor();
                            senderSvc.sendKey(cryptor.getMyPublicKey(), item.getNumber());
                            cryptor.createAndStoreSecretKey(item.getNumber());

                            adapter.remove(adapter.getData().get(position));
                            dbUtils.deleteKeyRequestEntry(item.getNumber());
                        } catch (InvalidKeyTypeException | InvalidKeyException e) {
                            Log.e(TAG, "Error creating secret key", e);
                            Toast.makeText(KeyRequestsActivity.this, "Error creating secret key", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int buttonClicked) {
                        dbUtils.deleteKeyRequestEntry(item.getNumber());
                        adapter.remove(adapter.getData().get(position));
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        setListAdapter(adapter);
    }

    @Override
    public void onDestroy()
    {
        unbindService(senderConnection);
        super.onDestroy();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        if(senderSvc == null)
        {
            Intent intent = new Intent(this, SenderSvc.class);
            bindService(intent, senderConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private class LoadKeyRequestsTask extends AsyncTask<Void, Void, List<KeyRequest>> {

        @Override
        protected List<KeyRequest> doInBackground(Void... params) {
            return dbUtils.loadKeyRequests();
        }

        @Override
        protected void onPostExecute(List<KeyRequest> requests) {
            adapter.addAll(requests);
        }
    }
}
