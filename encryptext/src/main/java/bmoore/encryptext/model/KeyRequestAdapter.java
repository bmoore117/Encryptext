package bmoore.encryptext.model;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import bmoore.encryptext.R;

/**
 * Created by Benjamin Moore on 11/23/2015.
 */
public class KeyRequestAdapter extends ArrayAdapter<KeyRequest> {
    Context context;
    ArrayList<KeyRequest> data = null;
    int layoutResourceId;
    Calendar c;

    public KeyRequestAdapter(Context paramContext, int layoutResourceId, ArrayList<KeyRequest> keyRequests)
    {
        super(paramContext, layoutResourceId, keyRequests);
        this.layoutResourceId = layoutResourceId;
        context = paramContext;
        data = keyRequests;
        c = new GregorianCalendar(Locale.getDefault());
        c.setTime(new Date());
    }

    @Override
    public View getView(int pos, View row, ViewGroup parent)
    {
        if(row == null)
        {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.key_exchange_request_listitem, parent, false);
        }

        TextView name = (TextView) row.findViewById(R.id.key_exhange_request_listitem_name);
        TextView status = (TextView) row.findViewById(R.id.key_exchange_request_listitem_status);
        ImageView contactThumb = (ImageView) row.findViewById(R.id.key_exchange_request_listitem_contact_thumb);


        KeyRequest request = data.get(pos);

        name.setText(request.getName());

        if(request.getStatus() != null && request.getDate() != null)
            status.setText(data.get(pos).getStatus() + " " + formatDate(data.get(pos).getDate()));

        contactThumb.setImageBitmap(data.get(pos).getContactThumb());

        return row;
    }

    private String formatDate(String date)
    {
        //Log.i("Adapter", "Formatting date");
        if(date == null)
            return null;

        if(!date.contains(","))
            return date;

        String[] dateParts = date.split(",");

        if(Integer.decode(dateParts[3]) == c.get(Calendar.YEAR))
        {
            if(Integer.decode(dateParts[1]) == c.get(Calendar.MONTH) && Integer.decode(dateParts[2])
                    == c.get(Calendar.DAY_OF_MONTH))
            {
                //Log.i("Adapter", "Returning " + dateParts[0]);
                return dateParts[0]; //return time of day
            }

            //Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2]);
            return findMonth(dateParts[1]) + " " + dateParts[2]; //return day of month ex: May 17
        }
        //Log.i("Adapter", "Returning " + findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3]);
        return findMonth(dateParts[1]) + " " + dateParts[2] + ", " + dateParts[3];
    }

    private String findMonth(String val)
    {

        if(val.equals("0"))
            return "Jan ";
        else if(val.equals("1"))
            return "Feb ";
        else if(val.equals("2"))
            return "Mar ";
        else if(val.equals("3"))
            return "Apr ";
        else if(val.equals("4"))
            return "May ";
        else if(val.equals("5"))
            return "Jun ";
        else if(val.equals("6"))
            return "Jul ";
        else if(val.equals("7"))
            return "Aug ";
        else if(val.equals("8"))
            return "Sep ";
        else if(val.equals("9"))
            return "Oct ";
        else if(val.equals("10"))
            return "Nov ";
        else
            return "Dec ";
    }
}
