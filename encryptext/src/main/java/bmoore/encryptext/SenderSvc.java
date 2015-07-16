package bmoore.encryptext;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.TreeMap;


public class SenderSvc extends Service {


    private static final String SENT_INTENT = "com.encryptext.PDU_SENT";
    public static final short APPLICATION_PORT = 17117;
    private static String TAG = "SenderSvc";
    private static final int MAX_DATA_BYTES = 133;
    private static final int HEADER_SIZE = 4;
    private final IBinder binder = new SenderBinder();
    private volatile LinkedList<Bundle> jobs;
    private TreeMap<String, TreeMap<Integer, long[]>> partialConfs;
    private TreeMap<String, TreeMap<Integer, String>> confirmTimes;
    private Thread worker;
    private int processingStatus, sequenceNo;
    private Files manager;
    private EncrypText app;
    private SmsManager mgr;
    private String currentConv;


    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.i(TAG, "SenderSvc created");

        //created = true;
        jobs = new LinkedList<>();
        processingStatus = 0;
        app = ((EncrypText)getApplication());
        manager = app.getFileManager();
        currentConv = "";
        mgr = SmsManager.getDefault();

        if(app == null)
        {
            Log.v(TAG, "Error retrieving application instance");
            throw new NullPointerException();
        }

        partialConfs = new TreeMap<>();
        confirmTimes = new TreeMap<>();

