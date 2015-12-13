package bmoore.encryptext.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.util.LinkedList;
import java.util.TreeMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import bmoore.encryptext.EncrypText;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.model.MessageConfirmation;
import bmoore.encryptext.ui.ConversationActivity;
import bmoore.encryptext.ui.HomeActivity;
import bmoore.encryptext.utils.Cryptor;
import bmoore.encryptext.utils.DBUtils;
import bmoore.encryptext.utils.DateUtils;
import bmoore.encryptext.utils.InvalidKeyTypeException;


public class SenderSvc extends Service {


    private static final String SENT_INTENT = "com.encryptext.PDU_SENT";
    public static final short APPLICATION_PORT = 17117;
    private static String TAG = "SenderSvc";
    private static final int MAX_DATA_BYTES = 133;
    private static final int HEADER_SIZE = 4;
    private final IBinder binder = new SenderBinder();
    private TreeMap<String, TreeMap<Integer, MessageConfirmation>> partialConfs;
    private TreeMap<String, TreeMap<Integer, String>> confirmTimes;
    private int processingStatus, sequenceNo;
    private DBUtils dbUtils;
    private EncrypText app;
    private SmsManager mgr;
    private String currentConv;
    private Cryptor cryptor;

    private static boolean created;

    private SenderHandler handler;


    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "SenderSvc created");

        //created = true;
        processingStatus = 0;
        app = ((EncrypText) getApplication());
        cryptor = app.getCryptor();
        dbUtils = app.getDbUtils();
        currentConv = "";
        mgr = SmsManager.getDefault();

       /* if(app == null)
        {
            Log.v(TAG, "Error retrieving application instance");
            throw new NullPointerException();
        }*/

        partialConfs = new TreeMap<>();
        confirmTimes = new TreeMap<>();

        HandlerThread worker = new HandlerThread("Sender Worker");
        worker.start();
        handler = new SenderHandler(worker.getLooper(), this);

        created = true;
    }

    @Override
    public int onStartCommand(Intent intent, int one, int two)
    {
        Log.i(TAG, "Intent received");

        Message message = Message.obtain();
        message.setData(intent.getExtras());

        handler.dispatchMessage(message);

        return START_REDELIVER_INTENT;
    }

    private void handleJob(Bundle b)
    {
        if (b != null)
        {
            String address = b.getString(EncrypText.ADDRESS);
            int pos = b.getInt(EncrypText.THREAD_POSITION, -1);
            Key key = (Key) b.getSerializable(EncrypText.KEY);
            boolean shouldQuit = b.getBoolean(EncrypText.QUIT_FLAG, false);
            int flags = b.getInt(EncrypText.FLAGS, -1);

            if(key != null && address != null)
            {
                Log.i(TAG, "Sending public key");
                sendKey(key, address);

                if(flags == EncrypText.FLAG_GENERATE_SECRET_KEY) {
                    //cancel notification - action doesn't automatically do so
                    NotificationManager manager = (NotificationManager) getSystemService(ReceiverSvc.NOTIFICATION_SERVICE);
                    manager.cancel(address.hashCode());

                    generateSecretKey(address);
                }
            }
            else if(address != null && pos != -1)
            {
                Log.i(TAG, "Confirming message part");
                confirmMessagePart(address, pos);
            }
            else if(shouldQuit)
                tryQuit();
        }
        else
            tryQuit();
    }

    private void tryQuit()
    {
        if(!HomeActivity.isCreated() && !ConversationActivity.isCreated() && processingStatus == 0)
        {
            Log.i(TAG, "Quit Check Passed");
            stopSelf();
        }
    }

    private void generateSecretKey(String address) {
        Log.i(TAG, "Generating secret key");
        SecretKey secretKey;
        try {
            secretKey = cryptor.createAndStoreSecretKey(address);
        }
        catch (InvalidKeyException | InvalidKeyTypeException e) {
            Log.e(TAG, "Error generating secret key", e);
            Toast.makeText(this, "Could not generate secret key from exchange", Toast.LENGTH_SHORT).show();
            return;
        }

        if ((ConversationActivity.isActive()) && (ConversationActivity.currentNumber().equals(address)))
        {
            Log.i(TAG, "Passing secret key to ConversationActivity");
            Intent in = new Intent(this, ConversationActivity.class);
            in.putExtra(EncrypText.KEY, secretKey);
            in.setFlags(872415232); //Basically, clear top | single top | new task, as I recall.
            startActivity(in);
        }
    }

    public void sendKey(Key key, String address)
    {
        byte[] keyBytes = key.getEncoded();
        double pdus = keyBytes.length / (double) (MAX_DATA_BYTES - HEADER_SIZE); //effective data #

        if (pdus % 1 != 0) //making sure to round up to next whole packet
            pdus = (int) pdus + 1;

        Log.i(TAG, "Sending key of length " + keyBytes.length + " in " + pdus + " parts");
        String temp = "";
        for(byte b : keyBytes)
            temp += b + " ";
        Log.i(TAG, temp);

        byte[][] message = new byte[(int) pdus][MAX_DATA_BYTES];

        int k = 0;
        for(int pdu = 0; pdu < pdus; pdu++)
        {
            //indicating key exchange
            message[pdu][0] = 1;

            //message sequence number - used to mark key length in exchanges
            message[pdu][1] = (byte) keyBytes.length;

            message[pdu][2] = (byte) pdus;
            message[pdu][3] =  (byte) pdu;

            for(int j = 4; j < MAX_DATA_BYTES && k < keyBytes.length; j++)
            {
                message[pdu][j] = keyBytes[k];
                k++;
            }

            for(byte[] part : message)
                mgr.sendDataMessage(address, null, APPLICATION_PORT, part, null, null);
        }
    }

    public synchronized void sendMessage(ConversationEntry item, int place, Key key)
    {
        String address = item.getNumber();

        if(!currentConv.equals(address))
        {
            sequenceNo = 0;
            currentConv = address;
        }

        byte[] encryptedMessage;

        try {
            encryptedMessage = cryptor.encryptMessage(item.getMessage().getBytes(), (SecretKey) key, sequenceNo);
        }
        catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "While encrypting message", e);
            Toast.makeText(this, "Encryption error while sending message", Toast.LENGTH_SHORT).show();
            return;
        }

        double packets = encryptedMessage.length / (double) (MAX_DATA_BYTES - HEADER_SIZE);

        if (packets % 1 != 0) //making sure to round up to next whole packet
            packets = (int) packets + 1;

        byte[][] message = new byte[(int) packets][MAX_DATA_BYTES];

        int k = 0;
        for(int pdu = 0; pdu < packets; pdu++)
        {
            //2 for AES
            message[pdu][0] = 2;

            //message sequence number - session based - can only send 127 messages per session
            if(sequenceNo > 127)
                return;
            else
            {
                message[pdu][1] = (byte) sequenceNo;
            }

            message[pdu][2] = (byte) packets;
            message[pdu][3] =  (byte) pdu;

            for(int j = 4; j < MAX_DATA_BYTES && k < encryptedMessage.length; j++)
            {
                message[pdu][j] = encryptedMessage[k];
                k++;
            }
        }

        long messageId = dbUtils.storeMessage(item);
        //long pos = manager.writeSMS(address, item, -1, this);

        setupMessageConfirmation(address, place, messageId, message.length);

        for(byte[] pdu : message)
        {
            Intent in = new Intent(SENT_INTENT);
            in.putExtra(EncrypText.THREAD_POSITION, place);
            in.putExtra(EncrypText.ADDRESS, address);
            PendingIntent sent = PendingIntent.getBroadcast(this, 0, in,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            mgr.sendDataMessage(address, null, APPLICATION_PORT, pdu, sent, null);
        }

        sequenceNo++; //next sequence number
    }

    private void setupMessageConfirmation(String number, int place, long messageId, int parts)
    {
        Log.i(TAG, "Adding message");
        if(!partialConfs.containsKey(number))
            partialConfs.put(number, new TreeMap<Integer, MessageConfirmation>());

        Log.i(TAG, "setupMessageConfirmation Processing status " + processingStatus);
        partialConfs.get(number).put(place, new MessageConfirmation(parts, messageId));
        processingStatus++;
        Log.i(TAG, "setupMessageConfirmation Processing status " + processingStatus);
    }

    private void confirmMessagePart(String number, int pos)
    {
        Log.i(TAG, number + " " + pos);

        MessageConfirmation confirmation = partialConfs.get(number).get(pos);

        if(confirmation.getMessageParts() > 0)
            confirmation.setMessageParts(confirmation.getMessageParts() - 1);

        if(confirmation.getMessageParts() == 0)
        {
            String time = DateUtils.buildDate();

            if(!confirmTimes.containsKey(number))
                confirmTimes.put(number, new TreeMap<Integer, String>());

            confirmTimes.get(number).put(pos, time);

            dbUtils.confirmMessageSent(time, confirmation.getMessageId());

            Log.i(TAG, "confirmMessagePart Processing status " + processingStatus);
            partialConfs.get(number).remove(pos);
            processingStatus--;
            Log.i(TAG, "confirmMessagePart Processing status " + processingStatus);

            if(ConversationActivity.isActive() &&
                    ConversationActivity.currentNumber().equals(number))
            {
                Intent in = new Intent(this, ConversationActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                in.putExtra(EncrypText.THREAD_POSITION, pos);
                in.putExtra(EncrypText.TIME, time);

                startActivity(in);
            }
            else if(!ConversationActivity.isActive() && ConversationActivity.currentNumber().equals(number))
                ConversationActivity.markNewConfs();
        }
    }

    public TreeMap<Integer, String> getConfs(String number)
    {
        return confirmTimes.get(number);
    }

    public IBinder onBind(Intent intent) {
        return binder;
    }


    public class SenderBinder extends Binder
    {
        public SenderSvc getService()
        {
            return SenderSvc.this;
        }
    }

    public static boolean isCreated() {
        return created;
    }

    static class SenderHandler extends Handler {
        private final WeakReference<SenderSvc> reference;

        SenderHandler(Looper looper, SenderSvc svc) {
            super(looper);
            reference = new WeakReference<>(svc);
        }

        @Override
        public void handleMessage(Message msg) {
            SenderSvc svc = reference.get();
            svc.handleJob(msg.getData());
        }
    }
}