        worker = new Thread("Sender Worker")
        {
            @Override
            public void run()
            {
                synchronized (worker)
                {
                    while(true)
                    {
                        handleJobs();
                        try
                        {
                            worker.wait();
                        }
                        catch (InterruptedException e)
                        {
                        }
                    }
                }
            }
        };
        worker.start();
    }

    void addJob(Bundle b)
    {
        jobs.add(b);

        if(worker.getState().equals(Thread.State.WAITING))
        {
            synchronized (worker)
            {
                Log.i(TAG, "Bumping thread");
            worker.notify();
        }
    }
}

    @Override
    public int onStartCommand(Intent intent, int one, int two)
    {
        Log.i(TAG, "Intent received");

        Bundle b = new Bundle();
        b.putString("a", intent.getStringExtra("a"));
        b.putInt("p", intent.getIntExtra("p", -1));
        b.putByteArray("k", intent.getByteArrayExtra("k"));

        jobs.add(b);

        if(worker.getState().equals(Thread.State.WAITING))
        {
            synchronized (worker)
            {
                Log.i(TAG, "Bumping thread");
                worker.notify();
            }
        }


        return START_REDELIVER_INTENT;
    }

    private void handleJobs()
    {
        while(jobs.size() > 0)
        {
            Log.i(TAG, "Grabbing bundle");
            Bundle b = jobs.removeFirst();

            if (b != null)
            {
                ConversationEntry item = b.getParcelable("i");
                String address = b.getString("a");
                int pos = b.getInt("p", -1);
                byte[] key = b.getByteArray("k");

                if(item != null)
                {
                    Log.i(TAG, "Sending message");
                    sendMessage(item, pos);
                }
                else if(key != null && address != null)
                {
                    Log.i(TAG, "Sending public key");
                    sendKey(key, address);
                }
                else if(address != null && pos != -1)
                {
                    Log.i(TAG, "Confirming message part");
                    confirmMessagePart(address, pos);
                }
            }
            else
                tryQuit();
        }
    }

    private void tryQuit()
    {
        if(!Main.isCreated() && !Conversation.isCreated() && processingStatus == 0)
        {
            Log.i(TAG, "Quit Check Passed");
            stopSelf();
        }
    }


    private void sendKey(byte[] key, String address)
    {
        double pdus = key.length / (double) (MAX_DATA_BYTES - HEADER_SIZE); //effective data #

        if (pdus % 1 != 0) //making sure to round up to next whole packet
            pdus = (int) pdus + 1;

        Log.i(TAG, "Sending key of length " + key.length + " in " + pdus + " parts");
        String temp = "";
        for(byte b : key)
            temp += b + " ";
        Log.i(TAG, temp);

        byte[][] message = new byte[(int) pdus][MAX_DATA_BYTES];

        int k = 0;
        for(int pdu = 0; pdu < pdus; pdu++)
        {
            //indicating key exchange
            message[pdu][0] = 1;

            //message sequence number - used to mark key length in exchanges
            message[pdu][1] = (byte) key.length;

            message[pdu][2] = (byte) pdus;
            message[pdu][3] =  (byte) pdu;

            for(int j = 4; j < MAX_DATA_BYTES && k < key.length; j++)
            {
                message[pdu][j] = key[k];
                k++;
            }

            for(byte[] part : message)
                mgr.sendDataMessage(address, null, APPLICATION_PORT, part, null, null);
        }
    }

    private void sendMessage(ConversationEntry item, int place)
    {
        byte[] msg = item.getMessage().getBytes();
        String address = item.getNumber();

        double packets = msg.length / (double) (MAX_DATA_BYTES - HEADER_SIZE);

        if (packets % 1 != 0) //making sure to round up to next whole packet
            packets = (int) packets + 1;

        byte[][] message = new byte[(int) packets][MAX_DATA_BYTES];

        if(!currentConv.equals(address))
        {
            sequenceNo = 0;
            currentConv = address;
        }


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

            for(int j = 4; j < MAX_DATA_BYTES && k < msg.length; j++)
            {
                message[pdu][j] = msg[k];
                k++;
            }
        }


        long pos = manager.writeSMS(address, item, -1, this);

        setupMessageConfirmation(address, place, message.length, pos);

        for(byte[] pdu : message)
        {
            Intent in = new Intent(SENT_INTENT);
            in.putExtra("p", place);
            in.putExtra("a", address);
            PendingIntent sent = PendingIntent.getBroadcast(this, 0, in,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            mgr.sendDataMessage(address, null, APPLICATION_PORT, pdu, sent, null);
        }

        sequenceNo++; //next sequence number
    }

    private void setupMessageConfirmation(String number, int pos, int parts, long filePoint)
    {
        Log.i(TAG, "Adding message");
        if(!partialConfs.containsKey(number))
            partialConfs.put(number, new TreeMap<Integer, long[]>());

        long[] array = new long[2];
        array[0] = parts;
        array[1] = filePoint;

        Log.i(TAG, "setupMessageConfirmation Processing status " + processingStatus);
        partialConfs.get(number).put(pos, array);
        processingStatus++;
        Log.i(TAG, "setupMessageConfirmation Processing status " + processingStatus);
    }

    private void confirmMessagePart(String number, int pos)
    {
        Log.i(TAG, number + " " + pos);

        long partsConfirmed = partialConfs.get(number).get(pos)[0];
        if(partsConfirmed > 0)
            partsConfirmed--;

        if(partsConfirmed == 0)
        {
            String time = buildDate();

            if(!confirmTimes.containsKey(number))
                confirmTimes.put(number, new TreeMap<Integer, String>());

            confirmTimes.get(number).put(pos, time);

            manager.writeConf(number, time, partialConfs.get(number).get(pos)[1], this);

            Log.i(TAG, "confirmMessagePart Processing status " + processingStatus);
            partialConfs.get(number).remove(pos);
            processingStatus--;
            Log.i(TAG, "confirmMessagePart Processing status " + processingStatus);

            if(Conversation.isActive() &&
                    Conversation.currentNumber().equals(number))
            {
                Intent in = new Intent(this, Conversation.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                in.putExtra("p", pos);
                in.putExtra("t", time);

                startActivity(in);
            }
            else if(!Conversation.isActive() && Conversation.currentNumber().equals(number))
                Conversation.markNewConfs();
        }
    }

    TreeMap<Integer, String> getConfs(String number)
    {
        return confirmTimes.get(number);
    }

    private String buildDate()
    {
        final int MAX_DATE_LENGTH = 19;

        Calendar cal = app.getCal();
        String time;

        int hour = cal.get(Calendar.HOUR);

        if(hour == 0)
            time = "12:";
        else
            time = hour + ":";


        int minute = cal.get(Calendar.MINUTE);
        if(minute < 10) //apply minute filtering
            time += "0" + minute;
        else
            time += minute;

        if(cal.get(Calendar.AM_PM) == 0)
            time += " AM";
        else
            time += " PM";

        time += "," + cal.get(Calendar.MONTH) + ","
                + cal.get(Calendar.DAY_OF_MONTH) + "," + cal.get(Calendar.YEAR);

        int padLength = MAX_DATE_LENGTH - time.length();

        for(int i = 0; i < padLength; i++) //pad. Bump that String.format noise
            time += "*";

        return time;
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
}
